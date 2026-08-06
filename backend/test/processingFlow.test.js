import assert from "node:assert/strict";
import test from "node:test";
import {
  getStats,
  verifyPackage,
} from "../src/services/adminPackageService.js";
import {
  getProcessingCalendar,
  deleteProcessingPlan,
  generateProcessingPlanPackage,
  listProcessingPlans,
  receiveProcessingNotice,
  reorderProcessingQueue,
  restoreProcessingQueue,
  transitionProcessingPlan,
  updateProcessingPlan,
} from "../src/services/processingPlanService.js";
import {
  EQUIPMENT_USAGE_STATUS,
  PROCESSING_STAGE,
} from "../src/constants/processingWorkflow.js";
import { syncPrescriptionStatus } from "../src/services/prescriptionService.js";

const storeAdmin = { id: 12, role: 2, storeId: 3, phone: "13800000000" };

test("deleting the final unfinished plan completes a prescription with only completed plans left", async () => {
  const state = {
    prescription: { id: 8, status: 0, updatedBy: null },
    plans: [
      {
        id: 21,
        prescriptionId: 8,
        status: 4,
        remainingDose: 0,
        deletedAt: null,
      },
      {
        id: 22,
        prescriptionId: 8,
        status: 0,
        storeId: 3,
        deletedAt: null,
        prescription: { id: 8, storeId: 3 },
      },
    ],
  };
  const prisma = {
    processingPlan: {
      findFirst: async ({ where }) => {
        const plan = state.plans.find(
          (item) => item.id === where.id && item.deletedAt === null,
        );
        return plan && { ...plan };
      },
      update: async ({ where, data }) => {
        const plan = state.plans.find((item) => item.id === where.id);
        Object.assign(plan, data);
        return { ...plan };
      },
    },
    prescription: {
      findUnique: async () => ({
        ...state.prescription,
        plans: state.plans
          .filter((item) => item.deletedAt === null)
          .map(({ status }) => ({ status })),
      }),
      update: async ({ data }) => Object.assign(state.prescription, data),
    },
    operationLog: { create: async () => ({ id: 1 }) },
  };

  await deleteProcessingPlan(prisma, storeAdmin, 22);

  assert.ok(state.plans[1].deletedAt instanceof Date);
  assert.equal(state.prescription.status, 1);
  assert.equal(state.prescription.updatedBy, storeAdmin.id);
});

test("cancelled plans do not block prescription completion", async () => {
  const prescription = { id: 8, status: 0, updatedBy: null };
  const plans = [{ status: 2 }, { status: 5 }];
  const prisma = {
    prescription: {
      findUnique: async () => ({ ...prescription, plans }),
      update: async ({ data }) => Object.assign(prescription, data),
    },
  };

  await syncPrescriptionStatus(prisma, prescription.id, storeAdmin.id);

  assert.equal(prescription.status, 1);
  assert.equal(prescription.updatedBy, storeAdmin.id);
});

test("a prescription with only cancelled plans is not completed", async () => {
  const prescription = { id: 8, status: 1, updatedBy: null };
  const prisma = {
    prescription: {
      findUnique: async () => ({
        ...prescription,
        plans: [{ status: 5 }, { status: 5 }],
      }),
      update: async ({ data }) => Object.assign(prescription, data),
    },
  };

  await syncPrescriptionStatus(prisma, prescription.id, storeAdmin.id);

  assert.equal(prescription.status, 0);
  assert.equal(prescription.updatedBy, storeAdmin.id);
});

function processingFixture() {
  const state = {
    packageCreates: 0,
    userUpserts: 0,
    plan: {
      id: 21,
      prescriptionId: 8,
      processTypeId: 5,
      batchNo: 1,
      totalDose: 7,
      bagCount: 14,
      volumeMl: 200,
      takenDose: 0,
      remainingDose: 7,
      scheduleType: 1,
      processDate: new Date("2026-07-15T00:00:00"),
      status: 1,
      priority: 0,
      notifyType: 5,
      notifyStatus: 0,
      paymentStatus: 1,
      pickupMethod: 2,
      pickupCode: "654321",
      expressAddress: "苏州市测试路 1 号",
      storeId: 3,
      processType: { id: 5, code: "DECOCTION", name: "代煎" },
      dispensingCompletedAt: new Date(),
      prescription: {
        id: 8,
        customerName: "张三",
        phone: "13800000000",
        storeId: 3,
        status: 0,
        doctor: { id: 1, name: "李医生" },
        source: { id: 2, name: "本院" },
      },
      store: { id: 3, name: "苏州店" },
      package: null,
    },
    package: null,
  };
  const prisma = {
    processingPlan: {
      findFirst: async () => ({ ...state.plan }),
      count: async () => 0,
      updateMany: async () => ({ count: 0 }),
      update: async ({ data }) => {
        Object.assign(state.plan, data);
        return { ...state.plan, package: state.package };
      },
    },
    processingEquipmentUsage: {
      findMany: async () =>
        [
          PROCESSING_STAGE.SOAKING,
          PROCESSING_STAGE.DECOCTING,
          PROCESSING_STAGE.PACKAGING,
        ].map((stage) => ({
          stage,
          portionNo: 1,
          status: EQUIPMENT_USAGE_STATUS.COMPLETED,
          endedAt: new Date(),
          voidedAt: null,
        })),
    },
    prescription: {
      findFirst: async () => ({ id: 8, storeId: 3, status: 0 }),
    },
    dictionary: {
      findFirst: async () => ({
        id: 5,
        type: "ProcessType",
        code: state.plan.processType.code,
        name: state.plan.processType.name,
        status: 1,
      }),
    },
    package: {
      findUnique: async ({ where }) => {
        if (where.processingPlanId)
          return state.package && { id: state.package.id };
        return null;
      },
      create: async ({ data }) => {
        state.packageCreates += 1;
        state.package = { id: 30, status: 0, ...data };
        return state.package;
      },
    },
    user: {
      upsert: async () => {
        state.userUpserts += 1;
        return { id: 40 };
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };
  return { prisma, state };
}

test("finishing a processing plan creates exactly one linked package", async () => {
  const { prisma, state } = processingFixture();

  const result = await transitionProcessingPlan(prisma, storeAdmin, 21, {
    status: 2,
  });

  assert.equal(result.status, 3);
  assert.equal(result.takenDose, result.totalDose);
  assert.equal(result.remainingDose, 0);
  assert.equal(state.packageCreates, 1);
  assert.equal(state.package.processingPlanId, 21);
  assert.equal(state.package.storeId, 3);
  assert.equal(state.package.itemName, "代煎 7剂 14袋");
  assert.equal(state.package.receiverPhone, "13800000000");
  assert.equal(state.package.pickupMethod, 2);
  assert.equal(state.userUpserts, 1);
  assert.equal(state.package.pickupCode, "654321");
  assert.equal(state.package.expressAddress, "苏州市测试路 1 号");
  await assert.rejects(
    () => transitionProcessingPlan(prisma, storeAdmin, 21, { status: 2 }),
    {
      statusCode: 409,
    },
  );
  assert.equal(state.packageCreates, 1);
});

test("non-decoction package names do not include bag count", async () => {
  const { prisma, state } = processingFixture();
  state.plan.processType = { id: 6, code: "DIRECT", name: "直接发药" };

  await transitionProcessingPlan(prisma, storeAdmin, 21, {
    status: 2,
  });

  assert.equal(state.package.itemName, "直接发药 7剂");
});

test("finishing a prescription without a phone creates a package but not a user", async () => {
  const { prisma, state } = processingFixture();
  state.plan.prescription.phone = null;

  const result = await transitionProcessingPlan(prisma, storeAdmin, 21, {
    status: 2,
  });

  assert.equal(result.status, 3);
  assert.equal(state.packageCreates, 1);
  assert.equal(state.package.receiverPhone, null);
  assert.equal(state.userUpserts, 0);
});

test("a finished plan can defer package creation and generate it later", async () => {
  const { prisma, state } = processingFixture();

  const finished = await transitionProcessingPlan(prisma, storeAdmin, 21, {
    status: 2,
    createPackage: false,
  });

  assert.equal(finished.status, 2);
  assert.equal(finished.takenDose, finished.totalDose);
  assert.equal(finished.remainingDose, 0);
  assert.ok(finished.finishDate instanceof Date);
  assert.equal(state.packageCreates, 0);
  assert.equal(state.userUpserts, 0);

  const ready = await generateProcessingPlanPackage(
    prisma,
    storeAdmin,
    finished.id,
  );

  assert.equal(ready.status, 3);
  assert.equal(state.packageCreates, 1);
  assert.equal(state.package.processingPlanId, finished.id);
  assert.equal(state.package.itemName, "代煎 7剂 14袋");
  assert.equal(state.userUpserts, 1);
  await assert.rejects(
    () => generateProcessingPlanPackage(prisma, storeAdmin, finished.id),
    {
      statusCode: 409,
    },
  );
  assert.equal(state.packageCreates, 1);
});

test("starting a processing plan records its actual start time", async () => {
  const { prisma, state } = processingFixture();
  state.plan.status = 0;

  const result = await transitionProcessingPlan(prisma, storeAdmin, 21, {
    status: 1,
  });

  assert.equal(result.status, 1);
  assert.ok(result.startDate instanceof Date);
  assert.equal(state.packageCreates, 0);
});

test("started plans can only be cancelled before dispensing is completed", async () => {
  const { prisma, state } = processingFixture();
  const prescription = { id: 8, status: 0, updatedBy: null };
  prisma.prescription.findUnique = async () => ({
    ...prescription,
    plans: [{ status: state.plan.status }, { status: 2 }],
  });
  prisma.prescription.update = async ({ data }) =>
    Object.assign(prescription, data);
  Object.assign(state.plan, {
    currentStage: PROCESSING_STAGE.DISPENSING,
    dispensingCompletedAt: null,
  });

  const cancelled = await transitionProcessingPlan(prisma, storeAdmin, 21, {
    status: 5,
  });

  assert.equal(cancelled.status, 5);
  assert.equal(prescription.status, 1);
  assert.equal(prescription.updatedBy, storeAdmin.id);

  Object.assign(state.plan, {
    status: 1,
    currentStage: PROCESSING_STAGE.DISPENSING_DONE,
    dispensingCompletedAt: new Date(),
  });
  await assert.rejects(
    () => transitionProcessingPlan(prisma, storeAdmin, 21, { status: 5 }),
    {
      statusCode: 409,
      message: "只有尚未完成调配的加工计划可以取消",
    },
  );
});

test("receiving notice assigns the plan to the scheduled day's queue", async () => {
  const { prisma, state } = processingFixture();
  Object.assign(state.plan, {
    status: 0,
    scheduleType: 2,
    processDate: null,
    queueOrder: null,
  });

  const updated = await receiveProcessingNotice(prisma, storeAdmin, 21, {
    processDate: "2026-07-16",
  });

  assert.equal(updated.scheduleType, 1);
  assert.equal(updated.queueOrder, 1);
  assert.equal(updated.processDate.getFullYear(), 2026);
  assert.equal(updated.processDate.getMonth(), 6);
  assert.equal(updated.processDate.getDate(), 16);
});

function decoctionPlanFixture() {
  const pkg = { id: 31, pickupMethod: 0 };
  const plan = {
    id: 21,
    prescriptionId: 8,
    processTypeId: 5,
    batchNo: 1,
    totalDose: 7,
    bagCount: null,
    volumeMl: null,
    takenDose: 0,
    remainingDose: 7,
    scheduleType: 1,
    processDate: new Date("2026-07-18T00:00:00"),
    queueOrder: 1,
    status: 0,
    priority: 0,
    notifyType: 6,
    notifyStatus: 0,
    paymentStatus: 1,
    pickupMethod: 0,
    storeId: 3,
    processType: { id: 5, code: "DECOCTION", name: "代煎" },
    prescription: { id: 8, storeId: 3, status: 0 },
    store: { id: 3, name: "苏州店" },
    package: null,
  };
  const prisma = {
    processingPlan: {
      findFirst: async ({ select } = {}) =>
        select?.queueOrder ? { queueOrder: 4 } : { ...plan },
      update: async ({ data }) => Object.assign(plan, data),
      updateMany: async () => ({ count: 0 }),
      count: async () => 0,
    },
    prescription: {
      findFirst: async () => ({ id: 8, storeId: 3, status: 0 }),
    },
    dictionary: {
      findFirst: async ({ where }) =>
        where.type === "ProcessType"
          ? { id: 5, code: "DECOCTION", name: "代煎", status: 1 }
          : { id: 6, code: where.code, name: "提醒方式", status: 1 },
    },
    package: {
      update: async ({ data }) => Object.assign(pkg, data),
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };
  return { prisma, plan, pkg };
}

test("decoction plans require bag count and volume", async () => {
  const { prisma } = decoctionPlanFixture();

  await assert.rejects(
    () => updateProcessingPlan(prisma, storeAdmin, 21, { bagCount: 14 }),
    {
      statusCode: 400,
    },
  );
});

test("decoction plans persist bag count and volume", async () => {
  const { prisma, plan } = decoctionPlanFixture();

  const result = await updateProcessingPlan(prisma, storeAdmin, 21, {
    bagCount: 14,
    volumeMl: 200,
  });

  assert.equal(result.bagCount, 14);
  assert.equal(result.volumeMl, 200);
  assert.equal(plan.bagCount, 14);
  assert.equal(plan.volumeMl, 200);
});

test("active processing plans allow metadata-only quick updates", async () => {
  const { prisma, plan } = decoctionPlanFixture();

  const result = await updateProcessingPlan(prisma, storeAdmin, 21, {
    notifyType: 6,
    notifyStatus: 1,
    paymentStatus: 0,
    pickupMethod: 1,
  });

  assert.equal(result.notifyType, 6);
  assert.equal(result.notifyStatus, 1);
  assert.equal(result.paymentStatus, 0);
  assert.equal(result.pickupMethod, 1);
  assert.equal(plan.bagCount, null);
  assert.equal(plan.volumeMl, null);
});

test("waiting plans allow schedule-only quick updates", async () => {
  const { prisma, plan } = decoctionPlanFixture();

  const scheduled = await updateProcessingPlan(prisma, storeAdmin, 21, {
    scheduleType: 1,
    processDate: "2026-07-22",
  });

  assert.equal(scheduled.scheduleType, 1);
  assert.equal(scheduled.processDate.getDate(), 22);
  assert.equal(scheduled.queueOrder, 5);
  assert.equal(plan.bagCount, null);
  assert.equal(plan.volumeMl, null);

  const waitingNotice = await updateProcessingPlan(prisma, storeAdmin, 21, {
    scheduleType: 2,
    processDate: null,
  });

  assert.equal(waitingNotice.scheduleType, 2);
  assert.equal(waitingNotice.processDate, null);
  assert.equal(waitingNotice.queueOrder, null);
});

test("finished processing plans allow metadata updates only", async () => {
  const { prisma, plan, pkg } = decoctionPlanFixture();
  plan.status = 3;
  plan.package = pkg;

  const result = await updateProcessingPlan(prisma, storeAdmin, 21, {
    notifyType: 6,
    notifyStatus: 1,
    paymentStatus: 0,
    pickupMethod: 2,
  });

  assert.equal(result.notifyType, 6);
  assert.equal(result.notifyStatus, 1);
  assert.equal(result.paymentStatus, 0);
  assert.equal(result.pickupMethod, 2);
  assert.equal(pkg.pickupMethod, 2);
  assert.ok(result.notifyTime instanceof Date);
  await assert.rejects(
    () => updateProcessingPlan(prisma, storeAdmin, 21, { bagCount: 14 }),
    {
      statusCode: 409,
    },
  );
});

function pickupFixture() {
  const state = {
    prescription: { id: 8, status: 0, updatedBy: null },
    plans: [
      {
        id: 21,
        prescriptionId: 8,
        status: 3,
        totalDose: 7,
        takenDose: 0,
      },
      {
        id: 22,
        prescriptionId: 8,
        status: 3,
        totalDose: 5,
        takenDose: 0,
      },
    ],
    packages: [
      {
        id: 31,
        pickupCode: "001001",
        storeId: 3,
        status: 0,
        processingPlanId: 21,
      },
      {
        id: 32,
        pickupCode: "001002",
        storeId: 3,
        status: 0,
        processingPlanId: 22,
      },
    ],
  };
  const prisma = {
    package: {
      findFirst: async ({ where }) =>
        state.packages.find(
          (item) =>
            item.pickupCode === where.pickupCode &&
            item.storeId === where.storeId &&
            item.deletedAt == null,
        ) || null,
      updateMany: async ({ where, data }) => {
        const item = state.packages.find(
          (entry) => entry.id === where.id && entry.status === where.status,
        );
        if (!item) return { count: 0 };
        Object.assign(item, data);
        return { count: 1 };
      },
      findUnique: async ({ where }) => {
        const item = state.packages.find((entry) => entry.id === where.id);
        return item && { ...item, store: { id: 3, name: "苏州店" } };
      },
    },
    processingPlan: {
      findUnique: async ({ where }) => {
        const plan = state.plans.find((item) => item.id === where.id);
        return plan && { ...plan };
      },
      update: async ({ where, data }) => {
        const plan = state.plans.find((item) => item.id === where.id);
        Object.assign(plan, data);
        return plan;
      },
    },
    prescription: {
      findUnique: async () => ({
        ...state.prescription,
        plans: state.plans.map(({ status }) => ({ status })),
      }),
      update: async ({ data }) => Object.assign(state.prescription, data),
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };
  return { prisma, state };
}

test("package verification picks the whole batch and completes only the final batch prescription", async () => {
  const { prisma, state } = pickupFixture();

  await verifyPackage(prisma, storeAdmin, {
    pickupCode: "001001",
    pickupMethod: 0,
  });
  assert.deepEqual(
    {
      status: state.plans[0].status,
      takenDose: state.plans[0].takenDose,
      remainingDose: state.plans[0].remainingDose,
    },
    { status: 4, takenDose: 7, remainingDose: 0 },
  );
  assert.equal(state.plans[0].updatedBy, storeAdmin.id);
  assert.equal(state.prescription.status, 0);

  await verifyPackage(prisma, storeAdmin, {
    pickupCode: "001002",
    pickupMethod: 2,
    expressTrackingNo: "SF1234567890123",
  });
  assert.equal(state.plans[1].status, 4);
  assert.equal(state.packages[1].expressTrackingNo, "SF1234567890123");
  assert.equal(state.prescription.status, 1);
  assert.equal(state.prescription.updatedBy, storeAdmin.id);
});

test("processing list, calendar and dashboard statistics enforce the actor store", async () => {
  const listWheres = [];
  const calendarWheres = [];
  const packageWheres = [];
  const prescriptionWheres = [];
  const statsWheres = [];
  const prisma = {
    processingPlan: {
      findMany: async (args) => {
        if (args.select?.processDate) calendarWheres.push(args.where);
        else listWheres.push(args.where);
        return [];
      },
      count: async ({ where }) => {
        statsWheres.push(where);
        return 0;
      },
      findFirst: async ({ where }) => {
        statsWheres.push(where);
        return null;
      },
    },
    package: {
      count: async ({ where }) => {
        packageWheres.push(where);
        return 0;
      },
      findMany: async ({ where }) => {
        packageWheres.push(where);
        return [];
      },
    },
    prescription: {
      count: async ({ where }) => {
        prescriptionWheres.push(where);
        return 0;
      },
    },
    store: {
      findUnique: async () => ({ id: 3, name: "苏州店", status: 1 }),
    },
  };

  await listProcessingPlans(prisma, storeAdmin, {
    storeId: 99,
    page: 1,
    pageSize: 20,
  });
  await getProcessingCalendar(prisma, storeAdmin, {
    month: "2026-07",
    storeId: 99,
  });
  await getStats(prisma, storeAdmin, { storeId: 99 });

  assert.ok(
    listWheres.length > 0 && listWheres.every((where) => where.storeId === 3),
  );
  assert.ok(
    calendarWheres.length > 0 &&
      calendarWheres.every((where) => where.storeId === 3),
  );
  assert.ok(
    packageWheres.length > 0 &&
      packageWheres.every((where) => where.storeId === 3),
  );
  assert.ok(
    prescriptionWheres.length > 0 &&
      prescriptionWheres.every((where) => where.storeId === 3),
  );
  assert.ok(
    statsWheres.length > 0 && statsWheres.every((where) => where.storeId === 3),
  );
  const todayWaitingWhere = statsWheres.find(
    (where) =>
      where.status === 0 && where.scheduleType === 1 && where.processDate?.gte,
  );
  const overdueWhere = statsWheres.find(
    (where) =>
      where.status === 0 &&
      where.scheduleType === 1 &&
      where.processDate?.lt &&
      !where.processDate?.gte,
  );
  const urgentWhere = statsWheres.find((where) => where.priority?.gte === 1);
  const scheduledWaitingWheres = statsWheres.filter(
    (where) =>
      where.status === 0 && where.scheduleType === 1 && where.processDate?.gte,
  );
  assert.ok(todayWaitingWhere.processDate.lt instanceof Date);
  assert.ok(overdueWhere.processDate.lt instanceof Date);
  assert.deepEqual(urgentWhere.status, { in: [0, 1] });
  assert.equal(scheduledWaitingWheres.length, 3);
  assert.ok(
    scheduledWaitingWheres.some(
      (where) =>
        where.processDate.gte.getTime() ===
        todayWaitingWhere.processDate.lt.getTime(),
    ),
  );
});

test("today-all processing view combines today waiting, current processing and today finished", async () => {
  let listWhere;
  let countWhere;
  const prisma = {
    processingPlan: {
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

  await listProcessingPlans(prisma, storeAdmin, {
    view: "today-all",
    page: 1,
    pageSize: 20,
  });

  assert.equal(listWhere.storeId, storeAdmin.storeId);
  assert.equal(listWhere.scheduleType, undefined);
  assert.equal(listWhere.status, undefined);
  assert.equal(listWhere.AND.length, 1);
  assert.equal(listWhere.AND[0].OR.length, 3);
  assert.equal(listWhere.AND[0].OR[0].status, 0);
  assert.equal(listWhere.AND[0].OR[0].scheduleType, 1);
  assert.ok(listWhere.AND[0].OR[0].processDate.gte instanceof Date);
  assert.ok(listWhere.AND[0].OR[0].processDate.lt instanceof Date);
  assert.deepEqual(listWhere.AND[0].OR[1], { status: 1 });
  assert.ok(listWhere.AND[0].OR[2].finishDate.gte instanceof Date);
  assert.ok(listWhere.AND[0].OR[2].finishDate.lt instanceof Date);
  assert.deepEqual(countWhere, listWhere);
});

test("processing workbench focused views use mutually clear scheduling rules", async () => {
  const wheres = [];
  const prisma = {
    processingPlan: {
      findMany: async ({ where }) => {
        wheres.push(where);
        return [];
      },
      count: async () => 0,
    },
  };

  for (const view of [
    "today-waiting",
    "overdue",
    "processing",
    "tomorrow",
    "notice",
  ]) {
    await listProcessingPlans(prisma, storeAdmin, { view });
  }

  const [todayWaiting, overdue, processing, tomorrow, notice] = wheres;
  assert.equal(todayWaiting.status, 0);
  assert.equal(todayWaiting.scheduleType, 1);
  assert.ok(todayWaiting.processDate.gte instanceof Date);
  assert.ok(todayWaiting.processDate.lt instanceof Date);

  assert.equal(overdue.status, 0);
  assert.equal(overdue.scheduleType, 1);
  assert.ok(overdue.processDate.lt instanceof Date);
  assert.equal(overdue.processDate.gte, undefined);

  assert.equal(processing.status, 1);
  assert.equal(processing.processDate, undefined);

  assert.equal(tomorrow.status, 0);
  assert.equal(tomorrow.scheduleType, 1);
  assert.ok(tomorrow.processDate.gte instanceof Date);
  assert.ok(tomorrow.processDate.lt instanceof Date);

  assert.equal(notice.status, 0);
  assert.equal(notice.scheduleType, 2);
  assert.equal(notice.processDate, null);
});

test("processing and finished views put the latest updated plan first", async () => {
  const orders = [];
  const prisma = {
    processingPlan: {
      findMany: async ({ orderBy }) => {
        orders.push(orderBy);
        return [];
      },
      count: async () => 0,
    },
  };

  for (const view of ["processing", "today-finished"]) {
    await listProcessingPlans(prisma, storeAdmin, { view });
  }

  assert.equal(orders.length, 2);
  for (const orderBy of orders) {
    assert.deepEqual(orderBy, [{ updatedAt: "desc" }, { id: "desc" }]);
  }
});

test("all puts notice plans first while today-all starts with planned date", async () => {
  const orders = [];
  const prisma = {
    processingPlan: {
      findMany: async ({ orderBy }) => {
        orders.push(orderBy);
        return [];
      },
      count: async () => 0,
    },
  };

  for (const view of ["today-all", "all"]) {
    await listProcessingPlans(prisma, storeAdmin, { view });
  }

  const dateAndStatusOrder = [
    { processDate: "desc" },
    { status: "asc" },
    { updatedAt: "desc" },
    { id: "desc" },
  ];
  assert.equal(orders.length, 2);
  assert.deepEqual(orders[0], dateAndStatusOrder);
  assert.deepEqual(orders[1], [
    { scheduleType: "desc" },
    ...dateAndStatusOrder,
  ]);
});

test("restoring an empty queue does not open an empty Prisma transaction", async () => {
  let transactionCalls = 0;
  const prisma = {
    processingPlan: { findMany: async () => [] },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async () => {
      transactionCalls += 1;
    },
  };

  const result = await restoreProcessingQueue(prisma, storeAdmin, {
    processDate: "2026-07-15",
  });
  assert.deepEqual(result, { count: 0 });
  assert.equal(transactionCalls, 0);
});

test("queue reordering rejects plans from different stores", async () => {
  const prisma = {
    processingPlan: {
      findMany: async () => [
        {
          id: 1,
          storeId: 3,
          scheduleType: 1,
          processDate: new Date("2026-07-15T00:00:00"),
          status: 0,
        },
        {
          id: 2,
          storeId: 4,
          scheduleType: 1,
          processDate: new Date("2026-07-15T00:00:00"),
          status: 0,
        },
      ],
    },
  };

  await assert.rejects(
    () =>
      reorderProcessingQueue(
        prisma,
        { ...storeAdmin, role: 0 },
        { ids: [1, 2] },
      ),
    { statusCode: 400 },
  );
});
