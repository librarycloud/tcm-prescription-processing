import assert from "node:assert/strict";
import test from "node:test";
import { listE6PharmacyProducts } from "../src/services/e6PharmacyService.js";
import { mergeBatches } from "../src/services/e6PharmacySyncService.js";

test("E6 pharmacy batches keep locations separate", () => {
  const result = mergeBatches([
    { e6ProductId: 99052, batchNo: "2411061", locationName: "A货位", quantity: "2.000", amount: "12.34" },
    { e6ProductId: 99052, batchNo: "2411061", locationName: "B货位", quantity: "3.000", amount: "12.34" },
    { e6ProductId: 99052, batchNo: "2411061", locationName: "A货位", quantity: "1.000", amount: "12.34" },
  ]);

  assert.equal(result.length, 2);
  assert.equal(result[0].quantity, "3.000");
  assert.equal(result[0].amount, "12.34");
  assert.equal(result[0].locationName, "A货位");
  assert.equal(result[1].quantity, "3.000");
  assert.equal(result[1].locationName, "B货位");
});

test("E6 pharmacy query scopes store admins and searches product fields", async () => {
  const calls = [];
  const product = {
    id: 10,
    storeId: 3,
    e6ProductId: 107889,
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
        amount: "121.04",
        receivedAt: new Date("2026-08-22T01:00:00.000Z"),
        updatedAt: new Date("2026-08-22T01:00:00.000Z"),
      },
    ],
  };
  const prisma = {
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
  assert.equal(findArgs.where.storeId, 3);
  assert.deepEqual(findArgs.where.OR, [
    { productCode: { contains: "690000" } },
    { name: { contains: "690000" } },
    { barcode: { contains: "690000" } },
  ]);
  assert.equal(result.pagination.total, 1);
  assert.equal(result.list[0].totalQuantity, 3);
  assert.equal(result.list[0].inventories[0].batchNo, "G011226");
  assert.equal(result.list[0].unit, "盒");
  assert.equal(result.list[0].inventories[0].locationName, "一号货位");
});

test("E6 pharmacy expiry filter applies to products and inventory details", async () => {
  const calls = [];
  const prisma = {
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
