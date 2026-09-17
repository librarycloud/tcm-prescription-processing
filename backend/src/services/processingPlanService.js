import bcrypt from "bcrypt";
import { AppError } from "../utils/appError.js";
import { isSuperAdmin, ROLES } from "../constants/roles.js";
import {
  canTransition,
  DICTIONARY_TYPES,
  NOTIFY_STATUS,
  NOTIFY_TYPE,
  PAYMENT_STATUS,
  PLAN_STATUS,
  PRESCRIPTION_STATUS,
  PRIORITY,
  PROCESS_TYPE_CODES,
  SCHEDULE_TYPES,
} from "../constants/processing.js";
import {
  createPrescriptionRecord,
  syncPrescriptionStatus,
} from "./prescriptionService.js";
import { normalizeOptionalPhone, toPositiveInt } from "../utils/validators.js";
import { businessScope } from "./permissionService.js";
import { describeChanges, recordOperation } from "./operationLogService.js";
import { generateUniquePickupCode } from "../utils/pickupCode.js";
import { PICKUP_METHOD, PICKUP_METHOD_VALUES } from "../constants/package.js";
import { processingPlanRepository } from "../repositories/processingPlanRepository.js";
import { packageRepository } from "../repositories/packageRepository.js";
import { prescriptionRepository } from "../repositories/prescriptionRepository.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import { publishProcessingCompletedRobotEvent } from "./robotBusinessEventService.js";
import { PROCESSING_STAGE } from "../constants/processingWorkflow.js";
import { generateProcessingPlanIdentity } from "../utils/processingCode.js";
import { assertProcessingWorkflowComplete } from "./processingWorkflowService.js";
import { withPickupQrContent } from "../utils/pickupQr.js";

const scope = (actor, requestedStoreId) => ({
  ...businessScope(actor, requestedStoreId),
  deletedAt: null,
});

function dayRange(value = new Date()) {
  const start = new Date(value);
  if (Number.isNaN(start.getTime())) throw new AppError("日期格式不正确", 400);
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { start, end };
}

function nextDay(value, days = 1) {
  const date = new Date(value);
  date.setDate(date.getDate() + days);
  return date;
}

function include() {
  return {
    processType: true,
    notifyTypeDictionary: { select: { id: true, code: true, name: true } },
    store: { select: { id: true, name: true, code: true } },
    prescription: { include: { doctor: true, source: true } },
    package: {
      select: {
        id: true,
        pickupCode: true,
        status: true,
        receiverName: true,
        receiverPhone: true,
      },
    },
  };
}

function dateOrNull(value, label) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime()))
    throw new AppError(`${label}格式不正确`, 400);
  return date;
}

function normalizeExpressAddress(value, pickupMethod) {
  if (![PICKUP_METHOD.FAMILY, PICKUP_METHOD.DELIVERY].includes(pickupMethod)) return null;
  const address = String(value || "").trim();
  if (address.length > 500) throw new AppError("地址不能超过 500 个字符", 400);
  return address || null;
}

async function normalizeNotifyType(prisma, value) {
  const notifyTypeId =
    value === undefined || value === null || value === ""
      ? null
      : Number(value);
  if (notifyTypeId !== null && (!Number.isInteger(notifyTypeId) || notifyTypeId <= 0))
    throw new AppError("提醒方式不正确", 400);
  const notifyTypeItem = await prisma.dictionary.findFirst({
    where: {
      ...(notifyTypeId === null
        ? { code: NOTIFY_TYPE.NONE }
        : { id: notifyTypeId }),
      type: DICTIONARY_TYPES.NOTIFY_TYPE,
      status: RECORD_STATUS.ENABLED,
      deletedAt: null,
    },
    select: { id: true },
  });
  if (!notifyTypeItem) throw new AppError("提醒方式不存在或已停用", 400);
  return notifyTypeItem.id;
}

async function getPlan(prisma, actor, id) {
  const item = await processingPlanRepository.findFirst(prisma, {
    where: { id: Number(id), ...scope(actor) },
    include: include(),
  });
  if (!item) throw new AppError("加工计划不存在", 404);
  return item;
}

async function normalize(prisma, actor, payload, current = null) {
  const prescriptionId = Number(
    payload.prescriptionId ?? current?.prescriptionId,
  );
  const processTypeId = Number(payload.processTypeId ?? current?.processTypeId);
  const totalDose = Number(payload.totalDose ?? current?.totalDose);
  if (!Number.isInteger(prescriptionId) || prescriptionId <= 0)
    throw new AppError("请选择处方", 400);
  if (!Number.isInteger(processTypeId) || processTypeId <= 0)
    throw new AppError("请选择加工类型", 400);
  if (!Number.isInteger(totalDose) || totalDose <= 0)
    throw new AppError("总剂量必须为正整数", 400);
  const prescription = await prescriptionRepository.findFirst(prisma, {
    where: { id: prescriptionId, ...scope(actor) },
  });
  if (!prescription || prescription.status === PRESCRIPTION_STATUS.CANCELLED)
    throw new AppError("处方不存在或已取消", 400);
  const processType = await prisma.dictionary.findFirst({
    where: {
      id: processTypeId,
      type: DICTIONARY_TYPES.PROCESS_TYPE,
      status: RECORD_STATUS.ENABLED,
      deletedAt: null,
    },
  });
  if (!processType) throw new AppError("加工类型不存在或已停用", 400);
  const isDecoction =
    processType.code === PROCESS_TYPE_CODES.DECOCTION ||
    processType.name === "代煎";
  const bagCount = isDecoction
    ? Number(payload.bagCount ?? current?.bagCount)
    : null;
  const volumeMl = isDecoction
    ? Number(payload.volumeMl ?? current?.volumeMl)
    : null;
  if (isDecoction && (!Number.isInteger(bagCount) || bagCount <= 0))
    throw new AppError("代煎袋数必须为正整数", 400);
  if (isDecoction && (!Number.isInteger(volumeMl) || volumeMl <= 0))
    throw new AppError("代煎毫升数必须为正整数", 400);
  const usageMethod =
    payload.usageMethod === undefined
      ? current?.usageMethod || null
      : String(payload.usageMethod || "").trim() || null;
  if (usageMethod && usageMethod.length > 200)
    throw new AppError("服用方法不能超过 200 个字符", 400);
  const scheduleType = Number(
    payload.scheduleType ?? current?.scheduleType ?? SCHEDULE_TYPES.DATE,
  );
  if (!Object.values(SCHEDULE_TYPES).includes(scheduleType))
    throw new AppError("计划类型不正确", 400);
  const processDate =
    scheduleType === SCHEDULE_TYPES.DATE
      ? dateOrNull(payload.processDate ?? current?.processDate, "加工日期")
      : null;
  if (scheduleType === SCHEDULE_TYPES.DATE && !processDate)
    throw new AppError("请选择加工日期", 400);
  const takenDose = Number(payload.takenDose ?? current?.takenDose ?? 0);
  if (!Number.isInteger(takenDose) || takenDose < 0 || takenDose > totalDose)
    throw new AppError("已取剂量不正确", 400);
  const storeId = prescription.storeId;
  if (!isSuperAdmin(actor) && storeId !== Number(actor.storeId))
    throw new AppError("无权操作该处方", 403);
  const priority = Number(
    payload.priority ?? current?.priority ?? PRIORITY.NORMAL,
  );
  if (!Object.values(PRIORITY).includes(priority))
    throw new AppError("优先级不正确", 400);
  const notifyType = await normalizeNotifyType(
    prisma,
    payload.notifyType ?? current?.notifyType,
  );
  const notifyStatus = Number(
    payload.notifyStatus ?? current?.notifyStatus ?? NOTIFY_STATUS.PENDING,
  );
  if (!Object.values(NOTIFY_STATUS).includes(notifyStatus))
    throw new AppError("通知状态不正确", 400);
  const pickupMethod = Number(
    payload.pickupMethod ?? current?.pickupMethod ?? PICKUP_METHOD.SELF,
  );
  if (!PICKUP_METHOD_VALUES.includes(pickupMethod))
    throw new AppError("请选择取货方式", 400);
  const expressAddress = normalizeExpressAddress(
    payload.expressAddress ?? current?.expressAddress,
    pickupMethod,
  );
  return {
    prescriptionId,
    processTypeId,
    totalDose,
    bagCount,
    volumeMl,
    usageMethod,
    takenDose,
    remainingDose: totalDose - takenDose,
    scheduleType,
    processDate,
    priority,
    notifyType,
    notifyStatus,
    notifyTime:
      notifyStatus === NOTIFY_STATUS.NOTIFIED
        ? dateOrNull(
            payload.notifyTime ?? current?.notifyTime ?? new Date(),
            "通知时间",
          )
        : null,
    processRemark:
      payload.processRemark === undefined
        ? current?.processRemark || null
        : String(payload.processRemark || "").trim() || null,
    paymentStatus:
      Number(
        payload.paymentStatus ?? current?.paymentStatus ?? PAYMENT_STATUS.PAID,
      ) === PAYMENT_STATUS.UNPAID
        ? PAYMENT_STATUS.UNPAID
        : PAYMENT_STATUS.PAID,
    pickupMethod,
    expressAddress,
    remark:
      payload.remark === undefined
        ? current?.remark || null
        : String(payload.remark || "").trim() || null,
    storeId,
  };
}

export async function listProcessingPlans(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const where = {
    ...scope(actor, isSuperAdmin(actor) ? query.storeId : undefined),
  };
  if (query.status !== undefined && query.status !== "") {
    const status = Number(query.status);
    if (!Object.values(PLAN_STATUS).includes(status))
      throw new AppError("加工状态不正确", 400);
    where.status = status;
  }
  if (query.scheduleType !== undefined && query.scheduleType !== "") {
    const scheduleType = Number(query.scheduleType);
    if (!Object.values(SCHEDULE_TYPES).includes(scheduleType))
      throw new AppError("计划类型不正确", 400);
    where.scheduleType = scheduleType;
  }
  if (query.prescriptionId) where.prescriptionId = Number(query.prescriptionId);
  if (query.processTypeId) where.processTypeId = Number(query.processTypeId);
  if (query.doctorId) where.prescription = { doctorId: Number(query.doctorId) };
  if (query.priority !== undefined && query.priority !== "")
    where.priority = Number(query.priority);
  const view = String(query.view || "").toLowerCase();
  if (["today", "today-all"].includes(view)) {
    const { start, end } = dayRange();
    where.AND = [
      {
        OR: [
          {
            status: PLAN_STATUS.WAITING,
            scheduleType: SCHEDULE_TYPES.DATE,
            processDate: { gte: start, lt: end },
          },
          { status: PLAN_STATUS.PROCESSING },
          { finishDate: { gte: start, lt: end } },
        ],
      },
    ];
  } else if (view === "today-waiting") {
    const { start, end } = dayRange();
    where.status = PLAN_STATUS.WAITING;
    where.scheduleType = SCHEDULE_TYPES.DATE;
    where.processDate = { gte: start, lt: end };
  } else if (view === "overdue") {
    const { start } = dayRange();
    where.status = PLAN_STATUS.WAITING;
    where.scheduleType = SCHEDULE_TYPES.DATE;
    where.processDate = { lt: start };
  } else if (view === "processing") {
    where.status = PLAN_STATUS.PROCESSING;
  } else if (view === "today-finished") {
    const { start, end } = dayRange();
    where.finishDate = { gte: start, lt: end };
  } else if (view === "urgent") {
    where.priority = { gte: PRIORITY.URGENT };
    where.status = { in: [PLAN_STATUS.WAITING, PLAN_STATUS.PROCESSING] };
  } else if (view === "tomorrow") {
    const tomorrow = nextDay(new Date());
    const { start, end } = dayRange(tomorrow);
    where.status = PLAN_STATUS.WAITING;
    where.scheduleType = SCHEDULE_TYPES.DATE;
    where.processDate = { gte: start, lt: end };
  } else if (view === "notice") {
    where.status = PLAN_STATUS.WAITING;
    where.scheduleType = SCHEDULE_TYPES.NOTICE;
    where.processDate = null;
  } else if (query.processDate) {
    const { start, end } = dayRange(query.processDate);
    where.processDate = { gte: start, lt: end };
  }
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { processRemark: { contains: keyword } },
      { remark: { contains: keyword } },
      { prescription: { customerName: { contains: keyword } } },
      { prescription: { phone: { contains: keyword } } },
    ];
  }
  const dateAndStatusOrder = [
    { processDate: "desc" },
    { status: "asc" },
    { updatedAt: "desc" },
    { id: "desc" },
  ];
  const orderBy = view === "all"
    ? [{ scheduleType: "desc" }, ...dateAndStatusOrder]
    : ["today", "today-all"].includes(view)
      ? dateAndStatusOrder
      : ["processing", "today-finished"].includes(view)
        ? [{ updatedAt: "desc" }, { id: "desc" }]
        : view === "overdue"
          ? [{ processDate: "asc" }, { priority: "desc" }, { queueOrder: "asc" }]
          : ["today-waiting", "tomorrow"].includes(view) || query.processDate
            ? [{ queueOrder: "asc" }, { priority: "desc" }, { createdAt: "asc" }]
            : [
                { priority: "desc" },
                { processDate: "asc" },
                { queueOrder: "asc" },
                { createdAt: "asc" },
              ];
  const [list, total] = await Promise.all([
    processingPlanRepository.findMany(prisma, {
      where,
      include: include(),
      orderBy,
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    processingPlanRepository.count(prisma, { where }),
  ]);
  return {
    list: list.map((plan) => ({
      ...plan,
      package: withPickupQrContent(plan.package),
    })),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

async function assignScheduledQueueOrder(prisma, actor, data, excludedPlanId = null) {
  const { start, end } = dayRange(data.processDate);
  const queueScope = {
    storeId: data.storeId,
    scheduleType: SCHEDULE_TYPES.DATE,
    processDate: { gte: start, lt: end },
    deletedAt: null,
    ...(excludedPlanId ? { id: { not: Number(excludedPlanId) } } : {}),
  };
  const latest = await processingPlanRepository.findFirst(prisma, {
    where: { ...queueScope, queueOrder: { not: null } },
    orderBy: { queueOrder: "desc" },
    select: { queueOrder: true },
  });
  if (Number(latest?.queueOrder) > 0) {
    data.queueOrder = Number(latest.queueOrder) + 1;
    return;
  }

  const activeScope = {
    ...queueScope,
    status: { in: [PLAN_STATUS.WAITING, PLAN_STATUS.PROCESSING] },
  };
  const aheadCount = await processingPlanRepository.count(prisma, {
    where: { ...activeScope, priority: { gte: data.priority } },
  });
  data.queueOrder = aheadCount + 1;
  await processingPlanRepository.updateMany(prisma, {
    where: { ...activeScope, queueOrder: { gte: data.queueOrder } },
    data: { queueOrder: { increment: 1 }, updatedBy: Number(actor.id) },
  });
}

function isSameProcessingDay(left, right) {
  if (!left || !right) return false;
  return dayRange(left).start.getTime() === dayRange(right).start.getTime();
}

export async function createProcessingPlanRecord(prisma, actor, payload, options = {}) {
  const data = await normalize(prisma, actor, payload);
  const batchNo = Number(payload.batchNo);
  if (!Number.isInteger(batchNo) || batchNo <= 0)
    throw new AppError("批次号必须为正整数", 400);
  const existing = await processingPlanRepository.findFirst(prisma, {
    where: { prescriptionId: data.prescriptionId, batchNo },
    select: {
      id: true,
      deletedAt: true,
      pickupCode: true,
      planCode: true,
      scanToken: true,
    },
  });
  if (existing && !existing.deletedAt)
    throw new AppError("该处方批次号已存在", 409);

  data.batchNo = batchNo;
  data.status = PLAN_STATUS.WAITING;
  data.pickupCode = existing?.pickupCode || await generateUniquePickupCode(prisma);
  const identity = existing?.planCode && existing?.scanToken
    ? { planCode: existing.planCode, scanToken: existing.scanToken }
    : await generateProcessingPlanIdentity(prisma);
  data.planCode = identity.planCode;
  data.scanToken = identity.scanToken;
  data.currentStage = null;
  data.dispensingCompletedAt = null;
  data.dispensingCompletedBy = null;
  if (data.scheduleType === SCHEDULE_TYPES.DATE) {
    await assignScheduledQueueOrder(prisma, actor, data);
  } else {
    data.queueOrder = null;
  }

  const created = existing
    ? await processingPlanRepository.update(prisma, {
        where: { id: existing.id },
        data: {
          ...data,
          deletedAt: null,
          deletedBy: null,
          updatedBy: Number(actor.id),
        },
        include: include(),
      })
    : await processingPlanRepository.create(prisma, {
        data: { ...data, createdBy: Number(actor.id) },
        include: include(),
      });
  await recordOperation(prisma, actor, {
    module: "processing",
    action: existing ? "restore" : "create",
    targetId: created.id,
    storeId: created.storeId,
    description:
      options.description ||
      (existing ? "恢复已删除的加工计划" : "新增加工计划"),
  });
  return created;
}

export async function createProcessingPlan(prisma, actor, payload) {
  return prisma.$transaction(async (tx) => {
    const created = await createProcessingPlanRecord(tx, actor, payload);
    await syncPrescriptionStatus(tx, created.prescriptionId, actor.id);
    return created;
  });
}

export async function createProcessingPlanBatch(prisma, actor, payload = {}) {
  const plans = Array.isArray(payload.plans) ? payload.plans : [];
  if (!plans.length) throw new AppError("请至少添加一个加工批次", 400);
  if (plans.length > 100) throw new AppError("单次最多创建 100 个加工批次", 400);

  const seenBatchNos = new Set();
  for (const plan of plans) {
    const batchNo = Number(plan?.batchNo);
    if (!Number.isInteger(batchNo) || batchNo <= 0)
      throw new AppError("批次号必须为正整数", 400);
    if (seenBatchNos.has(batchNo))
      throw new AppError(`第 ${batchNo} 批重复，请调整批次号`, 400);
    seenBatchNos.add(batchNo);
  }

  const prescriptionMode = String(
    payload.prescriptionMode || (payload.prescriptionId ? "existing" : "new"),
  ).toLowerCase();
  if (!["existing", "new"].includes(prescriptionMode))
    throw new AppError("处方创建方式不正确", 400);

  return prisma.$transaction(async (tx) => {
    let prescription;
    if (prescriptionMode === "new") {
      prescription = await createPrescriptionRecord(
        tx,
        actor,
        payload.prescription || {},
        { description: "加工工作台新建处方" },
      );
    } else {
      const prescriptionId = Number(payload.prescriptionId);
      if (!Number.isInteger(prescriptionId) || prescriptionId <= 0)
        throw new AppError("请选择处方", 400);
      prescription = await prescriptionRepository.findFirst(tx, {
        where: { id: prescriptionId, ...scope(actor) },
        include: {
          doctor: true,
          source: true,
          store: { select: { id: true, name: true, code: true } },
        },
      });
      if (!prescription || prescription.status === PRESCRIPTION_STATUS.CANCELLED)
        throw new AppError("处方不存在或已取消", 400);
    }

    const batchNos = [...seenBatchNos];
    const existingPlans = await processingPlanRepository.findMany(tx, {
      where: {
        prescriptionId: prescription.id,
        batchNo: { in: batchNos },
        deletedAt: null,
      },
      select: { batchNo: true },
    });
    if (existingPlans.length) {
      const duplicated = existingPlans
        .map((item) => `第 ${item.batchNo} 批`)
        .join("、");
      throw new AppError(`${duplicated} 已存在，请调整批次号`, 409);
    }

    const createdPlans = [];
    for (const plan of plans) {
      const created = await createProcessingPlanRecord(
        tx,
        actor,
        { ...plan, prescriptionId: prescription.id },
        {
          description: `批量新增加工计划：第 ${Number(plan.batchNo)} 批，${Number(
            plan.totalDose,
          )} 剂`,
        },
      );
      createdPlans.push(created);
    }

    await recordOperation(tx, actor, {
      module: "processing",
      action: "batchCreate",
      targetId: prescription.id,
      storeId: prescription.storeId,
      description: `批量创建加工任务：${plans.length} 个批次`,
    });

    await syncPrescriptionStatus(tx, prescription.id, actor.id);

    return { prescription, plans: createdPlans };
  });
}

export async function updateProcessingPlan(prisma, actor, id, payload) {
  const current = await getPlan(prisma, actor, id);
  const providedKeys = Object.keys(payload).filter((key) => payload[key] !== undefined);
  const metadataOnlyUpdate =
    providedKeys.length > 0 &&
    providedKeys.every((key) =>
      [
        "notifyType",
        "notifyStatus",
        "notifyTime",
        "paymentStatus",
        "usageMethod",
        "pickupMethod",
        "expressAddress",
      ].includes(key),
    );
  const scheduleOnlyUpdate =
    providedKeys.length > 0 &&
    providedKeys.every((key) => ["scheduleType", "processDate"].includes(key));
  const canEditMetadata = [
    PLAN_STATUS.WAITING,
    PLAN_STATUS.PROCESSING,
    PLAN_STATUS.FINISHED,
    PLAN_STATUS.READY_PICKUP,
  ].includes(current.status);
  if (
    ![PLAN_STATUS.WAITING, PLAN_STATUS.PROCESSING].includes(current.status) &&
    !(canEditMetadata && metadataOnlyUpdate)
  )
    throw new AppError("加工已完成或已进入领取流程，不能再修改", 409);
  if (canEditMetadata && metadataOnlyUpdate) {
    const notifyType = await normalizeNotifyType(
      prisma,
      payload.notifyType ?? current.notifyType,
    );
    const notifyStatus = Number(
      payload.notifyStatus ?? current.notifyStatus ?? NOTIFY_STATUS.PENDING,
    );
    if (!Object.values(NOTIFY_STATUS).includes(notifyStatus))
      throw new AppError("通知状态不正确", 400);
    const paymentStatus = Number(
      payload.paymentStatus ?? current.paymentStatus ?? PAYMENT_STATUS.PAID,
    );
    if (!Object.values(PAYMENT_STATUS).includes(paymentStatus))
      throw new AppError("收费状态不正确", 400);
    const notifyTime =
      notifyStatus === NOTIFY_STATUS.NOTIFIED
        ? dateOrNull(payload.notifyTime ?? current.notifyTime ?? new Date(), "通知时间")
        : null;
    const usageMethod =
      payload.usageMethod === undefined
        ? current.usageMethod || null
        : String(payload.usageMethod || "").trim() || null;
    if (usageMethod && usageMethod.length > 200)
      throw new AppError("服用方法不能超过 200 个字符", 400);
    const pickupMethod = Number(
      payload.pickupMethod ?? current.pickupMethod ?? PICKUP_METHOD.SELF,
    );
    if (!PICKUP_METHOD_VALUES.includes(pickupMethod))
      throw new AppError("请选择取货方式", 400);
    const expressAddress = normalizeExpressAddress(
      payload.expressAddress ?? current.expressAddress,
      pickupMethod,
    );
    const metadataData = {
      notifyType,
      notifyStatus,
      notifyTime,
      paymentStatus,
      usageMethod,
      pickupMethod,
      expressAddress,
      updatedBy: Number(actor.id),
    };
    const updated =
      (payload.pickupMethod !== undefined || payload.expressAddress !== undefined) && current.package?.id
        ? await prisma.$transaction(async (tx) => {
            const plan = await processingPlanRepository.update(tx, {
              where: { id: current.id },
              data: metadataData,
              include: include(),
            });
            await packageRepository.update(tx, {
              where: { id: current.package.id },
              data: {
                pickupMethod,
                expressAddress,
                updatedBy: Number(actor.id),
              },
            });
            return plan;
          })
        : await processingPlanRepository.update(prisma, {
            where: { id: current.id },
            data: metadataData,
            include: include(),
          });
    await recordOperation(prisma, actor, {
      module: "processing",
      action: "update",
      targetId: updated.id,
      storeId: updated.storeId,
      description: describeChanges(current, updated, [
        { key: "notifyType", label: "提醒方式" },
        { key: "notifyStatus", label: "通知状态", values: { 0: "未通知", 1: "已通知" } },
        { key: "notifyTime", label: "通知时间" },
        { key: "paymentStatus", label: "收费状态", values: { 0: "未收费", 1: "已收费" } },
        { key: "usageMethod", label: "服用方法" },
        { key: "pickupMethod", label: "取货方式", values: { 0: "自提", 1: "跑腿", 2: "快递" } },
        { key: "expressAddress", label: "地址" },
      ]),
    });
    return updated;
  }
  if (current.status === PLAN_STATUS.WAITING && scheduleOnlyUpdate) {
    const scheduleType = Number(
      payload.scheduleType ?? current.scheduleType ?? SCHEDULE_TYPES.DATE,
    );
    if (!Object.values(SCHEDULE_TYPES).includes(scheduleType))
      throw new AppError("计划类型不正确", 400);
    const processDate =
      scheduleType === SCHEDULE_TYPES.DATE
        ? dateOrNull(payload.processDate ?? current.processDate, "加工日期")
        : null;
    if (scheduleType === SCHEDULE_TYPES.DATE && !processDate)
      throw new AppError("请选择加工日期", 400);

    const scheduleData = {
      storeId: current.storeId,
      scheduleType,
      processDate,
      queueOrder: current.queueOrder,
    };
    if (scheduleType === SCHEDULE_TYPES.NOTICE) {
      scheduleData.queueOrder = null;
    } else if (
      current.scheduleType !== SCHEDULE_TYPES.DATE ||
      !isSameProcessingDay(current.processDate, processDate) ||
      current.queueOrder == null
    ) {
      await assignScheduledQueueOrder(prisma, actor, scheduleData, current.id);
    }

    const updated = await processingPlanRepository.update(prisma, {
      where: { id: current.id },
      data: {
        scheduleType: scheduleData.scheduleType,
        processDate: scheduleData.processDate,
        queueOrder: scheduleData.queueOrder,
        updatedBy: Number(actor.id),
      },
      include: include(),
    });
    await recordOperation(prisma, actor, {
      module: "processing",
      action: "update",
      targetId: updated.id,
      storeId: updated.storeId,
      description: describeChanges(current, updated, [
        {
          key: "scheduleType",
          label: "调度方式",
          values: { 1: "指定日期", 2: "等待通知" },
        },
        { key: "processDate", label: "计划加工日期" },
        { key: "queueOrder", label: "加工顺序" },
      ]),
    });
    return updated;
  }
  const data = await normalize(prisma, actor, payload, current);
  if (data.scheduleType === SCHEDULE_TYPES.NOTICE) {
    data.queueOrder = null;
  } else if (
    current.scheduleType !== SCHEDULE_TYPES.DATE ||
    !isSameProcessingDay(current.processDate, data.processDate) ||
    current.queueOrder == null
  ) {
    await assignScheduledQueueOrder(prisma, actor, data, current.id);
  }
  if (payload.batchNo !== undefined) {
    const batchNo = Number(payload.batchNo);
    if (!Number.isInteger(batchNo) || batchNo <= 0)
      throw new AppError("批次号必须为正整数", 400);
    data.batchNo = batchNo;
  }
  const requestedStatus =
    payload.status === undefined ? null : Number(payload.status);
  if (requestedStatus !== null && requestedStatus !== current.status) {
    if (!Object.values(PLAN_STATUS).includes(requestedStatus))
      throw new AppError("加工状态不正确", 400);
    if (!canTransition(current.status, requestedStatus))
      throw new AppError("当前状态不允许该操作", 409);
    if (
      requestedStatus === PLAN_STATUS.CANCELLED &&
      current.status === PLAN_STATUS.PROCESSING &&
      (current.currentStage !== PROCESSING_STAGE.DISPENSING || current.dispensingCompletedAt)
    ) {
      throw new AppError("只有尚未完成调配的加工计划可以取消", 409);
    }
    data.status = requestedStatus;
    if (data.status === PLAN_STATUS.PROCESSING && !current.startDate) {
      data.startDate = new Date();
      data.currentStage = PROCESSING_STAGE.DISPENSING;
    }
    if (data.status === PLAN_STATUS.FINISHED) {
      await assertProcessingWorkflowComplete(prisma, current);
      data.currentStage = PROCESSING_STAGE.COMPLETED;
      return finishProcessingPlan(
        prisma,
        actor,
        current,
        data,
        payload.createPackage !== false,
      );
    }
  }
  data.updatedBy = Number(actor.id);
  const updated = await processingPlanRepository.update(prisma, {
    where: { id: current.id },
    data,
    include: include(),
  });
  if (requestedStatus === PLAN_STATUS.CANCELLED) {
    await syncPrescriptionStatus(prisma, updated.prescriptionId, actor.id);
  }
  const action =
    requestedStatus !== null && requestedStatus !== current.status
      ? String(requestedStatus)
      : "update";
  await recordOperation(prisma, actor, {
    module: "processing",
    action,
    targetId: updated.id,
    storeId: updated.storeId,
    description: describeChanges(current, updated, [
      { key: "batchNo", label: "批次" },
      { label: "加工方式", get: (item) => item?.processType?.name || item?.processTypeId },
      { key: "totalDose", label: "剂数" },
      { key: "bagCount", label: "袋数" },
      { key: "volumeMl", label: "毫升数" },
      { key: "usageMethod", label: "服用方法" },
      {
        key: "scheduleType",
        label: "调度方式",
        values: { 1: "指定日期", 2: "等待通知" },
      },
      { key: "processDate", label: "计划加工日期" },
      { key: "startDate", label: "开始加工时间" },
      {
        key: "status",
        label: "状态",
        values: {
          0: "待加工",
          1: "加工中",
          2: "加工完成",
          3: "待领取",
          4: "已领取",
          5: "已取消",
        },
      },
      { key: "priority", label: "优先级", values: { 0: "普通", 1: "加急" } },
      { key: "notifyType", label: "提醒方式" },
      { key: "notifyStatus", label: "通知状态", values: { 0: "未通知", 1: "已通知" } },
      { key: "notifyTime", label: "通知时间" },
      { key: "processRemark", label: "加工备注" },
      { key: "paymentStatus", label: "收费状态", values: { 0: "未收费", 1: "已收费" } },
      { key: "pickupMethod", label: "取货方式", values: { 0: "自提", 1: "跑腿", 2: "快递" } },
      { key: "expressAddress", label: "地址" },
      { key: "remark", label: "备注" },
    ]),
  });
  return updated;
}

function processingPackageItemName(plan) {
  const parts = [plan.processType.name, `${plan.totalDose}剂`];
  const isDecoction =
    plan.processType.code === PROCESS_TYPE_CODES.DECOCTION ||
    plan.processType.name === "代煎";
  const bagCount = Number(plan.bagCount);
  if (isDecoction && Number.isInteger(bagCount) && bagCount > 0) {
    parts.push(`${bagCount}袋`);
  }
  return parts.join(" ");
}

async function createPackageForPlan(prisma, actor, plan, packageRemark) {
  const existingPackage = await packageRepository.findUnique(prisma, {
    where: { processingPlanId: plan.id },
    select: { id: true },
  });
  if (existingPackage) throw new AppError("该加工计划已生成包裹", 409);

  const prescription = plan.prescription;
  const normalizedPhone = normalizeOptionalPhone(prescription.phone);
  if (normalizedPhone) {
    await prisma.user.upsert({
      where: { phone: normalizedPhone },
      update: {},
      create: {
        username: null,
        phone: normalizedPhone,
        password: await bcrypt.hash(
          `processing-package:${normalizedPhone}:${Date.now()}`,
          10,
        ),
        status: RECORD_STATUS.ENABLED,
        name: prescription.customerName,
        createdBy: actor.id,
        updatedBy: actor.id,
      },
      select: { id: true },
    });
  }

  const pickupCode = plan.pickupCode || await generateUniquePickupCode(prisma);
  const itemInfo = packageRemark === undefined
    ? plan.processRemark || plan.remark || null
    : String(packageRemark || "").trim() || null;
  if (itemInfo && itemInfo.length > 500)
    throw new AppError("包裹备注不能超过 500 个字符", 400);
  return packageRepository.create(prisma, {
    data: {
      storeId: plan.storeId,
      pickupCode,
      itemName: processingPackageItemName(plan),
      itemInfo,
      receiverName: prescription.customerName,
      receiverPhone: normalizedPhone,
      pickupMethod: plan.pickupMethod,
      expressAddress: plan.expressAddress || null,
      createdBy: Number(actor.id),
      processingPlanId: plan.id,
    },
  });
}

async function finishProcessingPlan(
  prisma,
  actor,
  current,
  data,
  shouldCreatePackage,
) {
  try {
    const result = await prisma.$transaction(async (tx) => {
      const completedPlan = { ...current, ...data };
      const pkg = shouldCreatePackage
        ? await createPackageForPlan(tx, actor, completedPlan)
        : null;
      const updated = await processingPlanRepository.update(tx, {
        where: { id: current.id },
        data: {
          ...data,
          status: shouldCreatePackage
            ? PLAN_STATUS.READY_PICKUP
            : PLAN_STATUS.FINISHED,
          takenDose: data.totalDose,
          remainingDose: 0,
          finishDate: new Date(),
          updatedBy: Number(actor.id),
        },
        include: include(),
      });
      await syncPrescriptionStatus(tx, updated.prescriptionId, actor.id);
      await recordOperation(tx, actor, {
        module: "processing",
        action: "finish",
        targetId: updated.id,
        storeId: updated.storeId,
        description: describeChanges(current, updated, [
          {
            key: "status",
            label: "状态",
            values: {
              PROCESSING: "加工中",
              FINISHED: "加工完成",
              READY_PICKUP: "待领取",
            },
          },
          { key: "takenDose", label: "已加工剂数" },
          { key: "remainingDose", label: "剩余剂数" },
          { key: "finishDate", label: "完成加工时间" },
          {
            key: "notifyStatus",
            label: "通知状态",
            values: { 0: "未通知", 1: "已通知" },
          },
          { key: "notifyTime", label: "通知时间" },
        ]),
      });
      if (pkg) {
        await recordOperation(tx, actor, {
          module: "package",
          action: "create",
          targetId: pkg.id,
          storeId: updated.storeId,
          description: `加工计划 ${updated.id} 完成时生成包裹`,
        });
      }
      return updated;
    });
    await publishProcessingCompletedRobotEvent(prisma, result, actor);
    return result;
  } catch (error) {
    if (error?.code === "P2002")
      throw new AppError("该加工计划已生成包裹", 409);
    throw error;
  }
}

export async function generateProcessingPlanPackage(prisma, actor, id, payload = {}) {
  const current = await getPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.FINISHED)
    throw new AppError("仅已完成且未生成包裹的加工计划可生成包裹", 409);

  try {
    return await prisma.$transaction(async (tx) => {
      const packageRemark = payload.itemInfo !== undefined ? payload.itemInfo : payload.remark;
      const pkg = await createPackageForPlan(tx, actor, current, packageRemark);
      const updated = await processingPlanRepository.update(tx, {
        where: { id: current.id },
        data: {
          status: PLAN_STATUS.READY_PICKUP,
          updatedBy: Number(actor.id),
        },
        include: include(),
      });
      await syncPrescriptionStatus(tx, updated.prescriptionId, actor.id);
      await recordOperation(tx, actor, {
        module: "processing",
        action: "generate_package",
        targetId: updated.id,
        storeId: updated.storeId,
        description: "加工完成后补生成待取包裹：状态从加工完成改为待领取",
      });
      await recordOperation(tx, actor, {
        module: "package",
        action: "create",
        targetId: pkg.id,
        storeId: updated.storeId,
        description: `加工计划 ${updated.id} 补生成包裹`,
      });
      return updated;
    });
  } catch (error) {
    if (error?.code === "P2002")
      throw new AppError("该加工计划已生成包裹", 409);
    throw error;
  }
}

export async function deleteProcessingPlan(prisma, actor, id) {
  const current = await getPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.WAITING)
    throw new AppError("仅待加工计划可删除", 409);
  await processingPlanRepository.update(prisma, {
    where: { id: current.id },
    data: {
      deletedAt: new Date(),
      deletedBy: actor.id,
      updatedBy: actor.id,
    },
  });
  await syncPrescriptionStatus(prisma, current.prescriptionId, actor.id);
  await recordOperation(prisma, actor, {
    module: "processing",
    action: "delete",
    targetId: current.id,
    storeId: current.storeId,
    description: "删除加工计划",
  });
  return { id: current.id };
}

export async function reorderPrescriptionPlans(
  prisma,
  actor,
  prescriptionIdValue,
  payload = {},
) {
  const prescription = await getPrescriptionForPlanOrder(
    prisma,
    actor,
    prescriptionIdValue,
  );
  const ids = Array.isArray(payload.ids)
    ? payload.ids.map(Number).filter((id) => Number.isInteger(id) && id > 0)
    : [];
  const currentIds = prescription.plans.map((plan) => plan.id);
  if (
    ids.length !== currentIds.length ||
    new Set(ids).size !== ids.length ||
    ids.some((id) => !currentIds.includes(id))
  ) {
    throw new AppError("加工批次顺序数据不完整", 400);
  }

  await prisma.$transaction(async (tx) => {
    const allPlans = await processingPlanRepository.findMany(tx, {
      where: { prescriptionId: prescription.id },
      select: { id: true },
    });
    for (const plan of allPlans) {
      await processingPlanRepository.update(tx, {
        where: { id: plan.id },
        data: { batchNo: -plan.id, updatedBy: Number(actor.id) },
      });
    }
    for (const [index, id] of ids.entries()) {
      await processingPlanRepository.update(tx, {
        where: { id },
        data: { batchNo: index + 1, updatedBy: Number(actor.id) },
      });
    }
    await recordOperation(tx, actor, {
      module: "processing",
      action: "batch_reorder",
      targetId: prescription.id,
      storeId: prescription.storeId,
      description: "调整处方加工批次顺序",
    });
  });
  return { ids };
}

async function getPrescriptionForPlanOrder(prisma, actor, idValue) {
  const prescription = await prescriptionRepository.findFirst(prisma, {
    where: { id: Number(idValue), ...scope(actor) },
    select: {
      id: true,
      storeId: true,
      plans: {
        where: { deletedAt: null },
        orderBy: [{ batchNo: "asc" }, { createdAt: "asc" }],
        select: { id: true },
      },
    },
  });
  if (!prescription) throw new AppError("处方不存在", 404);
  return prescription;
}

export async function transitionProcessingPlan(
  prisma,
  actor,
  id,
  payload = {},
) {
  return updateProcessingPlan(prisma, actor, id, {
    status: payload.status,
    createPackage: payload.createPackage,
    notifyStatus: payload.notifyStatus,
    notifyTime: payload.notifyTime,
  });
}

export async function delayProcessingPlan(prisma, actor, id, payload = {}) {
  const current = await getPlan(prisma, actor, id);
  if (current.status !== PLAN_STATUS.WAITING) {
    throw new AppError("当前状态不能延期", 409);
  }
  const scheduleType = Number(payload.scheduleType);
  if (!Object.values(SCHEDULE_TYPES).includes(scheduleType))
    throw new AppError("请选择延期方式", 400);
  const updated = await updateProcessingPlan(prisma, actor, current.id, {
    scheduleType,
    processDate:
      scheduleType === SCHEDULE_TYPES.DATE ? payload.processDate : null,
  });
  await recordOperation(prisma, actor, {
    module: "processing",
    action: "delay",
    targetId: updated.id,
    storeId: updated.storeId,
    description:
      scheduleType === SCHEDULE_TYPES.NOTICE
        ? "延期并等待顾客通知"
        : "延期至指定日期",
  });
  return updated;
}

export async function receiveProcessingNotice(prisma, actor, id, payload = {}) {
  const current = await getPlan(prisma, actor, id);
  if (
    current.status !== PLAN_STATUS.WAITING ||
    current.scheduleType !== SCHEDULE_TYPES.NOTICE
  ) {
    throw new AppError("当前计划不在等待通知中", 409);
  }
  const updated = await updateProcessingPlan(prisma, actor, current.id, {
    scheduleType: SCHEDULE_TYPES.DATE,
    processDate: payload.processDate,
  });
  await recordOperation(prisma, actor, {
    module: "processing",
    action: "notice_received",
    targetId: updated.id,
    storeId: updated.storeId,
    description: "收到顾客通知并安排加工日期",
  });
  return updated;
}

export async function reorderProcessingQueue(prisma, actor, payload = {}) {
  const singleId = Number(payload.id);
  const singleOrder = Number(payload.queueOrder);
  const isSingleMove = Number.isInteger(singleId) && singleId > 0;
  const ids = isSingleMove
    ? [singleId]
    : Array.isArray(payload.ids)
      ? payload.ids.map(Number)
      : [];
  if (
    !ids.length ||
    ids.some((id) => !Number.isInteger(id) || id <= 0) ||
    new Set(ids).size !== ids.length
  ) {
    throw new AppError("加工顺序数据不正确", 400);
  }
  const plans = await processingPlanRepository.findMany(prisma, {
    where: { id: { in: ids }, ...scope(actor) },
    select: {
      id: true,
      storeId: true,
      scheduleType: true,
      processDate: true,
      status: true,
    },
  });
  if (plans.length !== ids.length)
    throw new AppError("加工计划不存在或无权操作", 404);
  const first = plans[0];
  const firstDay = first.processDate && dayRange(first.processDate);
  if (
    !firstDay ||
    plans.some(
      (plan) =>
        plan.storeId !== first.storeId ||
        plan.scheduleType !== SCHEDULE_TYPES.DATE ||
        ![PLAN_STATUS.WAITING, PLAN_STATUS.PROCESSING].includes(plan.status) ||
        !plan.processDate ||
        plan.processDate < firstDay.start ||
        plan.processDate >= firstDay.end,
    )
  ) {
    throw new AppError("只能调整同一门店、同一天的有效加工队列", 400);
  }
  const queue = await processingPlanRepository.findMany(prisma, {
    where: {
      storeId: first.storeId,
      deletedAt: null,
      scheduleType: SCHEDULE_TYPES.DATE,
      processDate: { gte: firstDay.start, lt: firstDay.end },
      status: { in: [PLAN_STATUS.WAITING, PLAN_STATUS.PROCESSING] },
    },
    orderBy: [
      { queueOrder: "asc" },
      { priority: "desc" },
      { createdAt: "asc" },
    ],
    select: { id: true },
  });
  const selected = new Set(ids);
  const remaining = queue.filter((plan) => !selected.has(plan.id));
  let insertAt;
  if (isSingleMove) {
    if (!Number.isInteger(singleOrder) || singleOrder <= 0)
      throw new AppError("加工顺序必须为正整数", 400);
    insertAt = Math.min(singleOrder - 1, remaining.length);
  } else {
    const requestedStart = Number(payload.startOrder);
    const currentIndexes = ids
      .map((id) => queue.findIndex((plan) => plan.id === id))
      .filter((index) => index >= 0);
    insertAt =
      Number.isInteger(requestedStart) && requestedStart > 0
        ? Math.min(requestedStart - 1, remaining.length)
        : Math.max(Math.min(...currentIndexes), 0);
  }
  const byId = new Map(queue.map((plan) => [plan.id, plan]));
  const ordered = [
    ...remaining.slice(0, insertAt),
    ...ids.map((id) => byId.get(id)),
    ...remaining.slice(insertAt),
  ];
  const updates = ordered.map((plan, index) =>
    processingPlanRepository.update(prisma, {
      where: { id: plan.id },
      data: { queueOrder: index + 1, updatedBy: Number(actor.id) },
    }),
  );
  if (updates.length) await prisma.$transaction(updates);
  await recordOperation(prisma, actor, {
    module: "processing",
    action: "reorder",
    targetId: ids[0],
    storeId: first.storeId,
    description: "调整加工队列顺序",
  });
  return { ids: ordered.map((plan) => plan.id) };
}

export async function restoreProcessingQueue(prisma, actor, payload = {}) {
  const { start, end } = dayRange(payload.processDate || new Date());
  const plans = await processingPlanRepository.findMany(prisma, {
    where: {
      ...scope(actor, isSuperAdmin(actor) ? payload.storeId : undefined),
      scheduleType: SCHEDULE_TYPES.DATE,
      processDate: { gte: start, lt: end },
      status: { in: [PLAN_STATUS.WAITING, PLAN_STATUS.PROCESSING] },
    },
    orderBy: [
      { priority: "desc" },
      { processDate: "asc" },
      { createdAt: "asc" },
    ],
    select: { id: true, storeId: true },
  });
  if (!plans.length) return { count: 0 };
  const storeIndexes = new Map();
  const updates = plans.map((plan) => {
    const index = (storeIndexes.get(plan.storeId) || 0) + 1;
    storeIndexes.set(plan.storeId, index);
    return processingPlanRepository.update(prisma, {
      where: { id: plan.id },
      data: { queueOrder: index, updatedBy: Number(actor.id) },
    });
  });
  await prisma.$transaction(updates);
  await recordOperation(prisma, actor, {
    module: "processing",
    action: "queue_restore",
    targetId: plans[0]?.id,
    storeId: plans[0]?.storeId,
    description: "恢复默认加工队列",
  });
  return { count: plans.length };
}

export async function getProcessingCalendar(prisma, actor, query = {}) {
  const monthText = String(query.month || "").trim();
  const match = /^(\d{4})-(\d{2})$/.exec(monthText);
  if (!match) throw new AppError("月份格式不正确", 400);
  const start = new Date(Number(match[1]), Number(match[2]) - 1, 1);
  const end = new Date(Number(match[1]), Number(match[2]), 1);
  const plans = await processingPlanRepository.findMany(prisma, {
    where: {
      ...scope(actor, isSuperAdmin(actor) ? query.storeId : undefined),
      scheduleType: SCHEDULE_TYPES.DATE,
      processDate: { gte: start, lt: end },
      status: { not: PLAN_STATUS.CANCELLED },
    },
    select: { processDate: true },
  });
  const counts = {};
  for (const plan of plans) {
    const date = plan.processDate;
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    counts[key] = (counts[key] || 0) + 1;
  }
  return counts;
}

export async function linkPackage(prisma, actor, id, packageIdValue) {
  const plan = await getPlan(prisma, actor, id);
  const packageId = Number(packageIdValue);
  const pkg = await packageRepository.findFirst(prisma, {
    where: { id: packageId, ...scope(actor) },
  });
  if (!pkg) throw new AppError("包裹不存在", 404);
  if (pkg.processingPlanId && pkg.processingPlanId !== plan.id)
    throw new AppError("包裹已关联其他加工计划", 409);
  const updated = await packageRepository.update(prisma, {
    where: { id: pkg.id },
    data: { processingPlanId: plan.id },
  });
  return { plan, package: updated };
}

export async function syncPlanAfterPackagePickup(prisma, planId, updatedBy) {
  const plan = await processingPlanRepository.findUnique(prisma, {
    where: { id: Number(planId) },
    select: { prescriptionId: true, status: true, totalDose: true },
  });
  if (!plan) return;
  if (plan.status !== PLAN_STATUS.PICKED) {
    await processingPlanRepository.update(prisma, {
      where: { id: Number(planId) },
      data: {
        status: PLAN_STATUS.PICKED,
        takenDose: plan.totalDose,
        remainingDose: 0,
        ...(updatedBy ? { updatedBy: Number(updatedBy) } : {}),
      },
    });
  }
  await syncPrescriptionStatus(prisma, plan.prescriptionId, updatedBy);
}
