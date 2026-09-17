import assert from "node:assert/strict";
import test from "node:test";
import {
  createManualEquipmentUsage,
  transferFaultyEquipment,
  voidEquipmentUsage,
} from "../src/services/processingWorkflowService.js";
import {
  EQUIPMENT_STATUS,
  EQUIPMENT_TYPE,
  EQUIPMENT_USAGE_SOURCE,
  EQUIPMENT_USAGE_STATUS,
  PROCESSING_STAGE,
  WORKFLOW_EXCEPTION_TYPE,
} from "../src/constants/processingWorkflow.js";

const actor = { id: 12, role: 2, storeId: 3, phone: "13800000000" };

function workflowFixture(stage = PROCESSING_STAGE.SOAKING) {
  const oldEquipment = {
    id: 31,
    storeId: 3,
    equipmentNo: stage === PROCESSING_STAGE.SOAKING ? "T01" : "P01",
    name: stage === PROCESSING_STAGE.SOAKING ? "1号浸泡桶" : "1号煎药机",
    type:
      stage === PROCESSING_STAGE.SOAKING
        ? EQUIPMENT_TYPE.SOAK_BUCKET
        : EQUIPMENT_TYPE.DECOCTION_POT,
    status: EQUIPMENT_STATUS.ENABLED,
    scanToken: "old-token",
    currentUsageId: 41,
    deletedAt: null,
    remark: null,
  };
  const replacement = {
    ...oldEquipment,
    id: 32,
    equipmentNo: stage === PROCESSING_STAGE.SOAKING ? "T02" : "P02",
    name: stage === PROCESSING_STAGE.SOAKING ? "2号浸泡桶" : "2号煎药机",
    scanToken: "new-token",
    currentUsageId: null,
  };
  const state = {
    plan: {
      id: 21,
      storeId: 3,
      status: 1,
      currentStage: stage,
      dispensingCompletedAt: new Date(),
      photos: [{ id: 1 }],
      processType: { code: "DECOCTION", name: "代煎" },
      prescription: { doctor: {}, source: {} },
      store: { id: 3, name: "测试门店" },
    },
    equipment: [oldEquipment, replacement],
    usages: [
      {
        id: 41,
        processingPlanId: 21,
        equipmentId: oldEquipment.id,
        equipment: oldEquipment,
        stage,
        portionNo: 1,
        status: EQUIPMENT_USAGE_STATUS.ACTIVE,
        source: EQUIPMENT_USAGE_SOURCE.SCAN,
        requestId: "original-request",
        startedAt: new Date(Date.now() - 20 * 60 * 1000),
        endedAt: null,
        startedBy: actor.id,
      },
    ],
    exceptions: [],
  };
  let nextUsageId = 42;
  const prisma = {
    processingPlan: {
      findFirst: async () => ({
        ...state.plan,
        equipmentUsages: state.usages,
        workflowExceptions: state.exceptions,
      }),
      update: async ({ data }) => {
        Object.assign(state.plan, data);
        return state.plan;
      },
    },
    processingEquipment: {
      findFirst: async ({ where }) => {
        if (where.id) {
          return (
            state.equipment.find(
              (item) =>
                item.id === where.id &&
                item.storeId === where.storeId &&
                item.type === where.type &&
                item.status === where.status,
            ) || null
          );
        }
        const code = where.OR?.[0]?.scanToken || where.OR?.[1]?.equipmentNo;
        return (
          state.equipment.find(
            (item) =>
              item.storeId === where.storeId &&
              (item.scanToken === code || item.equipmentNo === code),
          ) || null
        );
      },
      updateMany: async ({ where, data }) => {
        const equipment = state.equipment.find((item) => item.id === where.id);
        if (!equipment) return { count: 0 };
        if (
          Object.hasOwn(where, "currentUsageId") &&
          equipment.currentUsageId !== where.currentUsageId
        ) {
          return { count: 0 };
        }
        if (where.status !== undefined && equipment.status !== where.status)
          return { count: 0 };
        Object.assign(equipment, data);
        return { count: 1 };
      },
    },
    processingEquipmentUsage: {
      findFirst: async ({ where }) => {
        if (where.requestId) {
          return (
            state.usages.find(
              (item) =>
                item.requestId === where.requestId &&
                (!where.processingPlanId ||
                  item.processingPlanId === where.processingPlanId),
            ) || null
          );
        }
        if (where.id) {
          return (
            state.usages.find(
              (item) =>
                item.id === where.id &&
                item.processingPlanId === where.processingPlanId &&
                item.status === where.status,
            ) || null
          );
        }
        return null;
      },
      findMany: async ({ where }) =>
        state.usages.filter(
          (item) =>
            item.processingPlanId === where.processingPlanId &&
            item.status === where.status,
        ),
      create: async ({ data }) => {
        const equipment = state.equipment.find(
          (item) => item.id === data.equipmentId,
        );
        const usage = { id: nextUsageId++, endedAt: null, ...data, equipment };
        state.usages.push(usage);
        return usage;
      },
      update: async ({ where, data }) => {
        const usage = state.usages.find((item) => item.id === where.id);
        Object.assign(usage, data);
        return usage;
      },
    },
    processingWorkflowException: {
      create: async ({ data }) => {
        const exception = {
          id: state.exceptions.length + 1,
          createdAt: new Date(),
          ...data,
        };
        state.exceptions.push(exception);
        return exception;
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };
  return { prisma, state, oldEquipment, replacement };
}

test("voiding a wrong soaking scan releases equipment and keeps a numeric void status", async () => {
  const { prisma, state, oldEquipment } = workflowFixture();

  await voidEquipmentUsage(prisma, actor, 21, 41, {
    reason: "扫描了错误的浸泡桶",
  });

  assert.equal(oldEquipment.currentUsageId, null);
  assert.equal(state.usages[0].status, EQUIPMENT_USAGE_STATUS.VOIDED);
  assert.equal(state.usages[0].voidReason, "扫描了错误的浸泡桶");
  assert.equal(state.exceptions[0].type, WORKFLOW_EXCEPTION_TYPE.WRONG_SCAN);
  assert.equal(state.plan.currentStage, PROCESSING_STAGE.DISPENSING_DONE);
});

test("fault transfer moves the active usage and puts the old equipment into maintenance", async () => {
  const { prisma, state, oldEquipment, replacement } = workflowFixture();

  const payload = {
    reason: "桶体漏液",
    equipmentCode: "TCM:EQUIPMENT:1:new-token",
    requestId: "fault-transfer-request",
  };
  await transferFaultyEquipment(prisma, actor, 21, 41, payload);
  await transferFaultyEquipment(prisma, actor, 21, 41, payload);

  assert.equal(oldEquipment.currentUsageId, null);
  assert.equal(oldEquipment.status, EQUIPMENT_STATUS.MAINTENANCE);
  assert.equal(state.usages[0].status, EQUIPMENT_USAGE_STATUS.COMPLETED);
  assert.equal(state.usages[1].status, EQUIPMENT_USAGE_STATUS.ACTIVE);
  assert.equal(state.usages[1].source, EQUIPMENT_USAGE_SOURCE.FAULT_TRANSFER);
  assert.equal(state.usages[1].transferredFromUsageId, 41);
  assert.equal(state.usages.length, 2);
  assert.equal(replacement.currentUsageId, state.usages[1].id);
  assert.equal(state.exceptions[0].type, WORKFLOW_EXCEPTION_TYPE.DEVICE_FAULT);
});

test("manual workflow entries are completed numeric records with an audit exception", async () => {
  const { prisma, state, replacement } = workflowFixture();
  state.usages = [];
  state.equipment[0].currentUsageId = null;
  const endedAt = new Date(Date.now() - 5 * 60 * 1000);
  const startedAt = new Date(endedAt.getTime() - 30 * 60 * 1000);

  await createManualEquipmentUsage(prisma, actor, 21, {
    stage: PROCESSING_STAGE.SOAKING,
    portionNo: 1,
    equipmentId: replacement.id,
    startedAt,
    endedAt,
    reason: "现场漏扫后补录",
    requestId: "manual-request",
  });

  assert.equal(state.usages[0].status, EQUIPMENT_USAGE_STATUS.COMPLETED);
  assert.equal(state.usages[0].source, EQUIPMENT_USAGE_SOURCE.MANUAL);
  assert.equal(state.exceptions[0].type, WORKFLOW_EXCEPTION_TYPE.MANUAL_ENTRY);
  assert.equal(state.plan.currentStage, PROCESSING_STAGE.SOAKING);
});
