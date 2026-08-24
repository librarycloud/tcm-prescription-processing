import { AppError } from "../utils/appError.js";
import { randomUUID } from "node:crypto";
import { isSuperAdmin } from "../constants/roles.js";
import { PLAN_STATUS, PROCESS_TYPE_CODES } from "../constants/processing.js";
import {
  EQUIPMENT_STATUS,
  EQUIPMENT_TYPE,
  EQUIPMENT_TYPE_NAMES,
  EQUIPMENT_USAGE_SOURCE,
  EQUIPMENT_USAGE_STATUS,
  PROCESSING_PHOTO_KIND,
  PROCESSING_PHOTO_MAX_COUNT,
  PROCESSING_PHOTO_MAX_SIZE,
  PROCESSING_STAGE,
  WORKFLOW_EXCEPTION_STATUS,
  WORKFLOW_EXCEPTION_TYPE,
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
    workflowExceptions: {
      include: {
        creator: {
          select: { id: true, nickname: true, name: true, phone: true },
        },
      },
      orderBy: [{ createdAt: "desc" }, { id: "desc" }],
    },
  };
}

function isDecoction(plan) {
  return (
    plan.processType?.code === PROCESS_TYPE_CODES.DECOCTION ||
    plan.processType?.name === "代煎"
  );
}

function workflowCompletionState(
  plan,
  equipmentUsages,
  { allowActivePackaging = false } = {},
) {
  const blockers = [];
  if (!plan.dispensingCompletedAt) blockers.push("请先上传调配完成照片");
  if (!isDecoction(plan))
    return { canComplete: blockers.length === 0, blockers };

  const valid = equipmentUsages.filter(
    (item) => item.status !== EQUIPMENT_USAGE_STATUS.VOIDED,
  );
  if (
    valid.some(
      (item) =>
        item.status === EQUIPMENT_USAGE_STATUS.ACTIVE &&
        item.stage !== PROCESSING_STAGE.PACKAGING,
    )
  ) {
    blockers.push("还有浸泡或煎煮工序未结束");
  }
  if (
    !allowActivePackaging &&
    valid.some(
      (item) =>
        item.status === EQUIPMENT_USAGE_STATUS.ACTIVE &&
        item.stage === PROCESSING_STAGE.PACKAGING,
    )
  ) {
    blockers.push("还有打包工序未结束");
  }
  const soakingGroups = new Set(
    valid
      .filter(
        (item) =>
          item.stage === PROCESSING_STAGE.SOAKING &&
          item.status === EQUIPMENT_USAGE_STATUS.COMPLETED,
      )
      .map((item) => Number(item.portionNo)),
  );
  const decoctionGroups = new Set(
    valid
      .filter(
        (item) =>
          item.stage === PROCESSING_STAGE.DECOCTING &&
          item.status === EQUIPMENT_USAGE_STATUS.COMPLETED,
      )
      .map((item) => Number(item.portionNo)),
  );
  const packagingGroups = new Set(
    valid
      .filter(
        (item) =>
          item.stage === PROCESSING_STAGE.PACKAGING &&
          (item.status === EQUIPMENT_USAGE_STATUS.COMPLETED ||
            (allowActivePackaging &&
              item.status === EQUIPMENT_USAGE_STATUS.ACTIVE)),
      )
      .map((item) => Number(item.portionNo)),
  );
  if (!soakingGroups.size) blockers.push("请先完成浸泡和煎煮记录");
  for (const groupNo of [...soakingGroups].sort((a, b) => a - b)) {
    if (!decoctionGroups.has(groupNo))
      blockers.push(`第 ${groupNo} 组尚未完成煎煮`);
    else if (!packagingGroups.has(groupNo))
      blockers.push(`第 ${groupNo} 组尚未开始打包`);
  }
  return { canComplete: blockers.length === 0, blockers };
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
  const equipmentUsages = (plan.equipmentUsages || []).map((item) => ({
    ...item,
    status: Number(
      item.status ??
        (item.voidedAt
          ? EQUIPMENT_USAGE_STATUS.VOIDED
          : item.endedAt
            ? EQUIPMENT_USAGE_STATUS.COMPLETED
            : EQUIPMENT_USAGE_STATUS.ACTIVE),
    ),
    source: Number(item.source ?? EQUIPMENT_USAGE_SOURCE.SCAN),
  }));
  const activeUsages = equipmentUsages.filter(
    (item) => item.status === EQUIPMENT_USAGE_STATUS.ACTIVE,
  );
  const completion = workflowCompletionState(plan, equipmentUsages);
  const finalization = workflowCompletionState(plan, equipmentUsages, {
    allowActivePackaging: true,
  });
  return {
    ...plan,
    qrContent: processingPlanQrContent(plan.scanToken),
    isDecoction: isDecoction(plan),
    canCompleteWorkflow: completion.canComplete,
    canFinalizeWorkflow: finalization.canComplete,
    completionBlockers: completion.blockers,
    activeUsages,
    equipmentUsages,
  };
}

async function decoratePlanWithOperators(prisma, plan) {
  const operatorIds = [
    ...new Set(
      (plan.equipmentUsages || [])
        .map((item) => item.startedBy)
        .filter(Boolean),
    ),
  ];
  if (!operatorIds.length || !prisma.admin?.findMany) return decoratePlan(plan);

  const operators = await prisma.admin.findMany({
    where: { id: { in: operatorIds } },
    select: { id: true, nickname: true, name: true, phone: true },
  });
  const operatorMap = new Map(operators.map((item) => [item.id, item]));
  return decoratePlan({
    ...plan,
    equipmentUsages: (plan.equipmentUsages || []).map((item) => ({
      ...item,
      operator: operatorMap.get(item.startedBy) || null,
    })),
  });
}

function requiredReason(value, label = "异常原因") {
  const reason = String(value || "").trim();
  if (!reason) throw new AppError(`请填写${label}`, 400);
  if (reason.length > 255)
    throw new AppError(`${label}不能超过 255 个字符`, 400);
  return reason;
}

function requestId(value) {
  const id = String(value || randomUUID()).trim();
  if (!id || id.length > 64) throw new AppError("请求编号不正确", 400);
  return id;
}

function expectedEquipmentType(stage) {
  if (stage === PROCESSING_STAGE.SOAKING) return EQUIPMENT_TYPE.SOAK_BUCKET;
  if (stage === PROCESSING_STAGE.DECOCTING) return EQUIPMENT_TYPE.DECOCTION_POT;
  if (stage === PROCESSING_STAGE.PACKAGING)
    return EQUIPMENT_TYPE.PACKAGING_MACHINE;
  throw new AppError("工序不正确", 400);
}

async function recordWorkflowException(tx, actor, plan, data) {
  return tx.processingWorkflowException.create({
    data: {
      processingPlanId: plan.id,
      usageId: data.usageId || null,
      relatedUsageId: data.relatedUsageId || null,
      type: data.type,
      status: WORKFLOW_EXCEPTION_STATUS.RESOLVED,
      reason: data.reason,
      details: data.details || undefined,
      createdBy: Number(actor.id),
    },
  });
}

export async function getProcessingWorkflow(prisma, actor, id) {
  return decoratePlanWithOperators(
    prisma,
    await getWorkflowPlan(prisma, actor, id),
  );
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
  return decoratePlanWithOperators(prisma, plan);
}

function detectImageMimeType(buffer) {
  const bytes = Buffer.from(buffer || []);
  if (bytes.subarray(0, 3).equals(Buffer.from([0xff, 0xd8, 0xff])))
    return "image/jpeg";
  if (
    bytes
      .subarray(0, 8)
      .equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  )
    return "image/png";
  if (
    bytes.subarray(0, 4).toString("ascii") === "RIFF" &&
    bytes.subarray(8, 12).toString("ascii") === "WEBP"
  )
    return "image/webp";
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
  if (
    ![PROCESSING_STAGE.DISPENSING, PROCESSING_STAGE.DISPENSING_DONE].includes(
      current.currentStage,
    )
  )
    throw new AppError("当前工序不能上传调配照片", 409);
  if (current.photos.length >= PROCESSING_PHOTO_MAX_COUNT)
    throw new AppError(
      `调配照片最多上传 ${PROCESSING_PHOTO_MAX_COUNT} 张`,
      409,
    );
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
          dispensingCompletedBy:
            current.dispensingCompletedBy || Number(actor.id),
          updatedBy: Number(actor.id),
        },
      });
      await recordOperation(tx, actor, {
        module: "processing",
        action: "dispensing_complete",
        targetId: current.id,
        storeId: current.storeId,
        description: current.dispensingCompletedAt
          ? "补充调配完成照片"
          : "上传照片并完成调配",
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

export async function deleteProcessingPhoto(prisma, actor, planId, photoId) {
  const current = await getWorkflowPlan(prisma, actor, planId);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("只有加工中的计划可以删除照片", 409);
  if (
    ![PROCESSING_STAGE.DISPENSING, PROCESSING_STAGE.DISPENSING_DONE].includes(
      current.currentStage,
    )
  )
    throw new AppError("后续工序已开始，不能删除调配照片", 409);

  const photo = await prisma.processingPhoto.findFirst({
    where: {
      id: Number(photoId),
      processingPlanId: current.id,
      deletedAt: null,
    },
    select: { id: true, storagePath: true },
  });
  if (!photo) throw new AppError("照片不存在", 404);

  await prisma.$transaction(async (tx) => {
    const deleted = await tx.processingPhoto.updateMany({
      where: { id: photo.id, processingPlanId: current.id, deletedAt: null },
      data: { deletedAt: new Date() },
    });
    if (deleted.count !== 1)
      throw new AppError("照片状态已变化，请刷新后重试", 409);

    const remainingCount = await tx.processingPhoto.count({
      where: { processingPlanId: current.id, deletedAt: null },
    });
    if (
      remainingCount === 0 &&
      current.currentStage === PROCESSING_STAGE.DISPENSING_DONE
    ) {
      await tx.processingPlan.update({
        where: { id: current.id },
        data: {
          currentStage: PROCESSING_STAGE.DISPENSING,
          dispensingCompletedAt: null,
          dispensingCompletedBy: null,
          updatedBy: Number(actor.id),
        },
      });
    }
    await recordOperation(tx, actor, {
      module: "processing",
      action: "dispensing_photo_delete",
      targetId: current.id,
      storeId: current.storeId,
      description: `删除调配照片：${photo.id}`,
    });
  });

  try {
    await removeUploadFile(photo.storagePath);
  } catch {
    // The database deletion is authoritative; orphan cleanup can run separately.
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
    throw new AppError(`请扫描${expectedName}设备码`, 400);
  }
  if (equipment.status !== EQUIPMENT_STATUS.ENABLED)
    throw new AppError("设备已停用或维修中", 409);
  return equipment;
}

export async function startEquipmentUsage(prisma, actor, id, payload = {}) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("当前计划不在加工中", 409);
  if (!isDecoction(current))
    throw new AppError("只有代煎计划需要扫码使用设备", 409);
  const stage = Number(payload.stage);
  if (![PROCESSING_STAGE.SOAKING, PROCESSING_STAGE.DECOCTING].includes(stage))
    throw new AppError("工序不正确", 400);
  const portionNo = Number(payload.portionNo);
  if (!Number.isInteger(portionNo) || portionNo <= 0 || portionNo > 99)
    throw new AppError("分组编号不正确", 400);
  const operationRequestId = requestId(payload.requestId);
  const repeated = await prisma.processingEquipmentUsage.findFirst({
    where: { requestId: operationRequestId },
    select: { processingPlanId: true },
  });
  if (repeated) {
    if (repeated.processingPlanId !== current.id)
      throw new AppError("请求编号已被使用", 409);
    return getProcessingWorkflow(prisma, actor, current.id);
  }

  try {
    await prisma.$transaction(async (tx) => {
      const plan = await tx.processingPlan.findFirst({
        where: { id: current.id, status: PLAN_STATUS.PROCESSING },
        include: { processType: true },
      });
      if (!plan) throw new AppError("加工计划状态已变化，请刷新", 409);
      const expectedType =
        stage === PROCESSING_STAGE.SOAKING
          ? EQUIPMENT_TYPE.SOAK_BUCKET
          : EQUIPMENT_TYPE.DECOCTION_POT;
      const equipment = await findScannedEquipment(
        tx,
        plan,
        payload.equipmentCode,
        expectedType,
      );

      let sourceSoaking = null;
      if (stage === PROCESSING_STAGE.SOAKING) {
        if (
          ![
            PROCESSING_STAGE.DISPENSING_DONE,
            PROCESSING_STAGE.SOAKING,
          ].includes(plan.currentStage)
        )
          throw new AppError("请先上传照片完成调配", 409);
        const duplicate = await tx.processingEquipmentUsage.findFirst({
          where: {
            processingPlanId: plan.id,
            stage,
            portionNo,
            status: { not: EQUIPMENT_USAGE_STATUS.VOIDED },
          },
          select: { id: true },
        });
        if (duplicate) throw new AppError(`第 ${portionNo} 组已记录浸泡`, 409);
      } else {
        if (
          ![PROCESSING_STAGE.SOAKING, PROCESSING_STAGE.DECOCTING].includes(
            plan.currentStage,
          )
        )
          throw new AppError("请先扫码开始浸泡", 409);
        sourceSoaking = await tx.processingEquipmentUsage.findFirst({
          where: {
            processingPlanId: plan.id,
            stage: PROCESSING_STAGE.SOAKING,
            portionNo,
            status: EQUIPMENT_USAGE_STATUS.ACTIVE,
          },
          include: { equipment: true },
        });
        if (!sourceSoaking)
          throw new AppError(`第 ${portionNo} 组没有进行中的浸泡记录`, 409);
        await tx.processingEquipment.updateMany({
          where: {
            id: sourceSoaking.equipmentId,
            currentUsageId: sourceSoaking.id,
          },
          data: { currentUsageId: null, updatedBy: Number(actor.id) },
        });
        await tx.processingEquipmentUsage.update({
          where: { id: sourceSoaking.id },
          data: {
            status: EQUIPMENT_USAGE_STATUS.COMPLETED,
            endedAt: new Date(),
            endedBy: Number(actor.id),
            endReason: "转入煎煮",
          },
        });
      }

      const usage = await tx.processingEquipmentUsage.create({
        data: {
          processingPlanId: plan.id,
          equipmentId: equipment.id,
          stage,
          portionNo,
          status: EQUIPMENT_USAGE_STATUS.ACTIVE,
          source: EQUIPMENT_USAGE_SOURCE.SCAN,
          requestId: operationRequestId,
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
      if (occupied.count !== 1)
        throw new AppError("设备正在被其他加工计划使用", 409);
      await tx.processingPlan.update({
        where: { id: plan.id },
        data: { currentStage: stage, updatedBy: Number(actor.id) },
      });
      await recordOperation(tx, actor, {
        module: "processing",
        action:
          stage === PROCESSING_STAGE.SOAKING
            ? "soaking_start"
            : "decocting_start",
        targetId: plan.id,
        storeId: plan.storeId,
        description: `${stage === PROCESSING_STAGE.SOAKING ? "开始浸泡" : "开始煎煮"}：第 ${portionNo} 组，${equipment.equipmentNo} ${equipment.name}`,
      });
    });
  } catch (error) {
    if (error?.code !== "P2002") throw error;
    const duplicateRequest = await prisma.processingEquipmentUsage.findFirst({
      where: { requestId: operationRequestId, processingPlanId: current.id },
      select: { id: true },
    });
    if (!duplicateRequest) throw error;
  }
  return getProcessingWorkflow(prisma, actor, current.id);
}

export async function startPackagingUsage(
  prisma,
  actor,
  id,
  usageId,
  payload = {},
) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("当前计划不在加工中", 409);
  const operationRequestId = requestId(payload.requestId);
  const repeated = await prisma.processingEquipmentUsage.findFirst({
    where: { requestId: operationRequestId },
    select: { processingPlanId: true },
  });
  if (repeated) {
    if (repeated.processingPlanId !== current.id)
      throw new AppError("请求编号已被使用", 409);
    return getProcessingWorkflow(prisma, actor, current.id);
  }
  try {
    await prisma.$transaction(async (tx) => {
      const usage = await tx.processingEquipmentUsage.findFirst({
        where: {
          id: Number(usageId),
          processingPlanId: current.id,
          stage: PROCESSING_STAGE.DECOCTING,
          status: EQUIPMENT_USAGE_STATUS.ACTIVE,
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
        data: {
          status: EQUIPMENT_USAGE_STATUS.COMPLETED,
          endedAt: now,
          endedBy: Number(actor.id),
          endReason: "煎煮完成",
        },
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
          status: EQUIPMENT_USAGE_STATUS.ACTIVE,
          source: EQUIPMENT_USAGE_SOURCE.SCAN,
          requestId: operationRequestId,
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
        data: {
          currentUsageId: packagingUsage.id,
          updatedBy: Number(actor.id),
        },
      });
      if (occupied.count !== 1)
        throw new AppError("包装机正在被其他加工计划使用", 409);
      await refreshCurrentStage(
        tx,
        current.id,
        PROCESSING_STAGE.PACKAGING,
      );
      await recordOperation(tx, actor, {
        module: "processing",
        action: "packaging_start",
        targetId: current.id,
        storeId: current.storeId,
        description: `第 ${usage.portionNo} 组完成煎煮并开始打包：${usage.equipment.equipmentNo} ${usage.equipment.name} → ${packagingMachine.equipmentNo} ${packagingMachine.name}`,
      });
    });
  } catch (error) {
    if (error?.code !== "P2002") throw error;
    const duplicateRequest = await prisma.processingEquipmentUsage.findFirst({
      where: { requestId: operationRequestId, processingPlanId: current.id },
      select: { id: true },
    });
    if (!duplicateRequest) throw error;
  }
  return getProcessingWorkflow(prisma, actor, current.id);
}

export async function finishEquipmentUsage(prisma, actor, id, usageId) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("当前计划不在加工中", 409);
  const existing = (current.equipmentUsages || []).find(
    (item) => Number(item.id) === Number(usageId),
  );
  if (
    existing?.stage === PROCESSING_STAGE.PACKAGING &&
    existing?.status === EQUIPMENT_USAGE_STATUS.COMPLETED
  ) {
    return getProcessingWorkflow(prisma, actor, current.id);
  }

  await prisma.$transaction(async (tx) => {
    const usage = await tx.processingEquipmentUsage.findFirst({
      where: {
        id: Number(usageId),
        processingPlanId: current.id,
        stage: PROCESSING_STAGE.PACKAGING,
        status: EQUIPMENT_USAGE_STATUS.ACTIVE,
      },
      include: { equipment: true },
    });
    if (!usage) throw new AppError("打包记录不存在或已经完成", 409);

    const now = new Date();
    await tx.processingEquipmentUsage.update({
      where: { id: usage.id },
      data: {
        status: EQUIPMENT_USAGE_STATUS.COMPLETED,
        endedAt: now,
        endedBy: Number(actor.id),
        endReason: "加工完成",
      },
    });
    await tx.processingEquipment.updateMany({
      where: { id: usage.equipmentId, currentUsageId: usage.id },
      data: { currentUsageId: null, updatedBy: Number(actor.id) },
    });
    await refreshCurrentStage(
      tx,
      current.id,
      PROCESSING_STAGE.PACKAGING_DONE,
    );
    await recordOperation(tx, actor, {
      module: "processing",
      action: "packaging_complete",
      targetId: current.id,
      storeId: current.storeId,
      description: `第 ${usage.portionNo} 组完成打包：${usage.equipment.equipmentNo} ${usage.equipment.name}`,
    });
  });
  return getProcessingWorkflow(prisma, actor, current.id);
}

async function refreshCurrentStage(
  tx,
  planId,
  fallback = PROCESSING_STAGE.DISPENSING_DONE,
) {
  const active = await tx.processingEquipmentUsage.findMany({
    where: {
      processingPlanId: planId,
      status: EQUIPMENT_USAGE_STATUS.ACTIVE,
    },
    select: { stage: true },
  });
  const currentStage = active.some(
    (item) => item.stage === PROCESSING_STAGE.DECOCTING,
  )
    ? PROCESSING_STAGE.DECOCTING
    : active.some((item) => item.stage === PROCESSING_STAGE.PACKAGING)
      ? PROCESSING_STAGE.PACKAGING
      : active.some((item) => item.stage === PROCESSING_STAGE.SOAKING)
        ? PROCESSING_STAGE.SOAKING
        : fallback;
  await tx.processingPlan.update({
    where: { id: planId },
    data: { currentStage },
  });
  return currentStage;
}

export async function voidEquipmentUsage(
  prisma,
  actor,
  id,
  usageId,
  payload = {},
) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("当前计划不在加工中", 409);
  const reason = requiredReason(payload.reason, "撤销原因");

  await prisma.$transaction(async (tx) => {
    const usage = await tx.processingEquipmentUsage.findFirst({
      where: {
        id: Number(usageId),
        processingPlanId: current.id,
        status: EQUIPMENT_USAGE_STATUS.ACTIVE,
        stage: { in: [PROCESSING_STAGE.SOAKING, PROCESSING_STAGE.DECOCTING] },
      },
      include: { equipment: true },
    });
    if (!usage) throw new AppError("该设备记录不能撤销或已经处理", 409);

    let resumedSoaking = null;
    if (usage.stage === PROCESSING_STAGE.DECOCTING) {
      resumedSoaking = await tx.processingEquipmentUsage.findFirst({
        where: {
          processingPlanId: current.id,
          portionNo: usage.portionNo,
          stage: PROCESSING_STAGE.SOAKING,
          status: EQUIPMENT_USAGE_STATUS.COMPLETED,
        },
        orderBy: [{ endedAt: "desc" }, { id: "desc" }],
        include: { equipment: true },
      });
      if (!resumedSoaking)
        throw new AppError("原浸泡记录不存在，不能撤销本次转锅", 409);
      const resumed = await tx.processingEquipment.updateMany({
        where: {
          id: resumedSoaking.equipmentId,
          currentUsageId: null,
          status: EQUIPMENT_STATUS.ENABLED,
          deletedAt: null,
        },
        data: {
          currentUsageId: resumedSoaking.id,
          updatedBy: Number(actor.id),
        },
      });
      if (resumed.count !== 1)
        throw new AppError("原浸泡桶已被占用，不能撤销本次转锅", 409);
      await tx.processingEquipmentUsage.update({
        where: { id: resumedSoaking.id },
        data: {
          status: EQUIPMENT_USAGE_STATUS.ACTIVE,
          endedAt: null,
          endedBy: null,
          endReason: null,
        },
      });
    }

    const now = new Date();
    await tx.processingEquipment.updateMany({
      where: { id: usage.equipmentId, currentUsageId: usage.id },
      data: { currentUsageId: null, updatedBy: Number(actor.id) },
    });
    await tx.processingEquipmentUsage.update({
      where: { id: usage.id },
      data: {
        status: EQUIPMENT_USAGE_STATUS.VOIDED,
        endedAt: now,
        endedBy: Number(actor.id),
        endReason: "误扫撤销",
        voidedAt: now,
        voidedBy: Number(actor.id),
        voidReason: reason,
      },
    });
    await refreshCurrentStage(tx, current.id);
    await recordWorkflowException(tx, actor, current, {
      usageId: usage.id,
      relatedUsageId: resumedSoaking?.id,
      type: WORKFLOW_EXCEPTION_TYPE.WRONG_SCAN,
      reason,
      details: {
        equipmentId: usage.equipmentId,
        equipmentNo: usage.equipment.equipmentNo,
        stage: usage.stage,
        portionNo: usage.portionNo,
      },
    });
    await recordOperation(tx, actor, {
      module: "processing",
      action: "equipment_usage_void",
      targetId: current.id,
      storeId: current.storeId,
      description: `撤销第 ${usage.portionNo} 组${usage.stage === PROCESSING_STAGE.SOAKING ? "浸泡" : "煎煮"}误扫：${usage.equipment.equipmentNo}，原因：${reason}`,
    });
  });
  return getProcessingWorkflow(prisma, actor, current.id);
}

export async function transferFaultyEquipment(
  prisma,
  actor,
  id,
  usageId,
  payload = {},
) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("当前计划不在加工中", 409);
  const reason = requiredReason(payload.reason, "故障原因");
  const operationRequestId = requestId(payload.requestId);
  const repeated = await prisma.processingEquipmentUsage.findFirst({
    where: { requestId: operationRequestId },
    select: { processingPlanId: true },
  });
  if (repeated) {
    if (repeated.processingPlanId !== current.id)
      throw new AppError("请求编号已被使用", 409);
    return getProcessingWorkflow(prisma, actor, current.id);
  }

  try {
    await prisma.$transaction(async (tx) => {
      const usage = await tx.processingEquipmentUsage.findFirst({
        where: {
          id: Number(usageId),
          processingPlanId: current.id,
          status: EQUIPMENT_USAGE_STATUS.ACTIVE,
          stage: { in: [PROCESSING_STAGE.SOAKING, PROCESSING_STAGE.DECOCTING] },
        },
        include: { equipment: true },
      });
      if (!usage) throw new AppError("该设备记录已经结束，不能换机", 409);
      const replacement = await findScannedEquipment(
        tx,
        current,
        payload.equipmentCode,
        expectedEquipmentType(usage.stage),
      );
      if (replacement.id === usage.equipmentId)
        throw new AppError("请扫描另一台可用设备", 400);

      const nextUsage = await tx.processingEquipmentUsage.create({
        data: {
          processingPlanId: current.id,
          equipmentId: replacement.id,
          stage: usage.stage,
          portionNo: usage.portionNo,
          status: EQUIPMENT_USAGE_STATUS.ACTIVE,
          source: EQUIPMENT_USAGE_SOURCE.FAULT_TRANSFER,
          requestId: operationRequestId,
          transferredFromUsageId: usage.id,
          startedBy: Number(actor.id),
        },
      });
      const occupied = await tx.processingEquipment.updateMany({
        where: {
          id: replacement.id,
          currentUsageId: null,
          status: EQUIPMENT_STATUS.ENABLED,
          deletedAt: null,
        },
        data: { currentUsageId: nextUsage.id, updatedBy: Number(actor.id) },
      });
      if (occupied.count !== 1)
        throw new AppError("替换设备正在被其他加工计划使用", 409);

      const now = new Date();
      await tx.processingEquipmentUsage.update({
        where: { id: usage.id },
        data: {
          status: EQUIPMENT_USAGE_STATUS.COMPLETED,
          endedAt: now,
          endedBy: Number(actor.id),
          endReason: `设备故障：${reason}`,
        },
      });
      await tx.processingEquipment.updateMany({
        where: { id: usage.equipmentId, currentUsageId: usage.id },
        data: {
          currentUsageId: null,
          status: EQUIPMENT_STATUS.MAINTENANCE,
          remark: [usage.equipment.remark, `加工中报修：${reason}`]
            .filter(Boolean)
            .join("；")
            .slice(0, 500),
          updatedBy: Number(actor.id),
        },
      });
      await recordWorkflowException(tx, actor, current, {
        usageId: usage.id,
        relatedUsageId: nextUsage.id,
        type: WORKFLOW_EXCEPTION_TYPE.DEVICE_FAULT,
        reason,
        details: {
          fromEquipmentId: usage.equipmentId,
          fromEquipmentNo: usage.equipment.equipmentNo,
          toEquipmentId: replacement.id,
          toEquipmentNo: replacement.equipmentNo,
          stage: usage.stage,
          portionNo: usage.portionNo,
        },
      });
      await recordOperation(tx, actor, {
        module: "processing",
        action: "equipment_fault_transfer",
        targetId: current.id,
        storeId: current.storeId,
        description: `第 ${usage.portionNo} 组设备故障换机：${usage.equipment.equipmentNo} → ${replacement.equipmentNo}，原因：${reason}`,
      });
    });
  } catch (error) {
    if (error?.code !== "P2002") throw error;
    const duplicateRequest = await prisma.processingEquipmentUsage.findFirst({
      where: { requestId: operationRequestId, processingPlanId: current.id },
      select: { id: true },
    });
    if (!duplicateRequest) throw error;
  }
  return getProcessingWorkflow(prisma, actor, current.id);
}

function manualDate(value, label) {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime()))
    throw new AppError(`${label}不正确`, 400);
  return date;
}

export async function createManualEquipmentUsage(
  prisma,
  actor,
  id,
  payload = {},
) {
  const current = await getWorkflowPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.PROCESSING)
    throw new AppError("只有加工中的计划可以补录工序", 409);
  if (!isDecoction(current))
    throw new AppError("只有代煎计划需要补录设备工序", 409);
  const reason = requiredReason(payload.reason, "补录原因");
  const stage = Number(payload.stage);
  const expectedType = expectedEquipmentType(stage);
  const portionNo = Number(payload.portionNo);
  if (!Number.isInteger(portionNo) || portionNo <= 0 || portionNo > 99)
    throw new AppError("分组编号不正确", 400);
  const startedAt = manualDate(payload.startedAt, "开始时间");
  const endedAt = manualDate(payload.endedAt, "结束时间");
  if (endedAt <= startedAt) throw new AppError("结束时间必须晚于开始时间", 400);
  if (endedAt > new Date()) throw new AppError("结束时间不能晚于当前时间", 400);
  const operationRequestId = requestId(payload.requestId);

  const repeated = await prisma.processingEquipmentUsage.findFirst({
    where: { requestId: operationRequestId },
    select: { processingPlanId: true },
  });
  if (repeated) {
    if (repeated.processingPlanId !== current.id)
      throw new AppError("请求编号已被使用", 409);
    return getProcessingWorkflow(prisma, actor, current.id);
  }

  try {
    await prisma.$transaction(async (tx) => {
      const equipment = await tx.processingEquipment.findFirst({
        where: {
          id: Number(payload.equipmentId),
          storeId: current.storeId,
          type: expectedType,
          status: EQUIPMENT_STATUS.ENABLED,
          deletedAt: null,
        },
      });
      if (!equipment)
        throw new AppError("设备不存在、不可用或不属于当前门店", 404);
      const duplicate = await tx.processingEquipmentUsage.findFirst({
        where: {
          processingPlanId: current.id,
          stage,
          portionNo,
          status: { not: EQUIPMENT_USAGE_STATUS.VOIDED },
        },
        select: { id: true },
      });
      if (duplicate)
        throw new AppError(`第 ${portionNo} 组该工序已有记录`, 409);
      const overlap = await tx.processingEquipmentUsage.findFirst({
        where: {
          equipmentId: equipment.id,
          status: { not: EQUIPMENT_USAGE_STATUS.VOIDED },
          startedAt: { lt: endedAt },
          OR: [{ endedAt: null }, { endedAt: { gt: startedAt } }],
        },
        select: { id: true },
      });
      if (overlap) throw new AppError("该设备在补录时间段内已有使用记录", 409);
      if (
        [PROCESSING_STAGE.DECOCTING, PROCESSING_STAGE.PACKAGING].includes(stage)
      ) {
        const prerequisiteStage =
          stage === PROCESSING_STAGE.DECOCTING
            ? PROCESSING_STAGE.SOAKING
            : PROCESSING_STAGE.DECOCTING;
        const prerequisite = await tx.processingEquipmentUsage.findFirst({
          where: {
            processingPlanId: current.id,
            portionNo,
            stage: prerequisiteStage,
            status: EQUIPMENT_USAGE_STATUS.COMPLETED,
            endedAt: { lte: startedAt },
          },
          select: { id: true },
        });
        if (!prerequisite) {
          throw new AppError(
            stage === PROCESSING_STAGE.DECOCTING
              ? `第 ${portionNo} 组缺少已完成的浸泡记录`
              : `第 ${portionNo} 组缺少已完成的煎煮记录`,
            409,
          );
        }
      }
      const usage = await tx.processingEquipmentUsage.create({
        data: {
          processingPlanId: current.id,
          equipmentId: equipment.id,
          stage,
          portionNo,
          status: EQUIPMENT_USAGE_STATUS.COMPLETED,
          source: EQUIPMENT_USAGE_SOURCE.MANUAL,
          requestId: operationRequestId,
          startedAt,
          endedAt,
          startedBy: Number(actor.id),
          endedBy: Number(actor.id),
          endReason: `人工补录：${reason}`,
        },
      });
      await recordWorkflowException(tx, actor, current, {
        usageId: usage.id,
        type: WORKFLOW_EXCEPTION_TYPE.MANUAL_ENTRY,
        reason,
        details: {
          equipmentId: equipment.id,
          equipmentNo: equipment.equipmentNo,
          stage,
          portionNo,
          startedAt: startedAt.toISOString(),
          endedAt: endedAt.toISOString(),
        },
      });
      const fallback =
        stage === PROCESSING_STAGE.PACKAGING
          ? PROCESSING_STAGE.PACKAGING_DONE
          : stage;
      await refreshCurrentStage(tx, current.id, fallback);
      await recordOperation(tx, actor, {
        module: "processing",
        action: "equipment_usage_manual",
        targetId: current.id,
        storeId: current.storeId,
        description: `人工补录第 ${portionNo} 组${stage === PROCESSING_STAGE.SOAKING ? "浸泡" : stage === PROCESSING_STAGE.DECOCTING ? "煎煮" : "打包"}：${equipment.equipmentNo}，原因：${reason}`,
      });
    });
  } catch (error) {
    if (error?.code !== "P2002") throw error;
    const duplicateRequest = await prisma.processingEquipmentUsage.findFirst({
      where: { requestId: operationRequestId, processingPlanId: current.id },
      select: { id: true },
    });
    if (!duplicateRequest) throw error;
  }
  return getProcessingWorkflow(prisma, actor, current.id);
}

export async function assertProcessingWorkflowComplete(prisma, plan) {
  if (!plan.dispensingCompletedAt)
    throw new AppError("请先上传调配完成照片", 409);
  if (!isDecoction(plan)) return;
  const usages = await prisma.processingEquipmentUsage.findMany({
    where: { processingPlanId: plan.id },
    select: {
      stage: true,
      portionNo: true,
      status: true,
      endedAt: true,
      voidedAt: true,
    },
  });
  const normalized = usages.map((item) => ({
    ...item,
    status: Number(
      item.status ??
        (item.voidedAt
          ? EQUIPMENT_USAGE_STATUS.VOIDED
          : item.endedAt
            ? EQUIPMENT_USAGE_STATUS.COMPLETED
            : EQUIPMENT_USAGE_STATUS.ACTIVE),
    ),
  }));
  const completion = workflowCompletionState(plan, normalized);
  if (!completion.canComplete) throw new AppError(completion.blockers[0], 409);
}
