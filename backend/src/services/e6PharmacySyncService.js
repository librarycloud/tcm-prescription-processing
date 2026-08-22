import { AppError } from "../utils/appError.js";
import { authenticateStore } from "./e6IntegrationService.js";

function text(value, max, field, required = false) {
  const result = String(value ?? "").trim();
  if (required && !result) throw new AppError(`${field}不能为空`, 400);
  if (result.length > max) throw new AppError(`${field}不能超过${max}个字符`, 400);
  return result || null;
}

function integer(value, field) {
  const result = Number(value);
  if (!Number.isInteger(result) || result <= 0) throw new AppError(`${field}不正确`, 400);
  return result;
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
    e6ProductId: integer(item?.e6ProductId, "E6商品ID"),
    productCode: text(item?.productCode, 64, "商品编号", true),
    name: text(item?.name, 120, "商品名称", true),
    category: text(item?.category, 100, "分类"),
    categoryCode: text(item?.categoryCode, 64, "分类编号"),
    barcode: text(item?.barcode, 64, "条形码"),
    specification: text(item?.specification, 120, "规格"),
    dosageForm: text(item?.dosageForm, 64, "剂型"),
    manufacturer: text(item?.manufacturer, 200, "生产厂商"),
    categoryAttribute: text(item?.categoryAttribute, 100, "商品类别属性"),
    e6CreatedAt: date(item?.e6CreatedAt, "创建日期"),
    e6ModifiedAt: date(item?.e6ModifiedAt, "修改日期"),
  };
}

function normalizeBatch(item) {
  const quantity = decimal(item?.quantity, "库存数量", 3);
  if (Number(quantity) <= 0) throw new AppError("库存数量必须大于零", 400);
  return {
    e6ProductId: integer(item?.e6ProductId, "E6商品ID"),
    batchNo: text(item?.batchNo, 100, "批号") || "",
    productionDate: date(item?.productionDate, "生产日期"),
    expiryDate: date(item?.expiryDate, "有效期至"),
    quantity,
    amount: decimal(item?.amount, "库存金额", 2),
  };
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
      where: { storeId_e6ProductId: { storeId: store.id, e6ProductId: item.e6ProductId } },
      select: { id: true },
    });
    await prisma.e6PharmacyProduct.upsert({
      where: { storeId_e6ProductId: { storeId: store.id, e6ProductId: item.e6ProductId } },
      create: { storeId: store.id, ...item },
      update: item,
    });
    if (existing) updated++;
    else created++;
  }
  return { received: normalized.length, created, updated };
}

export async function uploadE6PharmacyInventory(prisma, payload, apiKey) {
  const store = await storeFromRequest(prisma, payload, apiKey);
  const items = Array.isArray(payload?.batches) ? payload.batches : [];
  if (items.length > 10000) throw new AppError("单次库存上传不能超过10000条", 400);
  const normalized = items.map(normalizeBatch);
  const productIds = [...new Set(normalized.map((item) => item.e6ProductId))];
  const products = await prisma.e6PharmacyProduct.findMany({
    where: { storeId: store.id, e6ProductId: { in: productIds } },
    select: { id: true, e6ProductId: true },
  });
  const productMap = new Map(products.map((item) => [item.e6ProductId, item.id]));
  const missing = productIds.filter((id) => !productMap.has(id));
  if (missing.length) throw new AppError(`库存对应商品尚未上传：${missing.join(",")}`, 400);

  const seen = new Set();
  let created = 0;
  let updated = 0;
  for (const item of normalized) {
    const productId = productMap.get(item.e6ProductId);
    const key = `${productId}\u0000${item.batchNo}`;
    if (seen.has(key)) throw new AppError(`库存批次重复：${item.e6ProductId}/${item.batchNo}`, 400);
    seen.add(key);
    const existing = await prisma.e6PharmacyInventoryBatch.findUnique({
      where: { storeId_productId_batchNo: { storeId: store.id, productId, batchNo: item.batchNo } },
      select: { id: true },
    });
    await prisma.e6PharmacyInventoryBatch.upsert({
      where: { storeId_productId_batchNo: { storeId: store.id, productId, batchNo: item.batchNo } },
      create: { storeId: store.id, productId, ...item, receivedAt: new Date() },
      update: { productionDate: item.productionDate, expiryDate: item.expiryDate, quantity: item.quantity, amount: item.amount, receivedAt: new Date() },
    });
    if (existing) updated++;
    else created++;
  }

  if (payload?.fullSync === true) {
    const existing = await prisma.e6PharmacyInventoryBatch.findMany({
      where: { storeId: store.id },
      select: { id: true, productId: true, batchNo: true },
    });
    const removeIds = existing
      .filter((item) => !seen.has(`${item.productId}\u0000${item.batchNo}`))
      .map((item) => item.id);
    if (removeIds.length) await prisma.e6PharmacyInventoryBatch.deleteMany({ where: { id: { in: removeIds } } });
  }

  await prisma.e6PharmacyProduct.updateMany({
    where: { storeId: store.id, e6ProductId: { in: productIds } },
    data: { lastInventorySeenAt: new Date() },
  });
  return { received: normalized.length, created, updated, fullSync: payload?.fullSync === true };
}
