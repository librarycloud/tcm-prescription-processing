import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import { PLAN_STATUS, PROCESS_TYPE_CODES } from "../constants/processing.js";
import {
  EQUIPMENT_STATUS,
  EQUIPMENT_TYPE,
  EQUIPMENT_TYPE_NAMES,
  PROCESSING_PHOTO_KIND,
  PROCESSING_PHOTO_MAX_COUNT,
  PROCESSING_PHOTO_MAX_SIZE,
  PROCESSING_STAGE,
  PROCESSING_WORKFLOW_VERSION,
} from "../constants/processingWorkflow.js";
import { businessScope } from "./permissionService.js";
import { recordOperation } from "./operationLogService.js";
import { processingPlanQrContent, scanValue } from "../utils/processingCode.js";
import {
  readUploadFile,
  removeUploadFile,
  saveUploadFile,
} from "./localUploadStorage.js";

const PHOTO_METADATA = {
  id: true,
  kind: true,
  originalName: true,
  mimeType: true,
  fileSize: true,
  createdAt: true,
  createdBy: true,
};

function planScope(actor, requestedStoreId) {
  return {
    ...businessScope(actor, isSuperAdmin(actor) ? requestedStoreId : undefined),
    deletedAt: null,
  };
}

function workflowInclude() {
  return {
    prescription: {
      include: { doctor: true, source: true },
    },
    processType: true,
    store: { select: { id: true, name: true, code: true } },
    photos: {
      where: { deletedAt: null },
      select: PHOTO_METADATA,
      orderBy: { createdAt: "asc" },
    },
    equipmentUsages: {
      include: {
        equipment: {
          select: { id: true, equipmentNo: true, name: true, type: true },
        },
      },
      orderBy: [{ startedAt: "asc" }, { id: "asc" }],
    },
  };
}

function isDecoction(plan) {
  return (
    plan.processType?.code === PROCESS_TYPE_CODES.DECOCTION ||
    plan.processType?.name === "代煎"
  );
}

async function getWorkflowPlan(prisma, actor, id) {
  const plan = await prisma.processingPlan.findFirst({
    where: { id: Number(id), ...planScope(actor) },
    include: workflowInclude(),
  });
  if (!plan) throw new AppError("加工计划不存在", 404);
  return plan;
}

function decoratePlan(plan) {
  const activeUsages = (plan.equipmentUsages || []).filter((item) => !item.endedAt);
  const completedDecoctions = (plan.equipmentUsages || []).filter(
    (item) => item.stage === PROCESSING_STAGE.DECOCTING && item.endedAt,
  );
  const completedPackagings = (plan.equipmentUsages || []).filter(
    (item) => item.stage === PROCESSING_STAGE.PACKAGING && item.endedAt,
  );
  const workflowEnabled = Number(plan.workflowVersion || 1) >= PROCESSING_WORKFLOW_VERSION;
  const canCompleteWorkflow = workflowEnabled
    ? Boolean(plan.dispensingCompletedAt) &&
      (!isDecoction(plan) ||
        (completedDecoctions.length > 0 &&
          completedPackagings.length === completedDecoctions.length &&
          activeUsages.length === 0))
    : true;
  return {
    ...plan,
    qrContent: processingPlanQrContent(plan.scanToken),
    workflowEnabled,
    isDecoction: isDecoction(plan),
    canCompleteWorkflow,
    activeUsages,
  };
}

export async function getProcessingWorkflow(prisma, actor, id) {
  return decoratePlan(await getWorkflowPlan(prisma, actor, id));
}

export async function findProcessingPlanByScan(prisma, actor, rawCode) {
  const code = scanValue(rawCode, "PLAN");
  if (!code) throw new AppError("加工计划码不能为空", 400);
  const plan = await prisma.processingPlan.findFirst({
    where: {
      ...planScope(actor),
      OR: [{ scanToken: code }, { planCode: code.toUpperCase() }],
    },
    include: workflowInclude(),
  });
  if (!plan) throw new AppError("未找到对应加工计划", 404);
  return decoratePlan(plan);
}

function detectImageMimeType(buffer) {
  const bytes = Buffer.from(buffer || []);
  if (bytes.subarray(0, 3).equals(Buffer.from([0xff, 0xd8, 0xff]))) return "image/jpeg";
  if (bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])))
    return "image/png";
  if (
    bytes.subarray(0, 4).toString("ascii") === "RIFF" &&
    bytes.subarray(8, 12).toString("ascii") === "WEBP"
  ) return "image/webp";
  return null;
}

function attachmentName(value) {
  const name = String(value || "调配完成照片")
    .replace(/[\\/\0]/g, "_")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .trim();
  return (name || "调配完成照片").slice(0, 255);
}

export async function completeDispensing(prisma, actor, id, file) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("请先开始加工", 409);
  if (Number(current.workflowVersion) < PROCESSING_WORKFLOW_VERSION)
    throw new AppError("该计划使用旧版加工流程，无需上传调配照片", 409);
  if (![PROCESSING_STAGE.DISPENSING, PROCESSING_STAGE.DISPENSING_DONE].includes(current.currentStage))
    throw new AppError("当前工序不能上传调配照片", 409);
  if (current.photos.length >= PROCESSING_PHOTO_MAX_COUNT)
    throw new AppError(`调配照片最多上传 ${PROCESSING_PHOTO_MAX_COUNT} 张`, 409);
  const buffer = Buffer.from(file?.buffer || []);
  if (!buffer.length) throw new AppError("请选择调配完成照片", 400);
  if (buffer.length > PROCESSING_PHOTO_MAX_SIZE)
    throw new AppError("照片不能超过 5MB", 400);
  const mimeType = detectImageMimeType(buffer);
  if (!mimeType) throw new AppError("仅支持 JPG、PNG 或 WEBP 图片", 400);

  const storagePath = await saveUploadFile(buffer, {
    category: "processing-photos",
    mimeType,
  });
  try {
    return await prisma.$transaction(async (tx) => {
      const photo = await tx.processingPhoto.create({
        data: {
          processingPlanId: current.id,
          kind: PROCESSING_PHOTO_KIND.DISPENSING_COMPLETED,
          originalName: attachmentName(file.filename),
          mimeType,
          fileSize: buffer.length,
          storagePath,
          data: null,
          createdBy: Number(actor.id),
        },
        select: PHOTO_METADATA,
      });
      const completedAt = current.dispensingCompletedAt || new Date();
      await tx.processingPlan.update({
        where: { id: current.id },
        data: {
          currentStage: PROCESSING_STAGE.DISPENSING_DONE,
          dispensingCompletedAt: completedAt,
          dispensingCompletedBy: current.dispensingCompletedBy || Number(actor.id),
          updatedBy: Number(actor.id),
        },
      });
      await recordOperation(tx, actor, {
        module: "processing",
        action: "dispensing_complete",
        targetId: current.id,
        storeId: current.storeId,
        description: current.dispensingCompletedAt ? "补充调配完成照片" : "上传照片并完成调配",
      });
      return photo;
    });
  } catch (error) {
    try {
      await removeUploadFile(storagePath);
    } catch {
      // Preserve the database error; an orphaned file can be removed separately.
    }
    throw error;
  }
}

export async function getProcessingPhoto(prisma, actor, planId, photoId) {
  await getWorkflowPlan(prisma, actor, planId);
  const photo = await prisma.processingPhoto.findFirst({
    where: {
      id: Number(photoId),
      processingPlanId: Number(planId),
      deletedAt: null,
    },
    select: { ...PHOTO_METADATA, storagePath: true, data: true },
  });
  if (!photo) throw new AppError("照片不存在", 404);
  const { storagePath, data, ...metadata } = photo;
  if (!storagePath) {
    if (!data) throw new AppError("照片文件不存在", 404);
    return { ...metadata, data };
  }
  try {
    return { ...metadata, data: await readUploadFile(storagePath) };
  } catch (error) {
    if (error?.code === "ENOENT") throw new AppError("照片文件不存在", 404);
    throw error;
  }
}

async function findScannedEquipment(tx, plan, rawCode, expectedType) {
  const code = scanValue(rawCode, "EQUIPMENT");
  if (!code) throw new AppError("请扫描设备码", 400);
  const equipment = await tx.processingEquipment.findFirst({
    where: {
      storeId: plan.storeId,
      deletedAt: null,
      OR: [{ scanToken: code }, { equipmentNo: code.toUpperCase() }],
    },
  });
  if (!equipment) throw new AppError("设备不存在或不属于当前门店", 404);
  if (equipment.type !== expectedType) {
    const expectedName = EQUIPMENT_TYPE_NAMES[expectedType] || "指定设备";
    throw new AppError(
      `请扫描${expectedName}设备码`,
      400,
    );
  }
  if (equipment.status !== EQUIPMENT_STATUS.ENABLED)
    throw new AppError("设备已停用或维修中", 409);
  return equipment;
}

export async function startEquipmentUsage(prisma, actor, id, payload = {}) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("当前计划不在加工中", 409);
  if (!isDecoction(current)) throw new AppError("只有代煎计划需要扫码使用设备", 409);
  if (Number(current.workflowVersion) < PROCESSING_WORKFLOW_VERSION)
    throw new AppError("该计划使用旧版加工流程", 409);
  const stage = String(payload.stage || "");
  if (![PROCESSING_STAGE.SOAKING, PROCESSING_STAGE.DECOCTING].includes(stage))
    throw new AppError("工序不正确", 400);
  const portionNo = Number(payload.portionNo);
  if (!Number.isInteger(portionNo) || portionNo <= 0 || portionNo > 99)
    throw new AppError("份组编号不正确", 400);

  await prisma.$transaction(async (tx) => {
    const plan = await tx.processingPlan.findFirst({
      where: { id: current.id, status: PLAN_STATUS.PROCESSING },
      include: { processType: true },
    });
    if (!plan) throw new AppError("加工计划状态已变化，请刷新", 409);
    const expectedType = stage === PROCESSING_STAGE.SOAKING
      ? EQUIPMENT_TYPE.SOAK_BUCKET
      : EQUIPMENT_TYPE.DECOCTION_POT;
    const equipment = await findScannedEquipment(tx, plan, payload.equipmentCode, expectedType);

    let sourceSoaking = null;
    if (stage === PROCESSING_STAGE.SOAKING) {
      if (![PROCESSING_STAGE.DISPENSING_DONE, PROCESSING_STAGE.SOAKING].includes(plan.currentStage))
        throw new AppError("请先上传照片完成调配", 409);
      const duplicate = await tx.processingEquipmentUsage.findFirst({
        where: { processingPlanId: plan.id, stage, portionNo },
        select: { id: true },
      });
      if (duplicate) throw new AppError(`第 ${portionNo} 份已记录浸泡`, 409);
    } else {
      if (![PROCESSING_STAGE.SOAKING, PROCESSING_STAGE.DECOCTING].includes(plan.currentStage))
        throw new AppError("请先扫码开始浸泡", 409);
      sourceSoaking = await tx.processingEquipmentUsage.findFirst({
        where: {
          processingPlanId: plan.id,
          stage: PROCESSING_STAGE.SOAKING,
          portionNo,
          endedAt: null,
        },
        include: { equipment: true },
      });
      if (!sourceSoaking) throw new AppError(`第 ${portionNo} 份没有进行中的浸泡记录`, 409);
      await tx.processingEquipment.updateMany({
        where: { id: sourceSoaking.equipmentId, currentUsageId: sourceSoaking.id },
        data: { currentUsageId: null, updatedBy: Number(actor.id) },
      });
      await tx.processingEquipmentUsage.update({
        where: { id: sourceSoaking.id },
        data: { endedAt: new Date(), endedBy: Number(actor.id), endReason: "转入煎煮" },
      });
    }

    const usage = await tx.processingEquipmentUsage.create({
      data: {
        processingPlanId: plan.id,
        equipmentId: equipment.id,
        stage,
        portionNo,
        startedBy: Number(actor.id),
      },
    });
    const occupied = await tx.processingEquipment.updateMany({
      where: {
        id: equipment.id,
        currentUsageId: null,
        status: EQUIPMENT_STATUS.ENABLED,
        deletedAt: null,
      },
      data: { currentUsageId: usage.id, updatedBy: Number(actor.id) },
    });
    if (occupied.count !== 1) throw new AppError("设备正在被其他加工计划使用", 409);
    await tx.processingPlan.update({
      where: { id: plan.id },
      data: { currentStage: stage, updatedBy: Number(actor.id) },
    });
    await recordOperation(tx, actor, {
      module: "processing",
      action: stage === PROCESSING_STAGE.SOAKING ? "soaking_start" : "decocting_start",
      targetId: plan.id,
      storeId: plan.storeId,
      description: `${stage === PROCESSING_STAGE.SOAKING ? "开始浸泡" : "开始煎煮"}：第 ${portionNo} 份，${equipment.equipmentNo} ${equipment.name}`,
    });
  });
  return getProcessingWorkflow(prisma, actor, current.id);
}

export async function finishEquipmentUsage(prisma, actor, id, usageId, payload = {}) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("当前计划不在加工中", 409);
  await prisma.$transaction(async (tx) => {
    const usage = await tx.processingEquipmentUsage.findFirst({
      where: {
        id: Number(usageId),
        processingPlanId: current.id,
        stage: PROCESSING_STAGE.DECOCTING,
        endedAt: null,
      },
      include: { equipment: true },
    });
    if (!usage) throw new AppError("煎煮记录不存在或已经完成", 409);
    const packagingMachine = await findScannedEquipment(
      tx,
      current,
      payload.equipmentCode,
      EQUIPMENT_TYPE.PACKAGING_MACHINE,
    );
    const now = new Date();
    await tx.processingEquipmentUsage.update({
      where: { id: usage.id },
      data: { endedAt: now, endedBy: Number(actor.id), endReason: "煎煮完成" },
    });
    await tx.processingEquipment.updateMany({
      where: { id: usage.equipmentId, currentUsageId: usage.id },
      data: { currentUsageId: null, updatedBy: Number(actor.id) },
    });

    const packagingUsage = await tx.processingEquipmentUsage.create({
      data: {
        processingPlanId: current.id,
        equipmentId: packagingMachine.id,
        stage: PROCESSING_STAGE.PACKAGING,
        portionNo: usage.portionNo,
        startedAt: now,
        startedBy: Number(actor.id),
      },
    });
    const occupied = await tx.processingEquipment.updateMany({
      where: {
        id: packagingMachine.id,
        currentUsageId: null,
        status: EQUIPMENT_STATUS.ENABLED,
        deletedAt: null,
      },
      data: { currentUsageId: packagingUsage.id, updatedBy: Number(actor.id) },
    });
    if (occupied.count !== 1) throw new AppError("打包机正在被其他加工计划使用", 409);
    await tx.processingEquipmentUsage.update({
      where: { id: packagingUsage.id },
      data: { endedAt: now, endedBy: Number(actor.id), endReason: "扫码完成打包" },
    });
    await tx.processingEquipment.updateMany({
      where: { id: packagingMachine.id, currentUsageId: packagingUsage.id },
      data: { currentUsageId: null, updatedBy: Number(actor.id) },
    });
    const activeCount = await tx.processingEquipmentUsage.count({
      where: { processingPlanId: current.id, endedAt: null },
    });
    await tx.processingPlan.update({
      where: { id: current.id },
      data: {
        currentStage: activeCount > 0
          ? PROCESSING_STAGE.DECOCTING
          : PROCESSING_STAGE.PACKAGING_DONE,
        updatedBy: Number(actor.id),
      },
    });
    await recordOperation(tx, actor, {
      module: "processing",
      action: "packaging_complete",
      targetId: current.id,
      storeId: current.storeId,
      description: `第 ${usage.portionNo} 份完成煎煮并扫码打包：${usage.equipment.equipmentNo} ${usage.equipment.name} → ${packagingMachine.equipmentNo} ${packagingMachine.name}`,
    });
  });
  return getProcessingWorkflow(prisma, actor, current.id);
}

export async function assertProcessingWorkflowComplete(prisma, plan) {
  if (Number(plan.workflowVersion || 1) < PROCESSING_WORKFLOW_VERSION) return;
  if (!plan.dispensingCompletedAt)
    throw new AppError("请先上传调配完成照片", 409);
  if (!isDecoction(plan)) return;
  const [activeCount, completedDecoctionCount, completedPackagingCount] = await Promise.all([
    prisma.processingEquipmentUsage.count({
      where: { processingPlanId: plan.id, endedAt: null },
    }),
    prisma.processingEquipmentUsage.count({
      where: {
        processingPlanId: plan.id,
        stage: PROCESSING_STAGE.DECOCTING,
        endedAt: { not: null },
      },
    }),
    prisma.processingEquipmentUsage.count({
      where: {
        processingPlanId: plan.id,
        stage: PROCESSING_STAGE.PACKAGING,
        endedAt: { not: null },
      },
    }),
  ]);
  if (activeCount > 0) throw new AppError("还有浸泡桶或煎药锅未结束", 409);
  if (completedDecoctionCount < 1) throw new AppError("请先完成浸泡和煎煮记录", 409);
  if (completedPackagingCount !== completedDecoctionCount)
    throw new AppError("每份煎煮完成后都需要扫描打包机", 409);
}
