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

  const productCodes = normalized.map((p) => p.productCode);
  const existingProductsList = await prisma.e6PharmacyProduct.findMany({
    where: { productCode: { in: productCodes } },
    select: Object.fromEntries(PRODUCT_FIELDS.map((field) => [field, true])),
  });
  const existingMap = new Map(existingProductsList.map((p) => [p.productCode, p]));

  const toCreate = [];
  const toUpdate = [];
  for (const item of normalized) {
    const existing = existingMap.get(item.productCode);
    if (!existing) { toCreate.push(item); created++; }
    else if (productChanged(existing, item)) { toUpdate.push(item); updated++; }
  }

  // 全部在同一事务内：有一个失败则全部回滚
  if (toCreate.length || toUpdate.length) {
    await prisma.$transaction(async (tx) => {
      for (const item of toCreate) {
        await tx.e6PharmacyProduct.create({ data: item });
      }
      for (const item of toUpdate) {
        await tx.e6PharmacyProduct.update({ where: { productCode: item.productCode }, data: item });
      }
    }, { timeout: 60000 }); // 最多等 60 秒
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

  // 处理 clearProductCodes，将其注入到处理队列中作为空批次（代表全线断货）
  for (const productCode of clearProductCodes) {
    const productId = productMap.get(productCode);
    if (!batchesByProduct.has(productId)) batchesByProduct.set(productId, []);
  }

  // Fetch all existing rows for these products in one query to avoid N+1 problem
  const allProductIds = Array.from(batchesByProduct.keys());
  const allExistingRows = await prisma.e6PharmacyInventoryBatch.findMany({
    where: { storeId: store.id, productId: { in: allProductIds } },
  });
  const existingRowsMap = new Map();
  for (const row of allExistingRows) {
    if (!existingRowsMap.has(row.productId)) existingRowsMap.set(row.productId, []);
    existingRowsMap.get(row.productId).push(row);
  }

  const seen = new Set();
  let created = 0;
  let updated = 0;
  let deleted = 0;

  // ── 阶段一：在事务外完成所有读操作（性能最优），计算出每个商品需要做什么 ──
  const writes = []; // 存储描述型操作，在事务内执行

  for (const [productId, productBatches] of batchesByProduct) {
    const hasActiveStock = productBatches.some((item) => Number(item.quantity) > 0);
    const existingRows = existingRowsMap.get(productId) || [];

    if (hasActiveStock) {
      // 场景 A：商品有非0库存，以本次 >0 批次为准做全量对比
      const incomingKeys = new Set(productBatches.map((item) => `${item.batchNo}\u0000${item.locationName}`));

      for (const item of productBatches) {
        seen.add(`${productId}\u0000${item.batchNo}\u0000${item.locationName}`);
        const existingRow = existingRows.find((row) => row.batchNo === item.batchNo && row.locationName === item.locationName);
        writes.push({ type: "upsert", storeId: store.id, productId, item });
        if (existingRow) updated++;
        else created++;
      }

      // 删除不在本次快照里的旧货位（换货位的旧记录、被 ERP 物理删除的批次）
      const staleIds = existingRows.filter((row) => !incomingKeys.has(`${row.batchNo}\u0000${row.locationName}`)).map((row) => row.id);
      if (staleIds.length) {
        writes.push({ type: "deleteMany", ids: staleIds });
        deleted += staleIds.length;
      }
    } else {
      // 场景 B：商品全线断货（clearProductCodes 路径），保留最近一条货位记录置为 0
      if (existingRows.length > 0) {
        existingRows.sort((a, b) => b.id - a.id);
        const keepRow = existingRows[0];
        seen.add(`${productId}\u0000${keepRow.batchNo}\u0000${keepRow.locationName || ""}`);
        if (Number(keepRow.quantity) !== 0) {
          writes.push({ type: "updateOne", id: keepRow.id });
          updated++;
        }
        const toDeleteIds = existingRows.slice(1).map((r) => r.id);
        if (toDeleteIds.length) {
          writes.push({ type: "deleteMany", ids: toDeleteIds });
          deleted += toDeleteIds.length;
        }
      }
    }
  }

  // 全量同步：处理本次未出现的旧商品（在事务外 read，结果纳入 writes）
  if (payload?.fullSync === true && (fullSyncComplete || !fullSyncStartedAt)) {
    const existing = await prisma.e6PharmacyInventoryBatch.findMany({
      where: { storeId: store.id },
      select: { id: true, productId: true, batchNo: true, locationName: true, receivedAt: true, quantity: true },
    });
    const touchedProductIds = new Set(allProductIds);
    const unseenByProduct = new Map();
    for (const item of existing) {
      const isSeen = fullSyncStartedAt
        ? item.receivedAt >= fullSyncStartedAt
        : seen.has(`${item.productId}\u0000${item.batchNo}\u0000${item.locationName || ""}`);
      if (!isSeen && !touchedProductIds.has(item.productId)) {
        if (!unseenByProduct.has(item.productId)) unseenByProduct.set(item.productId, []);
        unseenByProduct.get(item.productId).push(item);
      }
    }
    for (const [, rows] of unseenByProduct) {
      rows.sort((a, b) => b.id - a.id);
      if (Number(rows[0].quantity) > 0) {
        writes.push({ type: "updateOne", id: rows[0].id });
        updated++;
      }
      const toDeleteIds = rows.slice(1).map((r) => r.id);
      if (toDeleteIds.length) {
        writes.push({ type: "deleteMany", ids: toDeleteIds });
        deleted += toDeleteIds.length;
      }
    }
  }

  // ── 阶段二：单一事务执行所有写操作，失败则全部回滚 ──
  if (writes.length === 0 && productCodes.length === 0) {
    return { received: normalized.length, created, updated, deleted, fullSync: payload?.fullSync === true };
  }

  await prisma.$transaction(async (tx) => {
    for (const w of writes) {
      if (w.type === "upsert") {
        const { item } = w;
        await tx.e6PharmacyInventoryBatch.upsert({
          where: { storeId_productId_batchNo_locationName: { storeId: w.storeId, productId: w.productId, batchNo: item.batchNo, locationName: item.locationName } },
          create: {
            storeId: w.storeId, productId: w.productId,
            batchNo: item.batchNo, productionDate: item.productionDate,
            expiryDate: item.expiryDate, inboundDate: item.inboundDate,
            locationName: item.locationName, quantity: item.quantity, receivedAt: new Date(),
          },
          update: {
            productionDate: item.productionDate, expiryDate: item.expiryDate,
            inboundDate: item.inboundDate, quantity: item.quantity, receivedAt: new Date(),
          },
        });
      } else if (w.type === "deleteMany") {
        await tx.e6PharmacyInventoryBatch.deleteMany({ where: { id: { in: w.ids } } });
      } else if (w.type === "updateOne") {
        await tx.e6PharmacyInventoryBatch.update({ where: { id: w.id }, data: { quantity: "0", receivedAt: new Date() } });
      }
    }

    // 更新 last_inventory_seen_at（使用 tx 保持在同一事务内）
    if (productCodes.length) {
      const seenAt = new Date();
      for (const productCode of productCodes) {
        await tx.$executeRaw`UPDATE e6_pharmacy_products SET last_inventory_seen_at = ${seenAt} WHERE product_code = ${productCode}`;
      }
    }
  }, { timeout: 120000 }); // 全量同步写入量大，最多等 2 分钟

  return { received: normalized.length, created, updated, deleted, fullSync: payload?.fullSync === true };
}
