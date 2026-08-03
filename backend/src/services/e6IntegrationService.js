import bcrypt from "bcrypt";
import { createHash, randomBytes } from "node:crypto";
import { AppError } from "../utils/appError.js";
import { normalizeOptionalPhone, toPositiveInt } from "../utils/validators.js";
import { isSuperAdmin } from "../constants/roles.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import {
  DICTIONARY_TYPES,
  PAYMENT_STATUS,
  PRIORITY,
} from "../constants/processing.js";
import {
  E6_CONFIG_STATUS,
  E6_ERROR_CODE,
  E6_IMPORT_STATUS,
  E6_IMPORT_STATUS_VALUES,
  E6_MAPPING_STATUS,
  E6_SOURCE_CODE,
} from "../constants/e6Integration.js";
import { PICKUP_METHOD_VALUES } from "../constants/package.js";
import { assertBusinessStore, businessScope } from "./permissionService.js";
import { describeChanges, recordOperation } from "./operationLogService.js";
import { createPrescriptionRecord } from "./prescriptionService.js";
import { createProcessingPlanRecord } from "./processingPlanService.js";

const CONVERTIBLE_STATUSES = [
  E6_IMPORT_STATUS.IMPORT_PENDING,
  E6_IMPORT_STATUS.IMPORT_MAPPING_REQUIRED,
  E6_IMPORT_STATUS.IMPORT_ERROR,
];

function clean(value, max, label, required = true) {
  const result = String(value ?? "").trim();
  if (required && !result) throw new AppError(`请输入${label}`, 400);
  if (result.length > max)
    throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return result || null;
}

function normalizeDoctorCode(value) {
  return clean(value, 100, "E6医师编码").toUpperCase();
}

function dateOrNull(value, label) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime()))
    throw new AppError(`${label}格式不正确`, 400);
  return date;
}

function decimal(value, label) {
  const normalized = String(value ?? "").trim();
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(normalized))
    throw new AppError(`${label}格式不正确`, 400);
  return normalized;
}

function positiveInteger(value, label) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0)
    throw new AppError(`${label}必须为正整数`, 400);
  return number;
}

const API_KEY_HASH_ROUNDS = 12;

function hashApiKey(value) {
  return bcrypt.hash(String(value), API_KEY_HASH_ROUNDS);
}

function createApiKey() {
  return `e6_${randomBytes(32).toString("base64url")}`;
}

function publicConfig(store, apiKey = null) {
  return {
    id: store?.id || null,
    storeId: store?.id || null,
    enabled: Number(store?.e6Enabled || 0),
    hasApiKey: Boolean(store?.e6ApiKeyHash),
    apiKeyHint: store?.e6ApiKeyHint || null,
    lastUsedAt: store?.e6LastUsedAt || null,
    rotatedAt: store?.e6RotatedAt || null,
    ...(apiKey ? { apiKey } : {}),
  };
}

async function scopedStore(prisma, actor, requestedStoreId) {
  const storeId = isSuperAdmin(actor)
    ? positiveInteger(requestedStoreId, "门店ID")
    : positiveInteger(actor?.storeId, "门店ID");
  const store = await assertBusinessStore(prisma, actor, storeId);
  return store;
}

export async function getE6StoreConfig(prisma, actor, storeIdValue) {
  const store = await scopedStore(prisma, actor, storeIdValue);
  return { store, config: publicConfig(store) };
}

export async function saveE6StoreConfig(
  prisma,
  actor,
  storeIdValue,
  payload = {},
) {
  const store = await scopedStore(prisma, actor, storeIdValue);
  const enabled =
    Number(payload.enabled ?? store.e6Enabled) === E6_CONFIG_STATUS.ENABLED
      ? E6_CONFIG_STATUS.ENABLED
      : E6_CONFIG_STATUS.DISABLED;
  let apiKey = null;
  const shouldRotate =
    Boolean(payload.rotateApiKey) || (enabled && !store.e6ApiKeyHash);
  const data = {
    e6Enabled: enabled,
    updatedBy: Number(actor.id),
  };
  if (shouldRotate) {
    apiKey = createApiKey();
    data.e6ApiKeyHash = await hashApiKey(apiKey);
    data.e6ApiKeyHint = apiKey.slice(-6);
    data.e6RotatedAt = new Date();
  }
  const saved = await prisma.store.update({
    where: { id: store.id },
    data,
  });
  await recordOperation(prisma, actor, {
    module: "e6-integration",
    action: shouldRotate ? "config_rotate_key" : "config_update",
    targetId: store.id,
    storeId: store.id,
    description: describeChanges(
      store,
      saved,
      [
        {
          key: "e6Enabled",
          label: "启用状态",
          values: { 0: "停用", 1: "启用" },
        },
      ],
      shouldRotate ? "重置门店E6 API Key" : "更新门店E6配置",
    ),
  });
  return { store, config: publicConfig(saved, apiKey) };
}

function mappingInclude() {
  return {
    store: { select: { id: true, name: true, code: true } },
    doctor: { select: { id: true, name: true, status: true } },
  };
}

export async function listE6DoctorMappings(prisma, actor, query = {}) {
  const where = businessScope(actor, isSuperAdmin(actor) ? query.storeId : undefined);
  if (query.status !== undefined && query.status !== "") {
    const status = Number(query.status);
    if (!Object.values(E6_MAPPING_STATUS).includes(status))
      throw new AppError("映射状态不正确", 400);
    where.status = status;
  }
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { e6DoctorCode: { contains: keyword } },
      { doctor: { name: { contains: keyword } } },
    ];
  }
  return prisma.e6DoctorMapping.findMany({
    where,
    include: mappingInclude(),
    orderBy: [{ storeId: "asc" }, { e6DoctorCode: "asc" }],
  });
}

async function validateMappingData(prisma, actor, payload, current = null) {
  const store = await scopedStore(
    prisma,
    actor,
    payload.storeId ?? current?.storeId,
  );
  const doctorId = positiveInteger(payload.doctorId ?? current?.doctorId, "医生ID");
  const doctor = await prisma.doctor.findFirst({
    where: { id: doctorId, status: RECORD_STATUS.ENABLED, deletedAt: null },
  });
  if (!doctor) throw new AppError("医生不存在或已停用", 400);
  const status =
    Number(payload.status ?? current?.status) === E6_MAPPING_STATUS.DISABLED
      ? E6_MAPPING_STATUS.DISABLED
      : E6_MAPPING_STATUS.ENABLED;
  return {
    storeId: store.id,
    e6DoctorCode: normalizeDoctorCode(
      payload.e6DoctorCode ?? current?.e6DoctorCode,
    ),
    doctorId,
    status,
  };
}

export async function saveE6DoctorMapping(
  prisma,
  actor,
  idValue,
  payload = {},
) {
  const id = idValue ? positiveInteger(idValue, "映射ID") : null;
  const current = id
    ? await prisma.e6DoctorMapping.findUnique({ where: { id } })
    : null;
  if (id && !current) throw new AppError("医生映射不存在", 404);
  if (current) await scopedStore(prisma, actor, current.storeId);
  const data = await validateMappingData(prisma, actor, payload, current);
  const duplicate = await prisma.e6DoctorMapping.findFirst({
    where: {
      storeId: data.storeId,
      e6DoctorCode: data.e6DoctorCode,
      ...(id ? { id: { not: id } } : {}),
    },
    select: { id: true },
  });
  if (duplicate) throw new AppError("该门店的E6医师编码已配置", 409);
  if (id) data.updatedBy = Number(actor.id);
  else data.createdBy = Number(actor.id);
  const saved = id
    ? await prisma.e6DoctorMapping.update({ where: { id }, data, include: mappingInclude() })
    : await prisma.e6DoctorMapping.create({ data, include: mappingInclude() });
  if (saved.status === E6_MAPPING_STATUS.ENABLED) {
    await prisma.e6Import.updateMany({
      where: {
        storeId: saved.storeId,
        e6DoctorCode: saved.e6DoctorCode,
        status: E6_IMPORT_STATUS.IMPORT_MAPPING_REQUIRED,
      },
      data: {
        status: E6_IMPORT_STATUS.IMPORT_PENDING,
        errorCode: null,
        errorMessage: null,
      },
    });
  }
  await recordOperation(prisma, actor, {
    module: "e6-integration",
    action: id ? "doctor_mapping_update" : "doctor_mapping_create",
    targetId: saved.id,
    storeId: saved.storeId,
    description: id
      ? describeChanges(current, saved, [
          { key: "e6DoctorCode", label: "E6医师编码" },
          { key: "doctorId", label: "系统医生" },
          { key: "status", label: "状态", values: { 0: "停用", 1: "启用" } },
        ])
      : `新增E6医师映射：${saved.e6DoctorCode} → ${saved.doctor.name}`,
  });
  return saved;
}

export async function deleteE6DoctorMapping(prisma, actor, idValue) {
  const id = positiveInteger(idValue, "映射ID");
  const current = await prisma.e6DoctorMapping.findUnique({
    where: { id },
    include: mappingInclude(),
  });
  if (!current) throw new AppError("医生映射不存在", 404);
  await scopedStore(prisma, actor, current.storeId);
  await prisma.$transaction(async (tx) => {
    await tx.e6DoctorMapping.delete({ where: { id } });
    await tx.e6Import.updateMany({
      where: {
        storeId: current.storeId,
        e6DoctorCode: current.e6DoctorCode,
        status: E6_IMPORT_STATUS.IMPORT_PENDING,
      },
      data: {
        status: E6_IMPORT_STATUS.IMPORT_MAPPING_REQUIRED,
        errorCode: E6_ERROR_CODE.DOCTOR_MAPPING_REQUIRED,
        errorMessage: "E6医师编码尚未映射系统医生",
      },
    });
    await recordOperation(tx, actor, {
      module: "e6-integration",
      action: "doctor_mapping_delete",
      targetId: current.id,
      storeId: current.storeId,
      description: `删除E6医师映射：${current.e6DoctorCode} → ${current.doctor.name}`,
    });
  });
  return { id };
}

function normalizeImportPayload(payload = {}) {
  const storeCode = clean(payload.storeCode, 50, "门店编码").toUpperCase();
  const normalized = {
    storeCode,
    externalOrderNo: clean(payload.externalOrderNo, 100, "E6原始订单号"),
    customerName: clean(payload.customerName, 64, "顾客姓名"),
    phone: normalizeOptionalPhone(payload.phone),
    e6DoctorCode: normalizeDoctorCode(payload.e6DoctorCode),
    totalPrice: decimal(payload.totalPrice, "总价"),
    doseCount: positiveInteger(payload.doseCount, "剂数"),
    remark: clean(payload.remark, 500, "备注", false),
    sourceCreatedAt: dateOrNull(payload.sourceCreatedAt, "E6创建时间"),
    sourceUpdatedAt: dateOrNull(payload.sourceUpdatedAt, "E6更新时间"),
  };
  const canonical = {
    ...normalized,
    sourceCreatedAt: normalized.sourceCreatedAt?.toISOString() || null,
    sourceUpdatedAt: normalized.sourceUpdatedAt?.toISOString() || null,
  };
  return {
    ...normalized,
    rawPayload: JSON.stringify(payload),
    payloadHash: createHash("sha256")
      .update(JSON.stringify(canonical))
      .digest("hex"),
  };
}

async function authenticateStore(prisma, storeCode, apiKey) {
  const store = await prisma.store.findFirst({
    where: {
      code: storeCode,
      status: RECORD_STATUS.ENABLED,
      deletedAt: null,
    },
  });
  const matches = store?.e6ApiKeyHash
    ? await bcrypt.compare(String(apiKey || ""), store.e6ApiKeyHash)
    : false;
  if (
    !store ||
    store.e6Enabled !== E6_CONFIG_STATUS.ENABLED ||
    !matches
  ) {
    throw new AppError("E6接入凭证无效", 401);
  }
  return store;
}

async function activeDoctorMapping(prisma, storeId, e6DoctorCode) {
  return prisma.e6DoctorMapping.findFirst({
    where: {
      storeId,
      e6DoctorCode,
      status: E6_MAPPING_STATUS.ENABLED,
      doctor: { status: RECORD_STATUS.ENABLED, deletedAt: null },
    },
    include: { doctor: true },
  });
}

function importResult(record, duplicate) {
  return {
    importId: record.id,
    externalOrderNo: record.externalOrderNo,
    status: record.status,
    duplicate,
  };
}

async function persistImport(prisma, store, normalized, actor) {
  const mapping = await activeDoctorMapping(
    prisma,
    store.id,
    normalized.e6DoctorCode,
  );
  const desiredStatus = mapping
    ? E6_IMPORT_STATUS.IMPORT_PENDING
    : E6_IMPORT_STATUS.IMPORT_MAPPING_REQUIRED;
  const desiredError = mapping
    ? { errorCode: null, errorMessage: null }
    : {
        errorCode: E6_ERROR_CODE.DOCTOR_MAPPING_REQUIRED,
        errorMessage: "E6医师编码尚未映射系统医生",
      };
  return prisma.$transaction(async (tx) => {
    const existing = await tx.e6Import.findUnique({
      where: {
        storeId_externalOrderNo: {
          storeId: store.id,
          externalOrderNo: normalized.externalOrderNo,
        },
      },
    });
    const baseData = {
      customerName: normalized.customerName,
      phone: normalized.phone,
      e6DoctorCode: normalized.e6DoctorCode,
      totalPrice: normalized.totalPrice,
      doseCount: normalized.doseCount,
      remark: normalized.remark,
      rawPayload: normalized.rawPayload,
      payloadHash: normalized.payloadHash,
      sourceCreatedAt: normalized.sourceCreatedAt,
      sourceUpdatedAt: normalized.sourceUpdatedAt,
      lastSyncedAt: new Date(),
    };
    let record;
    let action;
    if (!existing) {
      record = await tx.e6Import.create({
        data: {
          ...baseData,
          storeId: store.id,
          externalOrderNo: normalized.externalOrderNo,
          status: desiredStatus,
          ...desiredError,
        },
      });
      action = "import_receive";
    } else {
      const changed = existing.payloadHash !== normalized.payloadHash;
      const converted = existing.prescriptionId != null;
      const terminal = [
        E6_IMPORT_STATUS.IMPORT_REJECTED,
        E6_IMPORT_STATUS.IMPORT_CANCELLED,
      ].includes(existing.status);
      const status = converted && changed
        ? E6_IMPORT_STATUS.IMPORT_CONFLICT
        : converted || terminal
          ? existing.status
          : desiredStatus;
      const error = converted && changed
        ? {
            errorCode: E6_ERROR_CODE.DATA_CHANGED_AFTER_CONVERSION,
            errorMessage: "E6订单在生成处方后发生变化，请人工核对",
          }
        : converted || terminal
          ? { errorCode: existing.errorCode, errorMessage: existing.errorMessage }
          : desiredError;
      record = await tx.e6Import.update({
        where: { id: existing.id },
        data: {
          ...baseData,
          status,
          ...error,
          syncCount: { increment: 1 },
        },
      });
      action = changed ? "import_update" : "import_duplicate";
    }
    await tx.store.update({
      where: { id: store.id },
      data: { e6LastUsedAt: new Date() },
    });
    await recordOperation(tx, actor, {
      module: "e6-integration",
      action,
      targetId: record.id,
      storeId: store.id,
      description: `E6订单 ${record.externalOrderNo}，顾客 ${record.customerName}，医师编码 ${record.e6DoctorCode}`,
    });
    return importResult(record, Boolean(existing));
  });
}

export async function receiveE6Prescription(
  prisma,
  payload,
  apiKey,
  requestMeta = {},
) {
  const normalized = normalizeImportPayload(payload);
  const store = await authenticateStore(prisma, normalized.storeCode, apiKey);
  const actor = {
    nickname: `E6:${store.code}`,
    storeId: store.id,
    ip: requestMeta.ip || null,
    userAgent: requestMeta.userAgent || null,
  };
  try {
    return await persistImport(prisma, store, normalized, actor);
  } catch (error) {
    if (error?.code === "P2002") {
      return persistImport(prisma, store, normalized, actor);
    }
    throw error;
  }
}

function importInclude(includeRaw = false) {
  return {
    store: { select: { id: true, name: true, code: true } },
    prescription: { select: { id: true, prescriptionNo: true, status: true } },
    processingPlan: { select: { id: true, status: true } },
    ...(includeRaw ? {} : {}),
  };
}

async function attachMappedDoctors(prisma, list) {
  if (!list.length) return list;
  const pairs = list.map((item) => ({
    storeId: item.storeId,
    e6DoctorCode: item.e6DoctorCode,
  }));
  const mappings = await prisma.e6DoctorMapping.findMany({
    where: { OR: pairs, status: E6_MAPPING_STATUS.ENABLED },
    include: { doctor: { select: { id: true, name: true, status: true } } },
  });
  const byKey = new Map(
    mappings.map((item) => [`${item.storeId}:${item.e6DoctorCode}`, item]),
  );
  return list.map((item) => ({
    ...item,
    doctorMapping: byKey.get(`${item.storeId}:${item.e6DoctorCode}`) || null,
  }));
}

export async function listE6Imports(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const where = businessScope(actor, isSuperAdmin(actor) ? query.storeId : undefined);
  if (query.status !== undefined && query.status !== "") {
    const status = Number(query.status);
    if (!E6_IMPORT_STATUS_VALUES.includes(status))
      throw new AppError("导入状态不正确", 400);
    where.status = status;
  }
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { externalOrderNo: { contains: keyword } },
      { customerName: { contains: keyword } },
      { phone: { contains: keyword } },
      { e6DoctorCode: { contains: keyword } },
    ];
  }
  const [list, total] = await Promise.all([
    prisma.e6Import.findMany({
      where,
      include: importInclude(),
      orderBy: [{ syncedAt: "desc" }, { id: "desc" }],
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.e6Import.count({ where }),
  ]);
  return {
    list: await attachMappedDoctors(prisma, list),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

async function findScopedImport(prisma, actor, idValue) {
  const id = positiveInteger(idValue, "导入记录ID");
  const item = await prisma.e6Import.findFirst({
    where: { id, ...businessScope(actor) },
    include: importInclude(true),
  });
  if (!item) throw new AppError("E6导入记录不存在", 404);
  return item;
}

export async function getE6Import(prisma, actor, idValue) {
  const item = await findScopedImport(prisma, actor, idValue);
  const [result] = await attachMappedDoctors(prisma, [item]);
  try {
    return { ...result, rawPayload: JSON.parse(result.rawPayload) };
  } catch {
    return result;
  }
}

function conversionError(message, status, code) {
  const error = new AppError(message, 400);
  error.e6ImportStatus = status;
  error.e6ErrorCode = code;
  return error;
}

export async function confirmE6Import(prisma, actor, idValue, payload = {}) {
  const current = await findScopedImport(prisma, actor, idValue);
  if (current.prescriptionId || current.status === E6_IMPORT_STATUS.IMPORT_CONVERTED)
    throw new AppError("该E6订单已生成处方", 409);
  if (!CONVERTIBLE_STATUSES.includes(current.status))
    throw new AppError("当前导入状态不能确认", 409);
  try {
    const result = await prisma.$transaction(async (tx) => {
      const claimed = await tx.e6Import.updateMany({
        where: {
          id: current.id,
          prescriptionId: null,
          status: { in: CONVERTIBLE_STATUSES },
        },
        data: {
          status: E6_IMPORT_STATUS.IMPORT_PROCESSING,
          errorCode: null,
          errorMessage: null,
        },
      });
      if (claimed.count !== 1)
        throw new AppError("该导入记录正在处理或已完成", 409);
      const item = await tx.e6Import.findUnique({ where: { id: current.id } });
      const mapping = await activeDoctorMapping(
        tx,
        item.storeId,
        item.e6DoctorCode,
      );
      if (!mapping) {
        throw conversionError(
          "请先配置该门店的E6医师映射",
          E6_IMPORT_STATUS.IMPORT_MAPPING_REQUIRED,
          E6_ERROR_CODE.DOCTOR_MAPPING_REQUIRED,
        );
      }
      const source = await tx.dictionary.findFirst({
        where: {
          type: DICTIONARY_TYPES.PRESCRIPTION_SOURCE,
          code: E6_SOURCE_CODE,
          status: RECORD_STATUS.ENABLED,
          deletedAt: null,
        },
      });
      if (!source)
        throw conversionError(
          "E6处方来源字典不存在或已停用",
          E6_IMPORT_STATUS.IMPORT_ERROR,
          E6_ERROR_CODE.CONVERSION_FAILED,
        );
      const prescription = await createPrescriptionRecord(
        tx,
        actor,
        {
          customerName: item.customerName,
          phone: item.phone,
          doctorId: mapping.doctorId,
          sourceId: source.id,
          storeId: item.storeId,
          totalPrice: item.totalPrice,
          remark: item.remark,
        },
        { description: `确认E6订单 ${item.externalOrderNo} 并生成处方` },
      );
      const pickupMethod = Number(payload.pickupMethod);
      if (!PICKUP_METHOD_VALUES.includes(pickupMethod))
        throw new AppError("请选择取货方式", 400);
      const plan = await createProcessingPlanRecord(
        tx,
        actor,
        {
          prescriptionId: prescription.id,
          batchNo: 1,
          processTypeId: payload.processTypeId,
          totalDose: item.doseCount,
          bagCount: payload.bagCount,
          volumeMl: payload.volumeMl,
          usageMethod: payload.usageMethod,
          scheduleType: payload.scheduleType,
          processDate: payload.processDate,
          priority: payload.priority ?? PRIORITY.NORMAL,
          notifyType: payload.notifyType,
          paymentStatus: payload.paymentStatus ?? PAYMENT_STATUS.PAID,
           pickupMethod,
           expressAddress: payload.expressAddress,
           processRemark: payload.processRemark,
          remark: item.remark,
        },
        { description: `由E6订单 ${item.externalOrderNo} 生成加工计划` },
      );
      const converted = await tx.e6Import.update({
        where: { id: item.id },
        data: {
          status: E6_IMPORT_STATUS.IMPORT_CONVERTED,
          prescriptionId: prescription.id,
          processingPlanId: plan.id,
          confirmedBy: Number(actor.id),
          confirmedAt: new Date(),
          errorCode: null,
          errorMessage: null,
        },
        include: importInclude(),
      });
      await recordOperation(tx, actor, {
        module: "e6-integration",
        action: "import_confirm",
        targetId: converted.id,
        storeId: converted.storeId,
        description: `确认E6订单 ${converted.externalOrderNo}，生成处方 ${prescription.prescriptionNo}`,
      });
      return converted;
    });
    const [withMapping] = await attachMappedDoctors(prisma, [result]);
    return withMapping;
  } catch (error) {
    const status = error.e6ImportStatus ?? E6_IMPORT_STATUS.IMPORT_ERROR;
    const errorCode = error.e6ErrorCode ?? E6_ERROR_CODE.CONVERSION_FAILED;
    const message = String(error.message || "E6导入转换失败").slice(0, 500);
    const failed = await prisma.e6Import.updateMany({
      where: { id: current.id, prescriptionId: null, status: { in: CONVERTIBLE_STATUSES } },
      data: { status, errorCode, errorMessage: message },
    });
    if (failed.count) {
      await recordOperation(prisma, actor, {
        module: "e6-integration",
        action: "import_convert_failed",
        targetId: current.id,
        storeId: current.storeId,
        description: `E6订单 ${current.externalOrderNo} 转换失败：${message}`,
      });
    }
    throw error;
  }
}

export async function rejectE6Import(prisma, actor, idValue, payload = {}) {
  const current = await findScopedImport(prisma, actor, idValue);
  if (current.prescriptionId)
    throw new AppError("已生成处方的E6订单不能驳回", 409);
  if (!CONVERTIBLE_STATUSES.includes(current.status))
    throw new AppError("当前导入状态不能驳回", 409);
  const reason = clean(payload.reason, 500, "驳回原因");
  const updated = await prisma.e6Import.update({
    where: { id: current.id },
    data: {
      status: E6_IMPORT_STATUS.IMPORT_REJECTED,
      rejectedBy: Number(actor.id),
      rejectedAt: new Date(),
      rejectReason: reason,
      errorCode: null,
      errorMessage: null,
    },
    include: importInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "e6-integration",
    action: "import_reject",
    targetId: updated.id,
    storeId: updated.storeId,
    description: `驳回E6订单 ${updated.externalOrderNo}：${reason}`,
  });
  return updated;
}

export async function revalidateE6Import(prisma, actor, idValue) {
  const current = await findScopedImport(prisma, actor, idValue);
  if (current.prescriptionId)
    throw new AppError("已生成处方的E6订单无需重新校验", 409);
  if (!CONVERTIBLE_STATUSES.includes(current.status))
    throw new AppError("当前导入状态不能重新校验", 409);
  const mapping = await activeDoctorMapping(
    prisma,
    current.storeId,
    current.e6DoctorCode,
  );
  const status = mapping
    ? E6_IMPORT_STATUS.IMPORT_PENDING
    : E6_IMPORT_STATUS.IMPORT_MAPPING_REQUIRED;
  const updated = await prisma.e6Import.update({
    where: { id: current.id },
    data: {
      status,
      errorCode: mapping ? null : E6_ERROR_CODE.DOCTOR_MAPPING_REQUIRED,
      errorMessage: mapping ? null : "E6医师编码尚未映射系统医生",
    },
    include: importInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "e6-integration",
    action: "import_revalidate",
    targetId: updated.id,
    storeId: updated.storeId,
    description: `重新校验E6订单 ${updated.externalOrderNo}`,
  });
  const [withMapping] = await attachMappedDoctors(prisma, [updated]);
  return withMapping;
}
