import assert from "node:assert/strict";
import test from "node:test";
import { listE6PharmacyProducts } from "../src/services/e6PharmacyService.js";
import { mergeBatches } from "../src/services/e6PharmacySyncService.js";

test("E6 pharmacy batches keep locations separate", () => {
  const result = mergeBatches([
    { productCode: "99052", batchNo: "2411061", locationName: "A货位", quantity: "2.000" },
    { productCode: "99052", batchNo: "2411061", locationName: "B货位", quantity: "3.000" },
    { productCode: "99052", batchNo: "2411061", locationName: "A货位", quantity: "1.000" },
  ]);

  assert.equal(result.length, 2);
  assert.equal(result[0].quantity, "3.000");
  assert.equal(result[0].locationName, "A货位");
  assert.equal(result[1].quantity, "3.000");
  assert.equal(result[1].locationName, "B货位");
});

test("E6 pharmacy batches preserve a zero quantity update", () => {
  const result = mergeBatches([
    { productCode: "99052", batchNo: "2411061", locationName: "A货位", quantity: "0.000" },
  ]);

  assert.equal(result.length, 1);
  assert.equal(result[0].quantity, "0.000");
});

test("E6 pharmacy query scopes store admins and searches product fields", async () => {
  const calls = [];
  const product = {
    id: 10,
    storeId: 3,
    productCode: "671012158",
    name: "氯雷他定糖浆",
    category: "西药",
    categoryCode: "6",
    barcode: "6900000000000",
    specification: "100ml",
    dosageForm: "糖浆剂",
    manufacturer: "厂商",
    categoryAttribute: "化学药制剂",
    unit: "盒",
    retailPrice: "49.50",
    e6CreatedAt: null,
    e6ModifiedAt: null,
    lastInventorySeenAt: null,
    store: { id: 3, name: "苏州店", code: "SZ001" },
    inventories: [
      {
        id: 20,
        batchNo: "G011226",
        locationName: "一号货位",
        productionDate: new Date("2026-01-23T00:00:00.000Z"),
        expiryDate: new Date("2029-01-22T00:00:00.000Z"),
        inboundDate: new Date("2026-08-20T00:00:00.000Z"),
        quantity: "3.000",
        receivedAt: new Date("2026-08-22T01:00:00.000Z"),
        updatedAt: new Date("2026-08-22T01:00:00.000Z"),
      },
    ],
  };
  const prisma = {
    $transaction: async (ops) => (typeof ops === "function" ? ops(prisma) : Promise.all(ops)),
    e6PharmacyProduct: {
      findMany: async (args) => {
        calls.push({ type: "findMany", args });
        return [product];
      },
      count: async (args) => {
        calls.push({ type: "count", args });
        return 1;
      },
    },
  };

  const result = await listE6PharmacyProducts(
    prisma,
    { id: 8, role: 2, storeId: 3 },
    { keyword: "690000", storeId: 99, page: 1, pageSize: 20 },
  );

  const findArgs = calls.find((item) => item.type === "findMany").args;
  assert.equal(findArgs.where.storeId, undefined);
  assert.equal(findArgs.where.inventories.some.storeId, 3);
  assert.deepEqual(findArgs.where.OR, [
    { productCode: { contains: "690000" } },
    { name: { contains: "690000" } },
    { barcode: { contains: "690000" } },
  ]);
  assert.equal(result.pagination.total, 1);
  assert.equal(result.list[0].totalQuantity, 3);
  assert.equal(result.list[0].inventories[0].batchNo, "G011226");
  assert.equal(result.list[0].unit, "盒");
  assert.equal(result.list[0].retailPrice, 49.5);
  assert.equal(result.list[0].inventories[0].locationName, "一号货位");
});

test("E6 pharmacy expiry filter applies to products and inventory details", async () => {
  const calls = [];
  const prisma = {
    $transaction: async (ops) => (typeof ops === "function" ? ops(prisma) : Promise.all(ops)),
    e6PharmacyProduct: {
      findMany: async (args) => {
        calls.push({ type: "findMany", args });
        return [];
      },
      count: async (args) => {
        calls.push({ type: "count", args });
        return 0;
      },
    },
  };

  await listE6PharmacyProducts(
    prisma,
    { id: 8, role: 2, storeId: 3 },
    { expiryWithinMonths: "3" },
  );

  const findArgs = calls.find((item) => item.type === "findMany").args;
  assert.equal(findArgs.where.inventories.some.quantity.gt, 0);
  assert.ok(findArgs.where.inventories.some.expiryDate.lt instanceof Date);
  assert.equal(findArgs.include.inventories.where.quantity.gt, 0);
  assert.ok(findArgs.include.inventories.where.expiryDate.lt instanceof Date);
  assert.equal(
    findArgs.where.inventories.some.expiryDate.lt.getMonth(),
    (new Date().getMonth() + 3) % 12,
  );
});

test("E6 pharmacy incremental sync: moving location from 0101 to 0102 deletes 0101", async () => {
  const bcrypt = (await import("bcrypt")).default;
  const { uploadE6PharmacyInventory } = await import("../src/services/e6PharmacySyncService.js");
  const hash = await bcrypt.hash("key123", 1);
  let batches = [
    { id: 1, storeId: 10, productId: 100, batchNo: "240101", locationName: "0101", quantity: "10.000", receivedAt: new Date() },
  ];
  const prisma = {
    $transaction: async (ops) => (typeof ops === "function" ? ops(prisma) : Promise.all(ops)),
    store: {
      findFirst: async () => ({ id: 10, code: "SZ001", e6Enabled: 1, e6ApiKeyHash: hash }),
    },
    e6PharmacyProduct: {
      findMany: async () => [{ id: 100, productCode: "MED01" }],
    },
    e6PharmacyInventoryBatch: {
      findMany: async ({ where }) => batches.filter((b) => b.storeId === where.storeId && ((where.productId.in && where.productId.in.includes(b.productId)) || b.productId === where.productId)),
      upsert: async ({ where, create, update }) => {
        const found = batches.find((b) => b.storeId === where.storeId_productId_batchNo_locationName.storeId && b.productId === where.storeId_productId_batchNo_locationName.productId && b.batchNo === where.storeId_productId_batchNo_locationName.batchNo && b.locationName === where.storeId_productId_batchNo_locationName.locationName);
        if (found) {
          Object.assign(found, update);
          return found;
        }
        const item = { id: 2, ...create };
        batches.push(item);
        return item;
      },
      deleteMany: async ({ where }) => {
        if (where?.id?.in) {
          const before = batches.length;
          batches = batches.filter((b) => !where.id.in.includes(b.id));
          return { count: before - batches.length };
        }
        return { count: 0 };
      },
      delete: async ({ where }) => {
        batches = batches.filter((b) => b.id !== where.id);
        return { id: where.id };
      },
      updateMany: async ({ where, data }) => {
        let count = 0;
        for (const b of batches) {
          if (b.storeId === where.storeId && ((where.productId.in && where.productId.in.includes(b.productId)) || b.productId === where.productId)) {
            Object.assign(b, data);
            count++;
          }
        }
        return { count };
      },
    },
    $executeRaw: async () => 1,
  };

  await uploadE6PharmacyInventory(
    prisma,
    {
      storeCode: "SZ001",
      batches: [
        { productCode: "MED01", batchNo: "240101", locationName: "0102", quantity: "10.000" },
      ],
    },
    "key123",
  );

  assert.equal(batches.length, 1);
  assert.equal(batches[0].locationName, "0102");
  assert.equal(batches[0].quantity, "10.000");
});

test("E6 pharmacy incremental sync: product out of stock preserves location with quantity 0", async () => {
  const bcrypt = (await import("bcrypt")).default;
  const { uploadE6PharmacyInventory } = await import("../src/services/e6PharmacySyncService.js");
  const hash = await bcrypt.hash("key123", 1);
  let batches = [
    { id: 1, storeId: 10, productId: 100, batchNo: "240101", locationName: "0102", quantity: "10.000", receivedAt: new Date() },
  ];
  const prisma = {
    $transaction: async (ops) => (typeof ops === "function" ? ops(prisma) : Promise.all(ops)),
    store: {
      findFirst: async () => ({ id: 10, code: "SZ001", e6Enabled: 1, e6ApiKeyHash: hash }),
    },
    e6PharmacyProduct: {
      findMany: async () => [{ id: 100, productCode: "MED01" }],
    },
    e6PharmacyInventoryBatch: {
      findMany: async ({ where }) => batches.filter((b) => b.storeId === where.storeId && ((where.productId.in && where.productId.in.includes(b.productId)) || b.productId === where.productId)),
      updateMany: async ({ where, data }) => {
        let count = 0;
        for (const b of batches) {
          if (b.storeId === where.storeId && ((where.productId.in && where.productId.in.includes(b.productId)) || b.productId === where.productId)) {
            Object.assign(b, data);
            count++;
          }
        }
        return { count };
      },
      update: async ({ where, data }) => {
        const b = batches.find((x) => x.id === where.id);
        if (b) Object.assign(b, data);
        return b;
      },
      deleteMany: async () => {
        throw new Error("Should not delete when all stock becomes 0!");
      },
      delete: async () => {
        throw new Error("Should not delete when all stock becomes 0!");
      },
    },
    $executeRaw: async () => 1,
  };

  // 1. Clear via clearProductCodes
  await uploadE6PharmacyInventory(
    prisma,
    {
      storeCode: "SZ001",
      clearProductCodes: ["MED01"],
    },
    "key123",
  );

  assert.equal(batches.length, 1);
  assert.equal(batches[0].locationName, "0102");
  assert.equal(batches[0].quantity, "0");

  // 2. Clear via batch with quantity 0
  await uploadE6PharmacyInventory(
    prisma,
    {
      storeCode: "SZ001",
      batches: [{ productCode: "MED01", batchNo: "240101", locationName: "0102", quantity: "0.000" }],
    },
    "key123",
  );
  assert.equal(batches.length, 1);
  assert.equal(batches[0].locationName, "0102");
  assert.equal(batches[0].quantity, "0");
});

test("E6 pharmacy incremental sync: new stock arrival cleans up previous 0-stock batch", async () => {
  const bcrypt = (await import("bcrypt")).default;
  const { uploadE6PharmacyInventory } = await import("../src/services/e6PharmacySyncService.js");
  const hash = await bcrypt.hash("key123", 1);
  let batches = [
    { id: 1, storeId: 10, productId: 100, batchNo: "240101", locationName: "0102", quantity: "0", receivedAt: new Date() },
  ];
  const prisma = {
    $transaction: async (ops) => (typeof ops === "function" ? ops(prisma) : Promise.all(ops)),
    store: {
      findFirst: async () => ({ id: 10, code: "SZ001", e6Enabled: 1, e6ApiKeyHash: hash }),
    },
    e6PharmacyProduct: {
      findMany: async () => [{ id: 100, productCode: "MED01" }],
    },
    e6PharmacyInventoryBatch: {
      findMany: async ({ where }) => batches.filter((b) => b.storeId === where.storeId && ((where.productId.in && where.productId.in.includes(b.productId)) || b.productId === where.productId)),
      upsert: async ({ create }) => {
        const item = { id: 2, ...create };
        batches.push(item);
        return item;
      },
      deleteMany: async ({ where }) => {
        if (where?.id?.in) {
          const before = batches.length;
          batches = batches.filter((b) => !where.id.in.includes(b.id));
          return { count: before - batches.length };
        }
        return { count: 0 };
      },
    },
    $executeRaw: async () => 1,
  };

  await uploadE6PharmacyInventory(
    prisma,
    {
      storeCode: "SZ001",
      batches: [
        { productCode: "MED01", batchNo: "240911", locationName: "0102", quantity: "20.000" },
      ],
    },
    "key123",
  );

  assert.equal(batches.length, 1);
  assert.equal(batches[0].batchNo, "240911");
  assert.equal(batches[0].locationName, "0102");
  assert.equal(batches[0].quantity, "20.000");
});

test("E6 pharmacy query returns 0-inventory product and location when searched by keyword", async () => {
  const calls = [];
  const zeroProduct = {
    id: 10,
    storeId: 3,
    productCode: "MED01",
    name: "阿莫西林胶囊",
    retailPrice: "25.00",
    inventories: [
      {
        id: 20,
        batchNo: "240101",
        locationName: "0102",
        quantity: "0.000",
      },
    ],
  };
  const prisma = {
    $transaction: async (ops) => (typeof ops === "function" ? ops(prisma) : Promise.all(ops)),
    e6PharmacyProduct: {
      findMany: async (args) => {
        calls.push({ type: "findMany", args });
        return [zeroProduct];
      },
      count: async () => 1,
    },
  };

  const result = await listE6PharmacyProducts(
    prisma,
    { id: 8, role: 2, storeId: 3 },
    { keyword: "阿莫西林", storeId: 3, page: 1, pageSize: 20 },
  );

  const findArgs = calls[0].args;
  assert.equal(findArgs.where.inventories.some.quantity, undefined);
  assert.equal(result.list[0].totalQuantity, 0);
  assert.equal(result.list[0].inventories[0].locationName, "0102");
  assert.equal(result.list[0].inventories[0].quantity, 0);
});

test("E6 pharmacy query sorts by direct columns (retailPrice, e6ModifiedAt, name, productCode)", async () => {
  const calls = [];
  const prisma = {
    e6PharmacyProduct: {
      findMany: async (args) => {
        calls.push(args);
        return [];
      },
      count: async () => 0,
    },
  };

  await listE6PharmacyProducts(
    prisma,
    { id: 1, role: 0 },
    { sortBy: "retailPrice", sortOrder: "desc" },
  );
  assert.deepEqual(calls[0].orderBy, [
    { retailPrice: "desc" },
    { productCode: "asc" },
    { id: "asc" },
  ]);

  calls.length = 0;
  await listE6PharmacyProducts(
    prisma,
    { id: 1, role: 0 },
    { sortBy: "e6ModifiedAt", sortOrder: "asc" },
  );
  assert.deepEqual(calls[0].orderBy, [
    { e6ModifiedAt: "asc" },
    { productCode: "asc" },
    { id: "asc" },
  ]);

  calls.length = 0;
  await listE6PharmacyProducts(
    prisma,
    { id: 1, role: 0 },
    { sortBy: "productCode", sortOrder: "desc" },
  );
  assert.deepEqual(calls[0].orderBy, [
    { productCode: "desc" },
    { id: "asc" },
  ]);
});

test("E6 pharmacy query sorts by aggregate columns in-memory fallback (totalQuantity, batchCount, categoryName)", async () => {
  const productA = {
    id: 1,
    productCode: "001",
    name: "商品A",
    category: "中成药",
    categoryCode: "CAT1",
    inventories: [
      { id: 10, batchNo: "B1", quantity: "5.000" },
      { id: 11, batchNo: "B2", quantity: "5.000" },
    ],
  };
  const productB = {
    id: 2,
    productCode: "002",
    name: "商品B",
    category: "西药",
    categoryCode: "CAT2",
    inventories: [
      { id: 20, batchNo: "B3", quantity: "20.000" },
    ],
  };

  const prisma = {
    e6PharmacyProduct: {
      findMany: async () => [productA, productB],
      count: async () => 2,
    },
    e6PharmacyCategoryMapping: {
      findMany: async () => [
        { categoryCode: "CAT1", categoryName: "中药" },
        { categoryCode: "CAT2", categoryName: "西药" },
      ],
    },
  };

  // Sort by totalQuantity desc -> Product B (20) before Product A (10)
  const resQtyDesc = await listE6PharmacyProducts(
    prisma,
    { id: 1, role: 0 },
    { sortBy: "totalQuantity", sortOrder: "desc" },
  );
  assert.equal(resQtyDesc.list[0].id, 2);
  assert.equal(resQtyDesc.list[1].id, 1);

  // Sort by batchCount desc -> Product A (2 batches) before Product B (1 batch)
  const resBatchDesc = await listE6PharmacyProducts(
    prisma,
    { id: 1, role: 0 },
    { sortBy: "batchCount", sortOrder: "desc" },
  );
  assert.equal(resBatchDesc.list[0].id, 1);
  assert.equal(resBatchDesc.list[1].id, 2);

  // Sort by categoryName asc -> "西药" before "中药" (X before Z)
  const resCatAsc = await listE6PharmacyProducts(
    prisma,
    { id: 1, role: 0 },
    { sortBy: "categoryName", sortOrder: "asc" },
  );
  assert.equal(resCatAsc.list[0].categoryName, "西药");
  assert.equal(resCatAsc.list[1].categoryName, "中药");
});

test("E6 pharmacy query executes $queryRaw when aggregate sort is used", async () => {
  let rawSql = null;
  const prisma = {
    $queryRaw: async (sql) => {
      rawSql = sql;
      return [{ id: 2 }, { id: 1 }];
    },
    e6PharmacyProduct: {
      findMany: async ({ where }) => {
        if (where?.id?.in) {
          return [
            { id: 1, productCode: "001", name: "A", inventories: [] },
            { id: 2, productCode: "002", name: "B", inventories: [] },
          ];
        }
        return [];
      },
      count: async () => 2,
    },
  };

  const res = await listE6PharmacyProducts(
    prisma,
    { id: 1, role: 0 },
    { sortBy: "totalQuantity", sortOrder: "desc" },
  );

  assert.ok(rawSql);
  assert.equal(res.list.length, 2);
  assert.equal(res.list[0].id, 2);
  assert.equal(res.list[1].id, 1);
});
