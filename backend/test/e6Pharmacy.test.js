import assert from "node:assert/strict";
import test from "node:test";
import { listE6PharmacyProducts } from "../src/services/e6PharmacyService.js";

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
    e6CreatedAt: null,
    e6ModifiedAt: null,
    lastInventorySeenAt: null,
    store: { id: 3, name: "苏州店", code: "SZ001" },
    inventories: [
      {
        id: 20,
        batchNo: "G011226",
        productionDate: new Date("2026-01-23T00:00:00.000Z"),
        expiryDate: new Date("2029-01-22T00:00:00.000Z"),
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
});
