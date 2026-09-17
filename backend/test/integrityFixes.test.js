import assert from "node:assert/strict";
import test from "node:test";
import { verifySuperAdmin } from "../src/middlewares/auth.js";
import adminRoutes from "../src/routes/adminRoutes.js";
import {
  describeChanges,
  listOperationLogs,
  recordOperation,
} from "../src/services/operationLogService.js";
import {
  nextPrescriptionNo,
  prescriptionBusinessDate,
} from "../src/services/prescriptionNoService.js";
import {
  createPrescription,
  listPrescriptions,
} from "../src/services/prescriptionService.js";
import {
  createPackage,
  deletePackage,
  listPackages,
} from "../src/services/adminPackageService.js";
import {
  getMyPackageDetail,
  listMyPackages,
} from "../src/services/userPackageService.js";
import {
  createProcessingPlan,
  reorderPrescriptionPlans,
  updateProcessingPlan,
} from "../src/services/processingPlanService.js";
import {
  createUser,
  deleteUser,
  listUsers,
  updateUser,
} from "../src/services/adminUserService.js";
import { listStores } from "../src/services/storeService.js";
import { normalizeOptionalPhone } from "../src/utils/validators.js";

const actor = { id: 12, role: 2, storeId: 3, phone: "13800000000" };

test("store list counts administrators from the admins relation", async () => {
  let include;
  const prisma = {
    store: {
      findMany: async (args) => {
        include = args.include;
        return [];
      },
      count: async () => 0,
    },
    admin: {
      groupBy: async () => [],
    },
  };

  await listStores(prisma, {});

  assert.deepEqual(include, {
    _count: { select: { admins: true, packages: true, herbs: true, herbLocations: true } },
  });
});

test("store list exposes separate administrator and staff counts", async () => {
  const prisma = {
    store: {
      findMany: async () => [{ id: 3, _count: { admins: 3 } }],
      count: async () => 1,
    },
    admin: {
      groupBy: async () => [
        { storeId: 3, role: 2, _count: { _all: 1 } },
        { storeId: 3, role: 3, _count: { _all: 2 } },
      ],
    },
  };

  const result = await listStores(prisma, {});
  assert.equal(result.list[0].adminCount, 1);
  assert.equal(result.list[0].staffCount, 2);
  assert.equal(result.list[0]._count.admins, 3);
});

test("optional business phones normalize blanks and still reject malformed values", () => {
  assert.equal(normalizeOptionalPhone(undefined), null);
  assert.equal(normalizeOptionalPhone("  "), null);
  assert.equal(normalizeOptionalPhone(" 13800000000 "), "13800000000");
  assert.throws(() => normalizeOptionalPhone("123"), { statusCode: 400 });
});

test("a package without a phone does not create an ordinary user", async () => {
  let userUpserts = 0;
  let createdData;
  const prisma = {
    store: {
      findUnique: async () => ({ id: 3, status: 1, deletedAt: null }),
    },
    user: {
      upsert: async () => {
        userUpserts += 1;
        return { id: 9 };
      },
    },
    package: {
      findUnique: async () => null,
      create: async ({ data }) => {
        createdData = data;
        return { id: 41, ...data };
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };

  await createPackage(prisma, actor, {
    itemName: "代煎药",
    receiverName: "张三",
    receiverPhone: "",
    pickupMethod: 0,
  });

  assert.equal(userUpserts, 0);
  assert.equal(createdData.receiverPhone, null);
});

test("a prescription can be created without a phone", async () => {
  let createdData;
  const prisma = {
    doctor: { findFirst: async () => ({ id: 1, status: 1 }) },
    dictionary: {
      findFirst: async () => ({ id: 2, type: "PrescriptionSource", status: 1 }),
    },
    store: {
      findUnique: async () => ({ id: 3, status: 1, deletedAt: null }),
    },
    prescriptionDailySequence: {
      upsert: async () => ({ currentValue: 1 }),
    },
    prescription: {
      create: async ({ data }) => {
        createdData = data;
        return { id: 8, ...data, plans: [] };
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };

  await createPrescription(prisma, actor, {
    customerName: "张三",
    phone: "",
    doctorId: 1,
    sourceId: 2,
  });

  assert.equal(createdData.phone, null);
});

test("operation log change descriptions include before and after values", () => {
  const description = describeChanges(
    { phone: "13800000000", status: 1, password: "old-hash" },
    { phone: "13900000000", status: 0, password: "new-hash" },
    [
      { key: "phone", label: "手机号" },
      { key: "status", label: "状态", values: { 0: "禁用", 1: "启用" } },
      { key: "password", label: "密码", sensitive: true },
    ],
  );

  assert.equal(
    description,
    "手机号：13800000000 → 13900000000；状态：启用 → 禁用；密码：已修改",
  );
  assert.doesNotMatch(description, /old-hash|new-hash/);
});

test("prescription numbers use the Shanghai business date around local midnight", async () => {
  const instant = new Date("2026-07-14T16:30:00.000Z");
  let sequenceDate;
  const prisma = {
    prescriptionDailySequence: {
      upsert: async ({ where }) => {
        sequenceDate = where.sequenceDate;
        return { currentValue: 1 };
      },
    },
  };

  assert.equal(prescriptionBusinessDate(instant), "2026-07-15");
  assert.equal(
    await nextPrescriptionNo(prisma, prisma, instant),
    "RX202607150001",
  );
  assert.equal(sequenceDate.toISOString(), "2026-07-15T00:00:00.000Z");
});

test("prescription detail queries exclude soft-deleted processing plans", async () => {
  let findManyArgs;
  const prisma = {
    prescription: {
      findMany: async (args) => {
        findManyArgs = args;
        return [];
      },
      count: async () => 0,
    },
  };

  await listPrescriptions(prisma, actor, { page: 1, pageSize: 20 });
  assert.deepEqual(findManyArgs.include.plans.where, { deletedAt: null });
});

test("READY_PICKUP processing plans cannot be edited", async () => {
  const prisma = {
    processingPlan: {
      findFirst: async () => ({ id: 21, status: 3, storeId: 3 }),
    },
  };

  await assert.rejects(
    () => updateProcessingPlan(prisma, actor, 21, { remark: "不应保存" }),
    { statusCode: 409 },
  );
});

test("packages generated by processing plans cannot be deleted independently", async () => {
  let updated = false;
  const prisma = {
    package: {
      findFirst: async () => ({
        id: 41,
        storeId: 3,
        processingPlanId: 21,
        deletedAt: null,
      }),
      update: async () => {
        updated = true;
      },
    },
  };

  await assert.rejects(() => deletePackage(prisma, actor, 41), {
    statusCode: 409,
  });
  assert.equal(updated, false);
});

test("package date scope supports today and overdue unpicked filters", async () => {
  const listWheres = [];
  const prisma = {
    package: {
      findMany: async ({ where }) => {
        listWheres.push(where);
        return [];
      },
      count: async () => 0,
    },
  };

  await listPackages(prisma, actor, { dateScope: "today" });
  await listPackages(prisma, actor, {
    dateScope: "overdue",
    status: 1,
  });
  await listPackages(prisma, actor, {
    dateScope: "pickup-workbench",
    source: "processing",
  });

  assert.equal(listWheres[0].storeId, actor.storeId);
  assert.ok(listWheres[0].createdAt.gte instanceof Date);
  assert.ok(listWheres[0].createdAt.lt instanceof Date);
  assert.equal(listWheres[1].storeId, actor.storeId);
  assert.equal(listWheres[1].status, 0);
  assert.ok(listWheres[1].createdAt.lt instanceof Date);
  assert.equal(listWheres[1].createdAt.gte, undefined);
  assert.equal(listWheres[2].storeId, actor.storeId);
  assert.deepEqual(listWheres[2].processingPlanId, { not: null });
  assert.equal(listWheres[2].AND[0].OR.length, 2);
  assert.ok(listWheres[2].AND[0].OR[0].createdAt.gte instanceof Date);
  assert.equal(listWheres[2].AND[0].OR[1].status, 0);
  assert.ok(listWheres[2].AND[0].OR[1].createdAt.lt instanceof Date);
});

test("ordinary users never receive soft-deleted packages", async () => {
  const user = { id: 7, role: 1, phone: "13800000000" };
  let listWhere;
  let detailWhere;
  const prisma = {
    package: {
      findMany: async ({ where }) => {
        listWhere = where;
        return [];
      },
      findFirst: async ({ where }) => {
        detailWhere = where;
        return null;
      },
    },
  };

  await listMyPackages(prisma, user);
  await assert.rejects(() => getMyPackageDetail(prisma, user, 41), {
    statusCode: 404,
  });
  assert.deepEqual(listWhere, {
    receiverPhone: user.phone,
    deletedAt: null,
  });
  assert.deepEqual(detailWhere, {
    id: 41,
    receiverPhone: user.phone,
    deletedAt: null,
  });
});

test("prescription batches can be reordered without colliding with soft-deleted batch numbers", async () => {
  const updates = [];
  const prisma = {
    prescription: {
      findFirst: async () => ({
        id: 8,
        storeId: 3,
        plans: [{ id: 31 }, { id: 32 }],
      }),
    },
    processingPlan: {
      findMany: async () => [{ id: 31 }, { id: 32 }, { id: 30 }],
      update: async ({ where, data }) => {
        updates.push({ id: where.id, batchNo: data.batchNo });
        return { id: where.id, ...data };
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };

  await reorderPrescriptionPlans(prisma, actor, 8, { ids: [32, 31] });
  assert.deepEqual(updates, [
    { id: 31, batchNo: -31 },
    { id: 32, batchNo: -32 },
    { id: 30, batchNo: -30 },
    { id: 32, batchNo: 1 },
    { id: 31, batchNo: 2 },
  ]);
});

function createPlanPrisma(existingPlan = null) {
  const state = { created: 0, restored: 0, shifted: null, log: null };
  const prisma = {
    prescription: {
      findFirst: async () => ({ id: 8, storeId: 3, status: 0 }),
    },
    dictionary: {
      findFirst: async ({ where }) => ({
        id: where.id || 9,
        code: where.code,
        status: 1,
      }),
    },
    processingPlan: {
      findFirst: async () => existingPlan,
      count: async () => 0,
      updateMany: async (args) => {
        state.shifted = args;
        return { count: 1 };
      },
      create: async ({ data }) => {
        state.created += 1;
        return {
          id: 31,
          ...data,
          processType: { name: "代煎" },
          prescription: {},
        };
      },
      update: async ({ data }) => {
        state.restored += 1;
        return {
          id: existingPlan.id,
          ...data,
          processType: { name: "代煎" },
          prescription: {},
        };
      },
    },
    operationLog: {
      create: async ({ data }) => {
        state.log = data;
        return { id: 1 };
      },
    },
    $transaction: async (work) => work(prisma),
  };
  return { prisma, state };
}

test("a deleted batch can be restored and an urgent task is inserted at queue position one", async () => {
  const { prisma, state } = createPlanPrisma({ id: 30, deletedAt: new Date() });
  const result = await createProcessingPlan(prisma, actor, {
    prescriptionId: 8,
    processTypeId: 5,
    batchNo: 2,
    totalDose: 7,
    scheduleType: 1,
    processDate: "2026-07-15",
    priority: 1,
    notifyType: 9,
    paymentStatus: 1,
  });

  assert.equal(state.created, 0);
  assert.equal(state.restored, 1);
  assert.equal(result.deletedAt, null);
  assert.equal(result.queueOrder, 1);
  assert.equal(result.notifyType, 9);
  assert.deepEqual(state.shifted.data, {
    queueOrder: { increment: 1 },
    updatedBy: actor.id,
  });
  assert.equal(state.log.action, "restore");
});

test("operation log failures are propagated", async () => {
  const prisma = {
    operationLog: {
      create: async () => Promise.reject(new Error("log unavailable")),
    },
  };
  await assert.rejects(
    () => recordOperation(prisma, actor, { module: "test" }),
    /log unavailable/,
  );
});

test("operation logs resolve operator and package names for readable audit details", async () => {
  const prisma = {
    operationLog: {
      findMany: async () => [
        {
          id: 1,
          actorId: 12,
          actorName: "13800000000",
          storeId: 3,
          module: "package",
          action: "verify",
          targetId: 41,
          description: "核销包裹",
        },
      ],
      count: async () => 1,
    },
    user: {
      findMany: async () => [
        { id: 12, name: "王药师", nickname: null, phone: "13800000000" },
      ],
    },
    package: {
      findMany: async () => [
        {
          id: 41,
          pickupCode: "123456",
          receiverName: "张三",
          itemName: "代煎 7剂 14袋",
        },
      ],
    },
  };

  const result = await listOperationLogs(prisma, { id: 1, role: 0 }, {});

  assert.equal(result.list[0].actorName, "王药师");
  assert.equal(result.list[0].targetLabel, "包裹「123456 张三 代煎 7剂 14袋」");
  assert.equal(
    result.list[0].description,
    "包裹「123456 张三 代煎 7剂 14袋」：核销包裹",
  );
});

test("operation logs route is protected by the super-admin guard", async () => {
  const routes = [];
  const fastify = {
    addHook() {},
    rateLimit: () => async () => {},
    get: (...args) => routes.push({ method: "GET", args }),
    post: (...args) => routes.push({ method: "POST", args }),
    put: (...args) => routes.push({ method: "PUT", args }),
    delete: (...args) => routes.push({ method: "DELETE", args }),
  };

  await adminRoutes(fastify);
  const route = routes.find(
    (item) => item.method === "GET" && item.args[0] === "/operation-logs",
  );
  const preHandlers = Array.isArray(route.args[1].preHandler)
    ? route.args[1].preHandler
    : [route.args[1].preHandler];
  assert.equal(preHandlers.includes(verifySuperAdmin), true);
});

test("store admins only list ordinary users", async () => {
  let listWhere;
  let countWhere;
  const prisma = {
    user: {
      findMany: async ({ where }) => {
        listWhere = where;
        return [];
      },
      count: async ({ where }) => {
        countWhere = where;
        return 0;
      },
    },
  };

  await listUsers(prisma, { lookup: () => null }, {}, actor);

  assert.deepEqual(listWhere, {});
  assert.deepEqual(countWhere, {});
});

test("store admins cannot update administrator accounts", async () => {
  const prisma = {
    user: {
      findUnique: async () => ({ id: 7, role: 2 }),
    },
  };

  await assert.rejects(
    () => updateUser(prisma, 7, { nickname: "越权修改" }, actor),
    { statusCode: 403 },
  );
});

test("store admins cannot create or delete users", async () => {
  await assert.rejects(
    () => createUser({}, { phone: "13800000000", password: "123456" }, actor),
    { statusCode: 403 },
  );
  await assert.rejects(() => deleteUser({}, 7, actor), { statusCode: 403 });
});
