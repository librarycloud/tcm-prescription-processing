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
    store: batch.store,
  };
}
function normalizeProduct(product) {
  const inventories = (product.inventories || []).map(normalizeBatch);
  const stores = [...new Map(
    (product.inventories || [])
      .map((item) => item.store)
      .filter(Boolean)
      .map((store) => [store.id, store]),
  ).values()];
  return {
    id: product.id,
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
    store: product.store || stores[0] || null,
    stores,
  };
}

export async function listE6PharmacyProducts(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const scope = businessScope(actor, query.storeId);
  const keyword = String(query.keyword || "").trim();
  const expiryWithinMonths = query.expiryWithinMonths === undefined || query.expiryWithinMonths === ""
    ? null
    : toPositiveInt(query.expiryWithinMonths, 0);
  const expiryBefore = expiryWithinMonths
    ? (() => {
      const date = new Date();
      date.setHours(0, 0, 0, 0);
      date.setMonth(date.getMonth() + expiryWithinMonths);
      return date;
    })()
    : null;
  const inventoryWhere = {
    quantity: { gt: 0 },
    ...(scope.storeId ? { storeId: scope.storeId } : {}),
    ...(expiryBefore ? { expiryDate: { lt: expiryBefore } } : {}),
  };
  const where = {
    ...scope,
    inventories: { some: inventoryWhere },
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
        inventories: {
          where: inventoryWhere,
          include: { store: { select: { id: true, name: true, code: true } } },
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
