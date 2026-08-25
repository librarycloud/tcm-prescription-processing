import bcrypt from "bcrypt";
import { AppError } from "../utils/appError.js";
import { todayRange } from "../utils/date.js";
import { generateUniquePickupCode } from "../utils/pickupCode.js";
import {
  normalizeOptionalPhone,
  required,
  toPositiveInt,
} from "../utils/validators.js";
import {
  canFilterStore,
  getAccessiblePackage,
  packageAccessWhere,
  resolveCreateStoreId,
} from "./packageAccessService.js";
import { isSuperAdmin, ROLES } from "../constants/roles.js";
import { syncPlanAfterPackagePickup } from "./processingPlanService.js";
import { describeChanges, recordOperation } from "./operationLogService.js";
import { PACKAGE_STATUS, PICKUP_METHOD_VALUES } from "../constants/package.js";
import {
  PLAN_STATUS,
  PRIORITY,
  SCHEDULE_TYPES,
} from "../constants/processing.js";
import { packageRepository } from "../repositories/packageRepository.js";
import { processingPlanRepository } from "../repositories/processingPlanRepository.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import { publishPackageRobotEvent } from "./robotBusinessEventService.js";

function normalizePickupMethod(value) {
  if (value === undefined || value === null || value === "") return null;
  const method = Number(value);
  return PICKUP_METHOD_VALUES.includes(method) ? method : null;
}

function requirePickupMethod(value) {
  const method = normalizePickupMethod(value);
  if (method === null) throw new AppError("请选择取货方式", 400);
  return method;
}

function normalizeExpressFields(payload, pickupMethod) {
  const expressTrackingNo = String(payload.expressTrackingNo || "").trim();
  const expressAddress = String(payload.expressAddress || "").trim();
  if (expressTrackingNo.length > 100)
    throw new AppError("快递单号不能超过 100 个字符", 400);
  if (expressAddress.length > 500)
    throw new AppError("地址不能超过 500 个字符", 400);
  if (![1, 2].includes(pickupMethod)) {
    return { expressTrackingNo: null, expressAddress: null };
  }
  return {
    expressTrackingNo: pickupMethod === 2 ? expressTrackingNo || null : null,
    expressAddress: expressAddress || null,
  };
}

function packageInclude() {
  return {
    creator: { select: { id: true, phone: true, nickname: true } },
    verifier: { select: { id: true, phone: true, nickname: true } },
    modifier: { select: { id: true, phone: true, nickname: true } },
    store: { select: { id: true, name: true, code: true, status: true } },
    processingPlan: {
      select: {
        id: true,
        totalDose: true,
        finishDate: true,
        processType: { select: { id: true, name: true, code: true } },
        prescription: {
          select: {
            id: true,
            prescriptionNo: true,
            customerName: true,
            phone: true,
          },
        },
      },
    },
  };
}

function normalizeStatus(status) {
  if (status === undefined || status === null || status === "") return null;
  const normalized = Number(status);
  if (normalized === PACKAGE_STATUS.READY_PICKUP)
    return PACKAGE_STATUS.READY_PICKUP;
  if (normalized === PACKAGE_STATUS.PICKED) return PACKAGE_STATUS.PICKED;
  return null;
}

function buildWhere(query, actor) {
  const where = packageAccessWhere(actor);

  if (
    canFilterStore(actor) &&
    query.storeId !== undefined &&
    query.storeId !== ""
  ) {
    const storeId = Number(query.storeId);
    if (Number.isInteger(storeId) && storeId > 0) where.storeId = storeId;
  }

  const status = normalizeStatus(query.status);
  if (status !== null) {
    where.status = status;
  }

  if (String(query.source || "").toLowerCase() === "processing") {
    where.processingPlanId = { not: null };
  }

  const dateScope = String(query.dateScope || "").toLowerCase();
  if (dateScope === "today") {
    const { start, end } = todayRange();
    where.createdAt = { gte: start, lt: end };
  } else if (dateScope === "today-picked") {
    const { start, end } = todayRange();
    where.status = PACKAGE_STATUS.PICKED;
    where.pickedAt = { gte: start, lt: end };
  } else if (dateScope === "overdue") {
    const { start } = todayRange();
    where.status = PACKAGE_STATUS.READY_PICKUP;
    where.createdAt = { lt: start };
  } else if (["dashboard", "pickup-workbench"].includes(dateScope)) {
    const { start, end } = todayRange();
    where.AND = [
      {
        OR: [
          { createdAt: { gte: start, lt: end } },
          {
            createdAt: { lt: start },
            status: PACKAGE_STATUS.READY_PICKUP,
          },
        ],
      },
    ];
  }

  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { pickupCode: { contains: keyword } },
      { receiverPhone: { contains: keyword } },
      { receiverName: { contains: keyword } },
      { itemName: { contains: keyword } },
    ];
  }

  return where;
}

function buildOrderBy(query) {
  const sortBy = ["createdAt", "pickedAt"].includes(query.sortBy)
    ? query.sortBy
    : "createdAt";
  const sortOrder = query.sortOrder === "asc" ? "asc" : "desc";
  return { [sortBy]: sortOrder };
}

export async function getStats(prisma, actor, query = {}) {
  const { start, end } = todayRange();
  const tomorrowEnd = new Date(end);
  tomorrowEnd.setDate(tomorrowEnd.getDate() + 1);
  const scope = packageAccessWhere(actor);
  let selectedStore = null;
  if (
    isSuperAdmin(actor) &&
    query.storeId !== undefined &&
    query.storeId !== ""
  ) {
    const storeId = Number(query.storeId);
    if (!Number.isInteger(storeId) || storeId <= 0)
      throw new AppError("门店参数不正确", 400);
    selectedStore = await prisma.store.findFirst({
      where: { id: storeId, deletedAt: null },
      select: { id: true, name: true, code: true, status: true },
    });
    if (!selectedStore) throw new AppError("门店不存在", 404);
    scope.storeId = storeId;
  }
  const activeTodayWhere = {
    ...scope,
    scheduleType: SCHEDULE_TYPES.DATE,
    processDate: { gte: start, lt: end },
  };
  const [
    pendingCount,
    todayAdded,
    todayPicked,
    totalCount,
    prescriptionCount,
    storeCount,
    store,
    waitingCount,
    overdueCount,
    processingCount,
    waitingNoticeCount,
    tomorrowWaitingCount,
    processingPlanTotalCount,
    todayFinished,
    urgentCount,
    firstTask,
  ] = await Promise.all([
    packageRepository.count(prisma, {
      where: { ...scope, status: PACKAGE_STATUS.READY_PICKUP },
    }),
    packageRepository.count(prisma, {
      where: { ...scope, createdAt: { gte: start, lt: end } },
    }),
    packageRepository.count(prisma, {
      where: {
        ...scope,
        status: PACKAGE_STATUS.PICKED,
        pickedAt: { gte: start, lt: end },
      },
    }),
    packageRepository.count(prisma, { where: scope }),
    prisma.prescription.count({ where: scope }),
    isSuperAdmin(actor)
      ? prisma.store.count({ where: { deletedAt: null } })
      : Promise.resolve(null),
    isSuperAdmin(actor) && !selectedStore
      ? Promise.resolve(null)
      : selectedStore
        ? Promise.resolve(selectedStore)
        : prisma.store.findUnique({
            where: { id: Number(actor.storeId) },
            select: { id: true, name: true, code: true, status: true },
          }),
    processingPlanRepository.count(prisma, {
      where: { ...activeTodayWhere, status: PLAN_STATUS.WAITING },
    }),
    processingPlanRepository.count(prisma, {
      where: {
        ...scope,
        status: PLAN_STATUS.WAITING,
        scheduleType: SCHEDULE_TYPES.DATE,
        processDate: { lt: start },
      },
    }),
    processingPlanRepository.count(prisma, {
      where: { ...scope, status: PLAN_STATUS.PROCESSING },
    }),
    processingPlanRepository.count(prisma, {
      where: {
        ...scope,
        status: PLAN_STATUS.WAITING,
        scheduleType: SCHEDULE_TYPES.NOTICE,
      },
    }),
    processingPlanRepository.count(prisma, {
      where: {
        ...scope,
        status: PLAN_STATUS.WAITING,
        scheduleType: SCHEDULE_TYPES.DATE,
        processDate: { gte: end, lt: tomorrowEnd },
      },
    }),
    processingPlanRepository.count(prisma, { where: scope }),
    processingPlanRepository.count(prisma, {
      where: { ...scope, finishDate: { gte: start, lt: end } },
    }),
    processingPlanRepository.count(prisma, {
      where: {
        ...scope,
        priority: { gte: PRIORITY.URGENT },
        status: { in: [PLAN_STATUS.WAITING, PLAN_STATUS.PROCESSING] },
      },
    }),
    processingPlanRepository.findFirst(prisma, {
      where: { ...activeTodayWhere, status: PLAN_STATUS.WAITING },
      orderBy: [
        { processDate: "desc" },
        { queueOrder: "asc" },
        { priority: "desc" },
        { createdAt: "asc" },
      ],
      select: {
        id: true,
        queueOrder: true,
        totalDose: true,
        priority: true,
        prescription: { select: { customerName: true } },
        processType: { select: { name: true } },
      },
    }),
  ]);

  return {
    pendingCount,
    todayAdded,
    todayPicked,
    totalCount,
    prescriptionCount,
    storeCount,
    store,
    waitingCount,
    overdueCount,
    processingCount,
    waitingNoticeCount,
    tomorrowWaitingCount,
    processingPlanTotalCount,
    todayFinished,
    urgentCount,
    firstTask,
  };
}

export async function listPackages(prisma, actor, query) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 10), 100);
  const where = buildWhere(query, actor);
  const orderBy = buildOrderBy(query);

  const [list, total] = await Promise.all([
    packageRepository.findMany(prisma, {
      where,
      include: packageInclude(),
      orderBy,
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    packageRepository.count(prisma, { where }),
  ]);

  return {
    list,
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function getPackageDetail(prisma, actor, id) {
  return getAccessiblePackage(
    prisma,
    actor,
    { id: Number(id) },
    packageInclude(),
  );
}

export async function getPackageByPickupCode(prisma, actor, pickupCodeValue) {
  required(pickupCodeValue, "取货码");
  const pickupCode = String(pickupCodeValue).replace(/\D/g, "");
  return getAccessiblePackage(prisma, actor, { pickupCode }, packageInclude());
}

export async function createPackage(prisma, actor, payload) {
  const {
    itemName,
    itemInfo,
    receiverName,
    receiverPhone,
    newUserName,
    newUserRemark,
  } = payload;
  required(itemName, "物品名称");
  required(receiverName, "收件人");
  const pickupMethod = requirePickupMethod(payload.pickupMethod);
  const expressFields = normalizeExpressFields(payload, pickupMethod);
  const storeId = await resolveCreateStoreId(prisma, actor, payload.storeId);

  const normalizedPhone = normalizeOptionalPhone(receiverPhone, "收件人手机号");
  const userName = String(newUserName || "").trim();
  const userRemark = String(newUserRemark || "").trim();
  if (userName.length > 64)
    throw new AppError("用户姓名不能超过 64 个字符", 400);
  if (userRemark.length > 500)
    throw new AppError("用户备注不能超过 500 个字符", 400);

  const created = await prisma.$transaction(async (tx) => {
    if (normalizedPhone) {
      await tx.user.upsert({
        where: { phone: normalizedPhone },
        update: {},
        create: {
          username: null,
          phone: normalizedPhone,
          password: await bcrypt.hash(
            `admin-package:${normalizedPhone}:${Date.now()}`,
            10,
          ),
          status: RECORD_STATUS.ENABLED,
          name: userName || null,
          remark: userRemark || null,
          createdBy: actor.id,
          updatedBy: actor.id,
        },
        select: { id: true },
      });
    }
    const pickupCode = await generateUniquePickupCode(tx);

    const createdPackage = await packageRepository.create(tx, {
      data: {
        pickupCode,
        storeId,
        itemName: itemName.trim(),
        itemInfo: itemInfo ? itemInfo.trim() : null,
        receiverName: receiverName.trim(),
        receiverPhone: normalizedPhone,
        pickupMethod,
        ...expressFields,
        createdBy: actor.id,
      },
      include: packageInclude(),
    });
    await recordOperation(tx, actor, {
      module: "package",
      action: "create",
      targetId: createdPackage.id,
      storeId,
      description: "新增包裹",
    });
    return createdPackage;
  });
  await publishPackageRobotEvent(prisma, "PACKAGE_CREATED", created, actor);
  return created;
}

export async function updatePackage(prisma, actor, id, payload) {
  const current = await getAccessiblePackage(prisma, actor, { id: Number(id) });
  if (current.status === PACKAGE_STATUS.PICKED)
    throw new AppError("已取包裹不能修改", 400);

  const data = {};
  if (payload.itemName !== undefined) {
    required(payload.itemName, "物品名称");
    data.itemName = payload.itemName.trim();
  }
  if (payload.itemInfo !== undefined) {
    data.itemInfo = payload.itemInfo ? payload.itemInfo.trim() : null;
  }
  if (payload.receiverName !== undefined) {
    required(payload.receiverName, "收件人");
    data.receiverName = payload.receiverName.trim();
  }
  if (payload.receiverPhone !== undefined) {
    data.receiverPhone = normalizeOptionalPhone(
      payload.receiverPhone,
      "收件人手机号",
    );
  }
  if (payload.pickupMethod !== undefined) {
    data.pickupMethod = requirePickupMethod(payload.pickupMethod);
  }

  const nextPickupMethod = data.pickupMethod ?? current.pickupMethod ?? 0;
  Object.assign(
    data,
    normalizeExpressFields(
      {
        expressTrackingNo: payload.expressTrackingNo ?? current.expressTrackingNo,
        expressAddress: payload.expressAddress ?? current.expressAddress,
      },
      nextPickupMethod,
    ),
  );

  data.modifiedBy = actor.id;
  data.modifiedAt = new Date();
  data.updatedBy = actor.id;

  const updated = await packageRepository.update(prisma, {
    where: { id: Number(id) },
    data,
    include: packageInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "package",
    action: "update",
    targetId: updated.id,
    storeId: updated.storeId,
    description: describeChanges(current, updated, [
      { key: "itemName", label: "物品名称" },
      { key: "itemInfo", label: "物品信息" },
      { key: "receiverName", label: "收件人" },
      { key: "receiverPhone", label: "手机号" },
      {
        key: "pickupMethod",
        label: "取货方式",
        values: { 0: "自提", 1: "跑腿", 2: "快递" },
      },
      { key: "expressTrackingNo", label: "\u5feb\u9012\u5355\u53f7" },
      { key: "expressAddress", label: "\u5feb\u9012\u5730\u5740" },
    ]),
  });
  return updated;
}

export async function deletePackage(prisma, actor, id) {
  const packageId = Number(id);
  if (!Number.isInteger(packageId) || packageId <= 0)
    throw new AppError("包裹 ID 不正确", 400);
  const current = await getAccessiblePackage(prisma, actor, { id: packageId });
  if (current.processingPlanId) {
    throw new AppError("加工计划自动生成的包裹不能单独删除", 409);
  }

  await packageRepository.update(prisma, {
    where: { id: packageId },
    data: { deletedAt: new Date(), deletedBy: actor.id, updatedBy: actor.id },
  });
  await recordOperation(prisma, actor, {
    module: "package",
    action: "delete",
    targetId: packageId,
    storeId: current.storeId,
    description: "删除包裹",
  });
  return { id: packageId };
}

export async function verifyPackage(prisma, actor, payload) {
  required(payload.pickupCode, "取货码");
  const pickupCode = String(payload.pickupCode).replace(/\D/g, "");
  const pickupMethod = requirePickupMethod(payload.pickupMethod);
  const expressTrackingNo = String(payload.expressTrackingNo || "").trim();
  if (pickupMethod === 2 && !expressTrackingNo)
    throw new AppError("核销快递包裹时请填写快递单号", 400);
  if (expressTrackingNo.length > 100)
    throw new AppError("快递单号不能超过 100 个字符", 400);

  const current = await getAccessiblePackage(prisma, actor, { pickupCode });
  if (current.status === PACKAGE_STATUS.PICKED)
    throw new AppError("该包裹已核销，不能重复核销", 400);

  const updated = await prisma.$transaction(async (tx) => {
    const claimed = await packageRepository.updateMany(tx, {
      where: { id: current.id, status: PACKAGE_STATUS.READY_PICKUP },
      data: {
        status: PACKAGE_STATUS.PICKED,
        pickedAt: new Date(),
        pickupMethod,
        expressTrackingNo: pickupMethod === 2 ? expressTrackingNo : null,
        verifiedBy: actor.id,
        updatedBy: actor.id,
      },
    });
    if (claimed.count !== 1)
      throw new AppError("该包裹已核销，不能重复核销", 400);
    const updated = await packageRepository.findUnique(tx, {
      where: { id: current.id },
      include: packageInclude(),
    });
    if (updated.processingPlanId) {
      await syncPlanAfterPackagePickup(tx, updated.processingPlanId, actor.id);
    }
    await recordOperation(tx, actor, {
      module: "package",
      action: "verify",
      targetId: updated.id,
      storeId: updated.storeId,
      description: "核销包裹",
    });
    return updated;
  });
  await publishPackageRobotEvent(prisma, "PACKAGE_VERIFIED", updated, actor);
  return updated;
}
