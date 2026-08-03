import assert from "node:assert/strict";
import test from "node:test";
import ExcelJS from "exceljs";
import {
  getHerbLocationLayout,
  importHerbLocationMoves,
  importHerbLocations,
  parseLocationCode,
  updateHerbLocationAssignment,
} from "../src/services/herbLocationService.js";

async function workbookBuffer(headers, rows) {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("导入");
  sheet.addRow(headers);
  rows.forEach((row) => sheet.addRow(row));
  return workbook.xlsx.writeBuffer();
}

test("parses the agreed D/G/F/C location code formats", () => {
  const sixColumnLayout = {
    drawerLayerCount: 8,
    drawerColumnCount: 6,
    drawerLayerColumns: [6, 6, 6, 6, 6, 6, 6, 6],
    bigCabinetUnitCount: 5,
  };
  assert.deepEqual(parseLocationCode("d-1-8-6", sixColumnLayout), {
    locationCode: "D-1-8-6",
    locationType: "D",
    unitNo: 1,
    layerNo: 8,
    columnNo: 6,
    slotNo: null,
    medicineCapacity: 3,
  });
  assert.equal(parseLocationCode("G-5-3").locationCode, "G-5-3");
  assert.equal(parseLocationCode("D365").locationCode, "D-3-6-5");
  assert.deepEqual(
    parseLocationCode("D1222", {
      drawerLayerCount: 8,
      drawerColumnCount: 6,
      drawerLayerColumns: [6, 6, 6, 6, 6, 6, 6, 3],
      bigCabinetUnitCount: 5,
    }),
    {
      locationCode: "D-1-2-2",
      locationType: "D",
      unitNo: 1,
      layerNo: 2,
      columnNo: 2,
      slotNo: 2,
      medicineCapacity: 3,
    },
  );
  assert.equal(parseLocationCode("G53").locationCode, "G-5-3");
  assert.equal(parseLocationCode("F-2-4").locationType, "F");
  assert.equal(parseLocationCode("C-3-7").locationType, "C");
});

test("rejects locations outside the known drug-drawer and opposite-cabinet layouts", () => {
  assert.throws(() => parseLocationCode("D-6-1-1"), { statusCode: 400 });
  assert.throws(() => parseLocationCode("D-1-9-1"), { statusCode: 400 });
  assert.throws(() => parseLocationCode("G-1-4"), { statusCode: 400 });
  assert.throws(() => parseLocationCode("F-1-1-1"), { statusCode: 400 });
});

test("uses the configured opposite-cabinet layer count", () => {
  const layout = { bigCabinetUnitCount: 5, bigCabinetLayerCount: 2 };
  assert.equal(parseLocationCode("G-1-2", layout).locationCode, "G-1-2");
  assert.throws(() => parseLocationCode("G-1-3", layout), { statusCode: 400 });
});

test("uses the current store drawer layout when validating a D location", () => {
  const layout = {
    drawerLayerCount: 9,
    drawerColumnCount: 7,
    drawerLayerColumns: [7, 7, 7, 7, 7, 7, 7, 7, 7],
    bigCabinetUnitCount: 5,
  };
  assert.equal(parseLocationCode("D197", layout).locationCode, "D-1-9-7");
  assert.throws(() => parseLocationCode("D198", layout), { statusCode: 400 });
});

test("supports a top row and cabinet-specific column counts", () => {
  const drawerLayerColumns = Array.from({ length: 5 }, () => [
    6, 6, 6, 6, 6, 6, 6, 6, 3,
  ]);
  drawerLayerColumns[2][8] = 6;
  const layout = {
    drawerLayerCount: 8,
    drawerLayerColumns,
    drawerTopColumnCount: 6,
    bigCabinetUnitCount: 5,
  };

  assert.equal(parseLocationCode("D102", layout).locationCode, "D-1-0-2");
  assert.equal(parseLocationCode("D386", layout).locationCode, "D-3-8-6");
  assert.throws(() => parseLocationCode("D186", layout), { statusCode: 400 });
});

test("uses the configured drawer cabinet count", () => {
  const drawerLayerColumns = Array.from({ length: 6 }, () => [
    6, 6, 6, 6, 6, 6, 6, 6, 3,
  ]);
  const layout = {
    drawerUnitCount: 6,
    drawerLayerCount: 8,
    drawerLayerColumns,
    bigCabinetUnitCount: 5,
  };

  assert.equal(parseLocationCode("D611", layout).locationCode, "D-6-1-1");
});

test("store administrators only read the layout of their assigned store", async () => {
  const store = {
    id: 7,
    name: "测试门店",
    code: "STORE7",
    address: null,
    phone: null,
    status: 1,
    deletedAt: null,
    drawerUnitCount: 5,
    drawerLayerCount: 8,
    drawerLayerColumns: "[6,6,6,6,6,6,6,3]",
    drawerTopColumnCount: 6,
    bigCabinetUnitCount: 5,
  };
  const prisma = {
    store: {
      findUnique: async ({ where }) => {
        assert.equal(where.id, 7);
        return store;
      },
      findFirst: async ({ where }) => {
        assert.equal(where.id, 7);
        return store;
      },
    },
  };

  const result = await getHerbLocationLayout(
    prisma,
    { id: 10, role: 2, storeId: 7 },
    { storeId: 999 },
  );

  assert.equal(result.store.id, 7);
  assert.equal(result.layout.drawerLayerColumns.length, 5);
});

test("updates the final D-code digit used for ordering herbs in a drawer", async () => {
  const location = {
    id: 20,
    storeId: 7,
    locationCode: "D-1-2-2",
    locationType: "D",
    unitNo: 1,
    layerNo: 2,
    columnNo: 2,
    medicineCapacity: 3,
  };
  let updateData;
  const assignment = {
    id: 30,
    herbId: 40,
    locationId: 20,
    slotNo: 2,
    location,
    herb: { id: 40, name: "花椒" },
  };
  const transaction = {
    herbLocation: { findUnique: async () => location },
    herbLocationAssignment: {
      findFirst: async () => null,
      update: async ({ data }) => {
        updateData = data;
        return assignment;
      },
    },
  };
  const prisma = {
    store: {
      findUnique: async () => ({ id: 7, status: 1, deletedAt: null }),
      findFirst: async () => ({
        drawerUnitCount: 5,
        drawerLayerCount: 8,
        drawerLayerColumns: "[6,6,6,6,6,6,6,3]",
        drawerTopColumnCount: 6,
        bigCabinetUnitCount: 5,
      }),
    },
    herbLocationAssignment: { findUnique: async () => assignment },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (callback) => callback(transaction),
  };

  await updateHerbLocationAssignment(
    prisma,
    { id: 10, role: 2, storeId: 7 },
    30,
    { locationCode: "D1221" },
  );

  assert.deepEqual(updateData, { locationId: 20, slotNo: 1 });
});

test("temporarily releases a slot before swapping two assignments", async () => {
  const location = {
    id: 20,
    storeId: 7,
    locationCode: "D-1-2-2",
    locationType: "D",
    unitNo: 1,
    layerNo: 2,
    columnNo: 2,
    medicineCapacity: 3,
  };
  const assignment = {
    id: 30,
    herbId: 40,
    locationId: 20,
    slotNo: 2,
    location,
    herb: { id: 40, name: "花椒" },
  };
  const updates = [];
  const transaction = {
    herbLocation: { findUnique: async () => location },
    herbLocationAssignment: {
      findFirst: async ({ where }) =>
        where.slotNo === 1 ? { id: 31, herbId: 41, slotNo: 1 } : null,
      update: async (args) => {
        updates.push(args);
        return assignment;
      },
    },
  };
  const prisma = {
    store: {
      findUnique: async () => ({ id: 7, status: 1, deletedAt: null }),
      findFirst: async () => ({
        drawerUnitCount: 5,
        drawerLayerCount: 8,
        drawerLayerColumns: "[6,6,6,6,6,6,6,3]",
        drawerTopColumnCount: 6,
        bigCabinetUnitCount: 5,
      }),
    },
    herbLocationAssignment: { findUnique: async () => assignment },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (callback) => callback(transaction),
  };

  await updateHerbLocationAssignment(
    prisma,
    { id: 10, role: 2, storeId: 7 },
    30,
    { locationCode: "D1221" },
  );

  assert.deepEqual(updates, [
    { where: { id: 30 }, data: { slotNo: null } },
    { where: { id: 31 }, data: { slotNo: 2 } },
    { where: { id: 30 }, data: { locationId: 20, slotNo: 1 } },
  ]);
});

test("import updates an existing herb by code inside one transaction", async () => {
  const layout = {
    drawerUnitCount: 5,
    drawerLayerCount: 8,
    drawerLayerColumns: "[6,6,6,6,6,6,6,3]",
    drawerTopColumnCount: 6,
    bigCabinetUnitCount: 5,
    bigCabinetLayerCount: 2,
  };
  const location = { id: 20, locationCode: "D-1-2-2" };
  const existingHerb = {
    id: 40,
    storeId: 7,
    code: "HJ",
    name: "老名称",
    specification: "统货",
    status: 1,
  };
  let herbUpdate;
  let transactionOptions;
  const transaction = {
    herbLocation: {
      createMany: async () => ({ count: 0 }),
      findUnique: async () => location,
    },
    herb: {
      findFirst: async () => existingHerb,
      update: async ({ data }) => {
        herbUpdate = data;
        return { ...existingHerb, ...data };
      },
    },
    herbLocationAssignment: {
      findUnique: async () => ({ id: 30, locationId: 20, herbId: 40 }),
    },
    operationLog: { create: async () => ({ id: 1 }) },
  };
  const prisma = {
    store: {
      findUnique: async () => ({ id: 7, status: 1, deletedAt: null }),
      findFirst: async () => layout,
    },
    $transaction: async (callback, options) => {
      transactionOptions = options;
      return callback(transaction);
    },
  };
  const buffer = await workbookBuffer(
    ["位置编号", "药材编码", "药材名称", "规格"],
    [["D122", "HJ", "花椒", "选货"]],
  );

  const result = await importHerbLocations(
    prisma,
    { id: 10, role: 2, storeId: 7 },
    7,
    { buffer },
  );

  assert.deepEqual(result, { total: 1, added: 0, updated: 1, skipped: 0 });
  assert.deepEqual(herbUpdate, {
    name: "花椒",
    specification: "选货",
    updatedBy: 10,
  });
  assert.equal(transactionOptions.timeout, 60000);
});

test("batch location import swaps drawer slot numbers atomically", async () => {
  const layout = {
    drawerUnitCount: 5,
    drawerLayerCount: 8,
    drawerLayerColumns: "[6,6,6,6,6,6,6,3]",
    drawerTopColumnCount: 6,
    bigCabinetUnitCount: 5,
    bigCabinetLayerCount: 2,
  };
  const location = {
    id: 20,
    storeId: 7,
    locationCode: "D-1-2-2",
    locationType: "D",
  };
  const herbs = {
    HJ: { id: 40, storeId: 7, code: "HJ", name: "花椒", status: 1 },
    HQ: { id: 41, storeId: 7, code: "HQ", name: "黄芪", status: 1 },
  };
  const assignments = {
    40: { id: 30, locationId: 20, herbId: 40, slotNo: 1 },
    41: { id: 31, locationId: 20, herbId: 41, slotNo: 2 },
  };
  const updates = [];
  const transaction = {
    herbLocation: {
      createMany: async () => ({ count: 0 }),
      findUnique: async () => location,
    },
    herb: {
      findMany: async ({ where }) => [herbs[where.code]],
    },
    herbLocationAssignment: {
      findUnique: async ({ where }) => {
        const key = where.locationId_herbId;
        return assignments[key.herbId] || null;
      },
      findFirst: async ({ where }) =>
        Object.values(assignments).find(
          (item) =>
            item.locationId === where.locationId && item.slotNo === where.slotNo,
        ) || null,
      update: async (args) => {
        updates.push(args);
        return { id: args.where.id, ...args.data };
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
  };
  const prisma = {
    store: {
      findUnique: async () => ({ id: 7, status: 1, deletedAt: null }),
      findFirst: async () => layout,
    },
    $transaction: async (callback) => callback(transaction),
  };
  const buffer = await workbookBuffer(
    ["原位置", "新位置", "药材编码", "药材名称"],
    [
      ["D1221", "D1222", "HJ", "花椒"],
      ["D1222", "D1221", "HQ", "黄芪"],
    ],
  );

  const result = await importHerbLocationMoves(
    prisma,
    { id: 10, role: 2, storeId: 7 },
    7,
    { buffer },
  );

  assert.deepEqual(result, { total: 2, moved: 2, skipped: 0 });
  assert.deepEqual(updates, [
    { where: { id: 30 }, data: { slotNo: null } },
    { where: { id: 31 }, data: { slotNo: null } },
    { where: { id: 30 }, data: { locationId: 20, slotNo: null } },
    { where: { id: 31 }, data: { locationId: 20, slotNo: null } },
    { where: { id: 30 }, data: { slotNo: 2 } },
    { where: { id: 31 }, data: { slotNo: 1 } },
  ]);
});
