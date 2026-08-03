import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import test from "node:test";
import { config } from "../src/config.js";
import { E6_IMPORT_STATUS } from "../src/constants/e6Integration.js";
import {
  confirmE6Import,
  receiveE6Prescription,
  saveE6StoreConfig,
} from "../src/services/e6IntegrationService.js";

const storeAdmin = {
  id: 12,
  role: 2,
  storeId: 3,
  phone: "13800000000",
};

const payload = {
  externalOrderNo: "E6-20260726-000123",
  storeCode: "SZ001",
  customerName: "张三",
  phone: "13800138000",
  e6DoctorCode: "D001",
  totalPrice: "268.00",
  doseCount: 7,
  remark: "饭后服用",
  sourceCreatedAt: "2026-07-26T10:22:00+08:00",
};

test("E6 import states are numeric and stable", () => {
  assert.deepEqual(E6_IMPORT_STATUS, {
    IMPORT_PENDING: 0,
    IMPORT_MAPPING_REQUIRED: 1,
    IMPORT_ERROR: 2,
    IMPORT_CONVERTED: 3,
    IMPORT_REJECTED: 4,
    IMPORT_CANCELLED: 5,
    IMPORT_CONFLICT: 6,
    IMPORT_PROCESSING: 7,
  });
});

test("enabling a store E6 integration generates a one-time API key", async () => {
  let storedStore;
  const prisma = {
    store: {
      findUnique: async () => ({
        id: 3,
        code: "SZ001",
        name: "苏州店",
        status: 1,
        e6Enabled: 0,
        e6ApiKeyHash: null,
        e6ApiKeyHint: null,
        e6LastUsedAt: null,
        e6RotatedAt: null,
      }),
      update: async ({ data }) => {
        storedStore = { id: 3, code: "SZ001", name: "苏州店", status: 1, ...data };
        return storedStore;
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
  };

  const result = await saveE6StoreConfig(prisma, storeAdmin, 3, { enabled: 1 });

  assert.equal(result.config.enabled, 1);
  assert.match(result.config.apiKey, /^e6_[A-Za-z0-9_-]+$/);
  assert.equal(storedStore.e6ApiKeyHash.length, 64);
  assert.equal(
    storedStore.e6ApiKeyHash,
    createHmac("sha256", config.e6ApiKeyHashSecret)
      .update(result.config.apiKey)
      .digest("hex"),
  );
  assert.equal("apiKeyHash" in result.config, false);
});

function syncFixture({ mapped = false } = {}) {
  const apiKey = "e6_test_key";
  const state = { import: null, prescriptionCreates: 0, operationLogs: [] };
  const prisma = {
    store: {
      findFirst: async () => ({
        id: 3,
        code: "SZ001",
        name: "苏州店",
        status: 1,
        e6Enabled: 1,
        e6ApiKeyHash: createHmac("sha256", config.e6ApiKeyHashSecret)
          .update(apiKey)
          .digest("hex"),
      }),
      update: async () => ({ id: 3 }),
    },
    e6DoctorMapping: {
      findFirst: async () =>
        mapped
          ? { id: 8, storeId: 3, e6DoctorCode: "D001", doctorId: 9, status: 1 }
          : null,
    },
    e6Import: {
      findUnique: async () => state.import && { ...state.import },
      create: async ({ data }) => {
        state.import = { id: 81, syncCount: 1, ...data };
        return { ...state.import };
      },
      update: async ({ data }) => {
        const increment = data.syncCount?.increment || 0;
        Object.assign(state.import, data, { syncCount: state.import.syncCount + increment });
        return { ...state.import };
      },
    },
    prescription: {
      create: async () => {
        state.prescriptionCreates += 1;
      },
    },
    operationLog: {
      create: async ({ data }) => {
        state.operationLogs.push(data);
        return { id: state.operationLogs.length, ...data };
      },
    },
  };
  prisma.$transaction = async (work) => work(prisma);
  return { apiKey, prisma, state };
}

test("E6 synchronization only creates an import and deduplicates the original order", async () => {
  const { apiKey, prisma, state } = syncFixture();

  const first = await receiveE6Prescription(prisma, payload, apiKey);
  const second = await receiveE6Prescription(prisma, payload, apiKey);

  assert.equal(first.status, E6_IMPORT_STATUS.IMPORT_MAPPING_REQUIRED);
  assert.equal(first.duplicate, false);
  assert.equal(second.duplicate, true);
  assert.equal(state.import.syncCount, 2);
  assert.equal(state.prescriptionCreates, 0);
  assert.deepEqual(
    state.operationLogs.map((item) => item.action),
    ["import_receive", "import_duplicate"],
  );
});

test("E6 synchronization rejects an API key from another store", async () => {
  const { prisma, state } = syncFixture();
  await assert.rejects(
    () => receiveE6Prescription(prisma, payload, "wrong-key"),
    { statusCode: 401 },
  );
  assert.equal(state.import, null);
});

test("a mapped E6 doctor leaves the synchronized order pending confirmation", async () => {
  const { apiKey, prisma } = syncFixture({ mapped: true });
  const result = await receiveE6Prescription(prisma, payload, apiKey);
  assert.equal(result.status, E6_IMPORT_STATUS.IMPORT_PENDING);
});

function confirmFixture() {
  const state = {
    import: {
      id: 81,
      storeId: 3,
      externalOrderNo: payload.externalOrderNo,
      customerName: payload.customerName,
      phone: payload.phone,
      e6DoctorCode: payload.e6DoctorCode,
      totalPrice: payload.totalPrice,
      doseCount: payload.doseCount,
      remark: payload.remark,
      status: E6_IMPORT_STATUS.IMPORT_PENDING,
      prescriptionId: null,
      processingPlanId: null,
    },
    prescription: null,
    plan: null,
    sequence: 0,
  };
  const prisma = {
    e6Import: {
      findFirst: async () => ({ ...state.import }),
      findUnique: async () => ({ ...state.import }),
      updateMany: async ({ where, data }) => {
        if (state.import.prescriptionId || !where.status.in.includes(state.import.status))
          return { count: 0 };
        Object.assign(state.import, data);
        return { count: 1 };
      },
      update: async ({ data }) => {
        Object.assign(state.import, data);
        return {
          ...state.import,
          store: { id: 3, code: "SZ001", name: "苏州店" },
          prescription: state.prescription,
          processingPlan: state.plan,
        };
      },
    },
    e6DoctorMapping: {
      findFirst: async () => ({
        id: 8,
        storeId: 3,
        e6DoctorCode: "D001",
        doctorId: 9,
        status: 1,
        doctor: { id: 9, name: "李医生", status: 1, deletedAt: null },
      }),
      findMany: async () => [],
    },
    doctor: {
      findFirst: async () => ({ id: 9, name: "李医生", status: 1 }),
    },
    dictionary: {
      findFirst: async ({ where }) => {
        if (where.type === "PrescriptionSource" || where.id === 10)
          return { id: 10, type: "PrescriptionSource", code: "E6", name: "E6系统", status: 1 };
        if (where.type === "ProcessType")
          return { id: 20, type: "ProcessType", code: "DIRECT", name: "直接发药", status: 1 };
        return { id: 30, type: "NotifyType", code: "NONE", name: "不提醒", status: 1 };
      },
    },
    store: {
      findUnique: async () => ({ id: 3, code: "SZ001", name: "苏州店", status: 1 }),
    },
    prescriptionDailySequence: {
      upsert: async () => ({ currentValue: ++state.sequence }),
    },
    prescription: {
      create: async ({ data }) => {
        state.prescription = {
          id: 100,
          ...data,
          doctor: { id: 9, name: "李医生" },
          source: { id: 10, code: "E6", name: "E6系统" },
          store: { id: 3, code: "SZ001", name: "苏州店" },
          plans: [],
        };
        return { ...state.prescription };
      },
      findFirst: async () => ({ id: 100, storeId: 3, status: 0 }),
    },
    processingPlan: {
      findFirst: async () => null,
      count: async () => 0,
      updateMany: async () => ({ count: 0 }),
      create: async ({ data }) => {
        state.plan = {
          id: 200,
          ...data,
          processType: { id: 20, code: "DIRECT", name: "直接发药" },
          prescription: state.prescription,
          store: { id: 3, code: "SZ001", name: "苏州店" },
          package: null,
        };
        return { ...state.plan };
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
  };
  prisma.$transaction = async (work) => work(prisma);
  return { prisma, state };
}

test("confirming an E6 import creates one prescription and one waiting plan", async () => {
  const { prisma, state } = confirmFixture();

  const result = await confirmE6Import(prisma, storeAdmin, 81, {
    processTypeId: 20,
    scheduleType: 1,
    processDate: "2026-07-27",
    pickupMethod: 1,
    expressAddress: "苏州市测试路 1 号",
  });

  assert.equal(result.status, E6_IMPORT_STATUS.IMPORT_CONVERTED);
  assert.equal(result.prescriptionId, 100);
  assert.equal(result.processingPlanId, 200);
  assert.equal(state.prescription.totalPrice, "268.00");
  assert.equal(state.plan.totalDose, 7);
  assert.equal(state.plan.remainingDose, 7);
  assert.equal(state.plan.status, 0);
  assert.match(state.plan.pickupCode, /^\d{6}$/);
  assert.equal(state.plan.expressAddress, "苏州市测试路 1 号");
});
