import { AppError } from "../utils/appError.js";
import { authenticateStore } from "./e6IntegrationService.js";

const PRODUCT_FIELDS = [
  "productCode", "name", "category", "categoryCode", "barcode", "specification",
  "dosageForm", "manufacturer", "categoryAttribute", "unit", "retailPrice", "e6CreatedAt", "e6ModifiedAt",
];

function sameValue(left, right) {
  if (left instanceof Date || right instanceof Date) {
    const leftTime = left == null ? null : new Date(left).getTime();
    const rightTime = right == null ? null : new Date(right).getTime();
    return leftTime === rightTime;
  }
  if (left && typeof left === "object") return String(left) === String(right);
  return (left ?? null) === (right ?? null);
}

function productChanged(existing, incoming) {
  return PRODUCT_FIELDS.some((field) => !sameValue(existing[field], incoming[field]));
}

function text(value, max, field, required = false) {
  const result = String(value ?? "").trim();
  if (required && !result) throw new AppError(`${field}不能为空`, 400);
  if (result.length > max) throw new AppError(`${field}不能超过${max}个字符`, 400);
  return result || null;
}

function decimal(value, field, scale) {
  const result = Number(value);
  if (!Number.isFinite(result) || result < 0) throw new AppError(`${field}不正确`, 400);
  return result.toFixed(scale);
}

function date(value, field) {
  if (value === undefined || value === null || value === "") return null;
  const result = new Date(value);
  if (Number.isNaN(result.getTime())) throw new AppError(`${field}格式不正确`, 400);
  return result;
}

function normalizeProduct(item) {
  return {
    productCode: text(item?.productCode, 64, "商品编号", true),
    name: text(item?.name, 120, "商品名称", true),
    category: text(item?.category, 100, "分类"),
    categoryCode: text(item?.categoryCode, 64, "分类编号"),
    barcode: text(item?.barcode, 64, "条形码"),
    specification: text(item?.specification, 120, "规格"),
    dosageForm: text(item?.dosageForm, 64, "剂型"),
    manufacturer: text(item?.manufacturer, 200, "生产厂商"),
    categoryAttribute: text(item?.categoryAttribute, 100, "商品类别属性"),
    unit: text(item?.unit, 30, "单位"),
    retailPrice: decimal(item?.retailPrice ?? 0, "零售价", 2),
    e6CreatedAt: date(item?.e6CreatedAt, "创建日期"),
    e6ModifiedAt: date(item?.e6ModifiedAt, "修改日期"),
  };
}

function normalizeBatch(item) {
  const quantity = decimal(item?.quantity, "库存数量", 3);
  return {
    productCode: text(item?.productCode, 64, "商品编号", true),
    batchNo: text(item?.batchNo, 100, "批号") || "",
    productionDate: date(item?.productionDate, "生产日期"),
    expiryDate: date(item?.expiryDate, "有效期至"),
    inboundDate: date(item?.inboundDate, "入库时间"),
    locationName: text(item?.locationName, 120, "货位名称") || "",
    quantity,
  };
}

export function mergeBatches(items) {
  const merged = new Map();
  for (const item of items) {
    const key = `${item.productCode}\u0000${item.batchNo}\u0000${item.locationName}`;
    const existing = merged.get(key);
    if (!existing) {
      merged.set(key, { ...item });
      continue;
    }
    existing.quantity = (Number(existing.quantity) + Number(item.quantity)).toFixed(3);
    // 同一货位的重复行只汇总库存数量，商品零售价统一保存在商品表。
  }
  return [...merged.values()];
}

async function storeFromRequest(prisma, payload, apiKey) {
  const storeCode = text(payload?.storeCode, 50, "门店编码", true).toUpperCase();
  return authenticateStore(prisma, storeCode, apiKey);
}

export async function uploadE6PharmacyProducts(prisma, payload, apiKey) {
  const store = await storeFromRequest(prisma, payload, apiKey);
  const items = Array.isArray(payload?.products) ? payload.products : [];
  if (items.length > 5000) throw new AppError("单次商品上传不能超过5000条", 400);
  const normalized = items.map(normalizeProduct);
  let created = 0;
  let updated = 0;
  for (const item of normalized) {
    const existing = await prisma.e6PharmacyProduct.findUnique({
      where: { productCode: item.productCode },
      select: Object.fromEntries(PRODUCT_FIELDS.map((field) => [field, true])),
    });
    if (!existing) {
      await prisma.e6PharmacyProduct.create({ data: item });
      created++;
    } else if (productChanged(existing, item)) {
      await prisma.e6PharmacyProduct.update({ where: { productCode: item.productCode }, data: item });
      updated++;
    }
  }
  return { received: normalized.length, created, updated };
}

export async function uploadE6PharmacyInventory(prisma, payload, apiKey) {
  const store = await storeFromRequest(prisma, payload, apiKey);
  const items = Array.isArray(payload?.batches) ? payload.batches : [];
  if (items.length > 10000) throw new AppError("单次库存上传不能超过10000条", 400);
  const normalized = mergeBatches(items.map(normalizeBatch));
  const clearProductCodes = [...new Set((Array.isArray(payload?.clearProductCodes) ? payload.clearProductCodes : []).map((value) => text(value, 64, "商品编号")).filter(Boolean))];
  const fullSyncStartedAt = payload?.fullSyncStartedAt
    ? date(payload.fullSyncStartedAt, "全量同步开始时间")
    : null;
  const fullSyncComplete = payload?.fullSyncComplete === true;
  const productCodes = [...new Set([...normalized.map((item) => item.productCode), ...clearProductCodes])];
  const products = await prisma.e6PharmacyProduct.findMany({
    where: { productCode: { in: productCodes } },
    select: { id: true, productCode: true },
  });
  const productMap = new Map(products.map((item) => [item.productCode, item.id]));
  const missing = productCodes.filter((code) => !productMap.has(code));
  if (missing.length) throw new AppError(`库存对应商品尚未上传：${missing.join(",")}`, 400);

  // 按商品分组组织上传批次
  const batchesByProduct = new Map();
  for (const item of normalized) {
    const productId = productMap.get(item.productCode);
    if (!batchesByProduct.has(productId)) batchesByProduct.set(productId, []);
    batchesByProduct.get(productId).push(item);
  }

  const seen = new Set();
  let created = 0;
  let updated = 0;
  let deleted = 0;

  for (const [productId, productBatches] of batchesByProduct) {
    const hasActiveStock = productBatches.some((item) => Number(item.quantity) > 0);
    const existingRows = await prisma.e6PharmacyInventoryBatch.findMany({
      where: { storeId: store.id, productId },
    });
    const incomingKeys = new Set(productBatches.map((item) => `${item.batchNo}\u0000${item.locationName}`));

    if (hasActiveStock) {
      // 该商品存在非0库存：更新/创建有效货位，删除已变0货位
      for (const item of productBatches) {
        const key = `${productId}\u0000${item.batchNo}\u0000${item.locationName}`;
        seen.add(key);
        const existing = existingRows.find((row) => row.batchNo === item.batchNo && row.locationName === item.locationName);
        if (Number(item.quantity) === 0) {
          if (existing) {
            await prisma.e6PharmacyInventoryBatch.delete({ where: { id: existing.id } });
            deleted++;
          }
          continue;
        }
        await prisma.e6PharmacyInventoryBatch.upsert({
          where: { storeId_productId_batchNo_locationName: { storeId: store.id, productId, batchNo: item.batchNo, locationName: item.locationName } },
          create: {
            storeId: store.id,
            productId,
            batchNo: item.batchNo,
            productionDate: item.productionDate,
            expiryDate: item.expiryDate,
            inboundDate: item.inboundDate,
            locationName: item.locationName,
            quantity: item.quantity,
            receivedAt: new Date(),
          },
          update: {
            productionDate: item.productionDate,
            expiryDate: item.expiryDate,
            inboundDate: item.inboundDate,
            locationName: item.locationName,
            quantity: item.quantity,
            receivedAt: new Date(),
          },
        });
        if (existing) updated++;
        else created++;
      }

      // 关键：删除数据库中存在但本次 E6 上传中已不存在的旧货位（如换货位前的 0101，或新货入库后清理历史 0 库存批次）
      const staleRows = existingRows.filter((row) => !incomingKeys.has(`${row.batchNo}\u0000${row.locationName}`));
      if (staleRows.length) {
        await prisma.e6PharmacyInventoryBatch.deleteMany({
          where: { id: { in: staleRows.map((row) => row.id) } },
        });
        deleted += staleRows.length;
      }
    } else {
      // 该商品所有库存货位从非0变为0（断货）：不删除货位记录，将数量改为0，保留上批次货位
      for (const item of productBatches) {
        const key = `${productId}\u0000${item.batchNo}\u0000${item.locationName}`;
        seen.add(key);
      }
      if (existingRows.length) {
        await prisma.e6PharmacyInventoryBatch.updateMany({
          where: { storeId: store.id, productId },
          data: { quantity: "0", receivedAt: new Date() },
        });
        updated += existingRows.length;
      } else {
        // 若之前无记录但上传了0数量批次，存入货位并将数量记为 0
        for (const item of productBatches) {
          await prisma.e6PharmacyInventoryBatch.upsert({
            where: { storeId_productId_batchNo_locationName: { storeId: store.id, productId, batchNo: item.batchNo, locationName: item.locationName } },
            create: {
              storeId: store.id,
              productId,
              batchNo: item.batchNo,
              productionDate: item.productionDate,
              expiryDate: item.expiryDate,
              inboundDate: item.inboundDate,
              locationName: item.locationName,
              quantity: "0",
              receivedAt: new Date(),
            },
            update: { quantity: "0", receivedAt: new Date() },
          });
          created++;
        }
      }
    }
  }

  // 处理 clearProductCodes：商品总库存清零，不删除记录，将数量置为 0
  for (const productCode of clearProductCodes) {
    const productId = productMap.get(productCode);
    const result = await prisma.e6PharmacyInventoryBatch.updateMany({
      where: { storeId: store.id, productId },
      data: { quantity: "0", receivedAt: new Date() },
    });
    updated += result.count || 0;
  }

  if (payload?.fullSync === true && (fullSyncComplete || !fullSyncStartedAt)) {
    const existing = await prisma.e6PharmacyInventoryBatch.findMany({
      where: { storeId: store.id },
      select: { id: true, productId: true, batchNo: true, locationName: true, receivedAt: true, quantity: true },
    });
    const touchedProductIds = new Set(products.map((p) => p.id));
    const removeIds = [];
    const setZeroIds = [];

    for (const item of existing) {
      const isSeen = fullSyncStartedAt
        ? item.receivedAt >= fullSyncStartedAt
        : seen.has(`${item.productId}\u0000${item.batchNo}\u0000${item.locationName || ""}`);
      if (!isSeen) {
        if (touchedProductIds.has(item.productId)) {
          removeIds.push(item.id);
        } else {
          if (Number(item.quantity) > 0) setZeroIds.push(item.id);
        }
      }
    }
    if (removeIds.length) {
      await prisma.e6PharmacyInventoryBatch.deleteMany({ where: { id: { in: removeIds } } });
      deleted += removeIds.length;
    }
    if (setZeroIds.length) {
      await prisma.e6PharmacyInventoryBatch.updateMany({
        where: { id: { in: setZeroIds } },
        data: { quantity: "0", receivedAt: new Date() },
      });
      updated += setZeroIds.length;
    }
  }

  // 仅记录库存同步时间，不触发商品表 updated_at（Prisma @updatedAt）。
  if (productCodes.length) {
    const seenAt = new Date();
    for (const productCode of productCodes) {
      await prisma.$executeRaw`
        UPDATE e6_pharmacy_products
        SET last_inventory_seen_at = ${seenAt}
        WHERE product_code = ${productCode}`;
    }
  }
  return { received: normalized.length, created, updated, deleted, fullSync: payload?.fullSync === true };
}
