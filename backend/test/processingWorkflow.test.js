import assert from "node:assert/strict";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import {
  assertProcessingWorkflowComplete,
  completeDispensing,
  finishEquipmentUsage,
  getProcessingPhoto,
} from "../src/services/processingWorkflowService.js";
import { config } from "../src/config.js";
import {
  EQUIPMENT_STATUS,
  EQUIPMENT_TYPE,
  PROCESSING_STAGE,
  PROCESSING_WORKFLOW_VERSION,
} from "../src/constants/processingWorkflow.js";
import {
  processingEquipmentQrContent,
  processingPlanQrContent,
  scanValue,
} from "../src/utils/processingCode.js";

const actor = { id: 12, role: 2, storeId: 3, phone: "13800000000" };

test("workflow QR contents identify plans and equipment without exposing database ids", () => {
  assert.equal(processingPlanQrContent("plan-token"), "TCM:PLAN:1:plan-token");
  assert.equal(
    processingEquipmentQrContent("equipment-token"),
    "TCM:EQUIPMENT:1:equipment-token",
  );
  assert.equal(scanValue("TCM:PLAN:1:plan-token", "PLAN"), "plan-token");
  assert.equal(scanValue("JG260805-ABC123", "PLAN"), "JG260805-ABC123");
});

test("new workflows require a dispensing photo before completion", async () => {
  await assert.rejects(
    () =>
      assertProcessingWorkflowComplete(
        {},
        {
          id: 21,
          workflowVersion: PROCESSING_WORKFLOW_VERSION,
          dispensingCompletedAt: null,
          processType: { code: "OTHER", name: "打粉" },
        },
      ),
    { statusCode: 409, message: "请先上传调配完成照片" },
  );
});

test("decoction workflows require released equipment and a completed decoction", async () => {
  const plan = {
    id: 21,
    workflowVersion: PROCESSING_WORKFLOW_VERSION,
    dispensingCompletedAt: new Date(),
    processType: { code: "DECOCTION", name: "代煎" },
  };
  const occupiedPrisma = {
    processingEquipmentUsage: {
      count: async ({ where }) => (where.endedAt === null ? 1 : 0),
    },
  };
  await assert.rejects(
    () => assertProcessingWorkflowComplete(occupiedPrisma, plan),
    { statusCode: 409, message: "还有浸泡桶或煎药锅未结束" },
  );

  const completePrisma = {
    processingEquipmentUsage: {
      count: async ({ where }) => (where.endedAt === null ? 0 : 1),
    },
  };
  await assert.doesNotReject(() => assertProcessingWorkflowComplete(completePrisma, plan));
});

test("decoction workflows require a packaging-machine scan for every completed portion", async () => {
  const plan = {
    id: 21,
    workflowVersion: PROCESSING_WORKFLOW_VERSION,
    dispensingCompletedAt: new Date(),
    processType: { code: "DECOCTION", name: "代煎" },
  };
  const prisma = {
    processingEquipmentUsage: {
      count: async ({ where }) => {
        if (where.endedAt === null) return 0;
        if (where.stage === PROCESSING_STAGE.DECOCTING) return 1;
        if (where.stage === PROCESSING_STAGE.PACKAGING) return 0;
        return 0;
      },
    },
  };
  await assert.rejects(
    () => assertProcessingWorkflowComplete(prisma, plan),
    { statusCode: 409, message: "每份煎煮完成后都需要扫描打包机" },
  );
});

test("scanning a packaging machine releases the decoction pot and records packaging", async () => {
  const state = {
    plan: {
      id: 21,
      storeId: 3,
      status: 1,
      workflowVersion: PROCESSING_WORKFLOW_VERSION,
      currentStage: PROCESSING_STAGE.DECOCTING,
      dispensingCompletedAt: new Date(),
      photos: [{ id: 1 }],
      processType: { code: "DECOCTION", name: "代煎" },
      prescription: { doctor: {}, source: {} },
      store: { id: 3, name: "测试门店" },
    },
    pot: {
      id: 31,
      storeId: 3,
      equipmentNo: "P01",
      name: "1号煎药锅",
      type: EQUIPMENT_TYPE.DECOCTION_POT,
      status: EQUIPMENT_STATUS.ENABLED,
      scanToken: "pot-token",
      currentUsageId: 41,
      deletedAt: null,
    },
    packer: {
      id: 32,
      storeId: 3,
      equipmentNo: "B01",
      name: "1号打包机",
      type: EQUIPMENT_TYPE.PACKAGING_MACHINE,
      status: EQUIPMENT_STATUS.ENABLED,
      scanToken: "packer-token",
      currentUsageId: null,
      deletedAt: null,
    },
    usages: [],
  };
  state.usages.push({
    id: 41,
    processingPlanId: 21,
    equipmentId: 31,
    equipment: state.pot,
    stage: PROCESSING_STAGE.DECOCTING,
    portionNo: 1,
    startedAt: new Date(Date.now() - 20 * 60000),
    endedAt: null,
  });
  let nextUsageId = 42;
  const prisma = {
    processingPlan: {
      findFirst: async () => ({ ...state.plan, equipmentUsages: state.usages }),
      update: async ({ data }) => {
        Object.assign(state.plan, data);
        return state.plan;
      },
    },
    processingEquipment: {
      findFirst: async ({ where }) => {
        const code = where.OR[0].scanToken || where.OR[1].equipmentNo;
        return [state.pot, state.packer].find(
          (item) => item.storeId === where.storeId &&
            (item.scanToken === code || item.equipmentNo === code),
        ) || null;
      },
      updateMany: async ({ where, data }) => {
        const item = [state.pot, state.packer].find((candidate) => candidate.id === where.id);
        if (!item) return { count: 0 };
        if (Object.hasOwn(where, "currentUsageId") && item.currentUsageId !== where.currentUsageId)
          return { count: 0 };
        Object.assign(item, data);
        return { count: 1 };
      },
    },
    processingEquipmentUsage: {
      findFirst: async ({ where }) => state.usages.find(
        (item) => item.id === where.id && item.processingPlanId === where.processingPlanId &&
          item.stage === where.stage && item.endedAt === where.endedAt,
      ) || null,
      create: async ({ data }) => {
        const equipment = [state.pot, state.packer].find((item) => item.id === data.equipmentId);
        const usage = { id: nextUsageId++, endedAt: null, ...data, equipment };
        state.usages.push(usage);
        return usage;
      },
      update: async ({ where, data }) => {
        const usage = state.usages.find((item) => item.id === where.id);
        Object.assign(usage, data);
        return usage;
      },
      count: async ({ where }) => state.usages.filter(
        (item) => item.processingPlanId === where.processingPlanId && item.endedAt === null,
      ).length,
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };

  const result = await finishEquipmentUsage(prisma, actor, 21, 41, {
    equipmentCode: "TCM:EQUIPMENT:1:packer-token",
  });

  assert.equal(state.pot.currentUsageId, null);
  assert.equal(state.packer.currentUsageId, null);
  assert.equal(state.usages[0].endReason, "煎煮完成");
  assert.equal(state.usages[1].stage, PROCESSING_STAGE.PACKAGING);
  assert.equal(state.usages[1].endReason, "扫码完成打包");
  assert.equal(state.plan.currentStage, PROCESSING_STAGE.PACKAGING_DONE);
  assert.equal(result.canCompleteWorkflow, true);
});

test("uploading a valid image stores it locally and completes dispensing", async (t) => {
  const previousUploadDir = config.uploadDir;
  const uploadDir = await mkdtemp(path.join(tmpdir(), "tcm-processing-photo-"));
  config.uploadDir = uploadDir;
  t.after(async () => {
    config.uploadDir = previousUploadDir;
    await rm(uploadDir, { recursive: true, force: true });
  });
  const state = {
    plan: {
      id: 21,
      storeId: 3,
      status: 1,
      workflowVersion: PROCESSING_WORKFLOW_VERSION,
      currentStage: PROCESSING_STAGE.DISPENSING,
      dispensingCompletedAt: null,
      dispensingCompletedBy: null,
      photos: [],
      equipmentUsages: [],
      processType: { code: "OTHER", name: "打粉" },
      prescription: { doctor: {}, source: {} },
      store: { id: 3, name: "测试门店" },
    },
    updated: null,
    photoData: null,
  };
  const prisma = {
    processingPlan: {
      findFirst: async () => ({ ...state.plan }),
      update: async ({ data }) => {
        state.updated = data;
        Object.assign(state.plan, data);
        return { ...state.plan };
      },
    },
    processingPhoto: {
      create: async ({ data }) => {
        state.photoData = data;
        return {
          id: 1,
          kind: data.kind,
          originalName: data.originalName,
          mimeType: data.mimeType,
          fileSize: data.fileSize,
          createdAt: new Date(),
          createdBy: data.createdBy,
        };
      },
      findFirst: async () => ({
        id: 1,
        ...state.photoData,
        createdAt: new Date(),
      }),
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };

  const photo = await completeDispensing(prisma, actor, 21, {
    filename: "调配.jpg",
    buffer: Buffer.from([0xff, 0xd8, 0xff, 0x00]),
  });

  assert.equal(photo.mimeType, "image/jpeg");
  assert.equal(state.updated.currentStage, PROCESSING_STAGE.DISPENSING_DONE);
  assert.equal(state.updated.dispensingCompletedBy, actor.id);
  assert.ok(state.updated.dispensingCompletedAt instanceof Date);
  assert.match(state.photoData.storagePath, /^processing-photos\/\d{4}\/\d{2}\//);
  assert.equal(state.photoData.data, null);
  const stored = await readFile(
    path.join(uploadDir, ...state.photoData.storagePath.split("/")),
  );
  assert.deepEqual(stored, Buffer.from([0xff, 0xd8, 0xff, 0x00]));
  const downloaded = await getProcessingPhoto(prisma, actor, 21, 1);
  assert.deepEqual(downloaded.data, stored);
});

test("a failed dispensing transaction removes the newly stored photo", async (t) => {
  const previousUploadDir = config.uploadDir;
  const uploadDir = await mkdtemp(path.join(tmpdir(), "tcm-processing-failed-"));
  config.uploadDir = uploadDir;
  t.after(async () => {
    config.uploadDir = previousUploadDir;
    await rm(uploadDir, { recursive: true, force: true });
  });
  const prisma = {
    processingPlan: {
      findFirst: async () => ({
        id: 21,
        storeId: 3,
        status: 1,
        workflowVersion: PROCESSING_WORKFLOW_VERSION,
        currentStage: PROCESSING_STAGE.DISPENSING,
        dispensingCompletedAt: null,
        photos: [],
      }),
    },
    $transaction: async () => {
      throw new Error("database write failed");
    },
  };

  await assert.rejects(
    completeDispensing(prisma, actor, 21, {
      filename: "调配.jpg",
      buffer: Buffer.from([0xff, 0xd8, 0xff, 0x00]),
    }),
    /database write failed/,
  );
  const entries = await readdir(uploadDir, { recursive: true });
  assert.equal(entries.some((entry) => entry.endsWith(".jpg")), false);
});

test("processing photos stored in the database remain readable during migration", async () => {
  const legacyData = Buffer.from([0xff, 0xd8, 0xff, 0x00]);
  const prisma = {
    processingPlan: { findFirst: async () => ({ id: 21 }) },
    processingPhoto: {
      findFirst: async () => ({
        id: 1,
        kind: "DISPENSING_COMPLETED",
        originalName: "调配.jpg",
        mimeType: "image/jpeg",
        fileSize: legacyData.length,
        createdAt: new Date(),
        createdBy: actor.id,
        storagePath: null,
        data: legacyData,
      }),
    },
  };

  const photo = await getProcessingPhoto(prisma, actor, 21, 1);
  assert.deepEqual(photo.data, legacyData);
  assert.equal(photo.storagePath, undefined);
});
