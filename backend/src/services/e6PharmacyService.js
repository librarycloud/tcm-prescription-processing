import { businessScope } from "./permissionService.js";
import { toPositiveInt } from "../utils/validators.js";

function normalizeBatch(batch) {
  return {
    id: batch.id,
    batchNo: batch.batchNo || "-",
    locationName: batch.locationName || "-",
    productionDate: batch.productionDate,
    expiryDate: batch.expiryDate,
    inboundDate: batch.inboundDate,
    quantity: Number(batch.quantity || 0),
    amount: Number(batch.amount || 0),
    receivedAt: batch.receivedAt,
    updatedAt: batch.updatedAt,
  };
}
function normalizeProduct(product) {
  const inventories = (product.inventories || []).map(normalizeBatch);
  return {
    id: product.id,
    storeId: product.storeId,
    e6ProductId: product.e6ProductId,
    productCode: product.productCode,
    name: product.name,
    category: product.category,
    categoryCode: product.categoryCode,
    barcode: product.barcode,
    specification: product.specification,
    dosageForm: product.dosageForm,
    manufacturer: product.manufacturer,
    categoryAttribute: product.categoryAttribute,
    unit: product.unit,
    e6CreatedAt: product.e6CreatedAt,
    e6ModifiedAt: product.e6ModifiedAt,
    lastInventorySeenAt: product.lastInventorySeenAt,
    totalQuantity: inventories.reduce((sum, item) => sum + item.quantity, 0),
    batchCount: inventories.length,
    inventories,
    store: product.store,
  };
}

export async function listE6PharmacyProducts(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const scope = businessScope(actor, query.storeId);
  const keyword = String(query.keyword || "").trim();
  const where = {
    ...scope,
    inventories: { some: { quantity: { gt: 0 } } },
  };

  if (keyword) {
    where.OR = [
      { productCode: { contains: keyword } },
      { name: { contains: keyword } },
      { barcode: { contains: keyword } },
    ];
  }

  const [list, total] = await Promise.all([
    prisma.e6PharmacyProduct.findMany({
      where,
      include: {
        store: { select: { id: true, name: true, code: true } },
        inventories: {
          where: { quantity: { gt: 0 } },
          orderBy: [{ expiryDate: "asc" }, { batchNo: "asc" }],
        },
      },
      orderBy: [{ name: "asc" }, { productCode: "asc" }],
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.e6PharmacyProduct.count({ where }),
  ]);

  return {
    list: list.map(normalizeProduct),
    pagination: {
      page,
      pageSize,
      total,
      pages: Math.ceil(total / pageSize),
    },
  };
}
