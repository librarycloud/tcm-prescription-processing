import { AppError } from "../utils/appError.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import { DICTIONARY_TYPES } from "../constants/processing.js";
import { describeChanges, recordOperation } from "./operationLogService.js";

const ALLOWED_TYPES = Object.values(DICTIONARY_TYPES);

function positiveId(value, label = "ID") {
  const id = Number(value);
  if (!Number.isInteger(id) || id <= 0)
    throw new AppError(`${label}不正确`, 400);
  return id;
}

function clean(value, max, label) {
  const text = String(value || "").trim();
  if (!text) throw new AppError(`请输入${label}`, 400);
  if (text.length > max)
    throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return text;
}

export async function listDictionaries(prisma, type, includeDisabled = false) {
  const normalizedType = String(type || "");
  if (!ALLOWED_TYPES.includes(normalizedType))
    throw new AppError("字典类型不正确", 400);
  return prisma.dictionary.findMany({
    where: {
      type: normalizedType,
      deletedAt: null,
      ...(includeDisabled ? {} : { status: RECORD_STATUS.ENABLED }),
    },
    orderBy: [{ sort: "asc" }, { id: "asc" }],
  });
}

export async function saveDictionary(prisma, idValue, payload, actor) {
  const current = idValue
    ? await prisma.dictionary.findFirst({
        where: { id: positiveId(idValue), deletedAt: null },
      })
    : null;
  if (idValue && !current) throw new AppError("字典项不存在", 404);
  const type = String(payload.type ?? current?.type ?? "");
  if (!ALLOWED_TYPES.includes(type)) throw new AppError("字典类型不正确", 400);
  const code = clean(
    payload.code ?? current?.code,
    50,
    "字典编码",
  ).toUpperCase();
  if (!/^[A-Z0-9_-]+$/.test(code))
    throw new AppError("字典编码格式不正确", 400);
  const data = {
    type,
    code,
    name: clean(payload.name ?? current?.name, 100, "字典名称"),
    sort: Number.isInteger(Number(payload.sort ?? current?.sort))
      ? Number(payload.sort ?? current?.sort)
      : 0,
    status:
      Number(payload.status ?? current?.status) === RECORD_STATUS.DISABLED
        ? RECORD_STATUS.DISABLED
        : RECORD_STATUS.ENABLED,
  };
  if (idValue) data.updatedBy = actor?.id ? Number(actor.id) : null;
  else data.createdBy = actor?.id ? Number(actor.id) : null;
  const duplicate = await prisma.dictionary.findFirst({
    where: {
      type,
      code,
      ...(idValue ? { id: { not: positiveId(idValue) } } : {}),
    },
    select: { id: true },
  });
  if (duplicate) throw new AppError("字典类型和编码已存在", 409);
  const result = idValue
    ? await prisma.dictionary.update({
        where: { id: positiveId(idValue) },
        data,
      })
    : await prisma.dictionary.create({ data });
  await recordOperation(prisma, actor, {
    module: "dictionary",
    action: idValue ? "update" : "create",
    targetId: result.id,
    description: idValue
      ? describeChanges(current, result, [
          { key: "type", label: "字典类型" },
          { key: "code", label: "编码" },
          { key: "name", label: "名称" },
          { key: "sort", label: "排序" },
          { key: "status", label: "状态", values: { 0: "禁用", 1: "启用" } },
        ])
      : "新增字典项",
  });
  return result;
}

export async function deleteDictionary(prisma, idValue, actor) {
  const id = positiveId(idValue);
  const usage = await prisma.dictionary.findUnique({
    where: { id, deletedAt: null },
    select: {
      _count: { select: { prescriptionSources: true, processingTypes: true } },
    },
  });
  if (!usage) throw new AppError("字典项不存在", 404);
  if (usage._count.prescriptionSources || usage._count.processingTypes) {
    throw new AppError("字典项已被业务数据使用，请改为禁用", 409);
  }
  await prisma.dictionary.update({
    where: { id },
    data: {
      updatedBy: actor?.id ? Number(actor.id) : null,
      deletedAt: new Date(),
      deletedBy: actor?.id ? Number(actor.id) : null,
    },
  });
  await recordOperation(prisma, actor, {
    module: "dictionary",
    action: "delete",
    targetId: id,
    description: "删除字典项",
  });
  return { id };
}

export async function listDoctors(prisma, includeDisabled = false) {
  return prisma.doctor.findMany({
    where: {
      deletedAt: null,
      ...(includeDisabled ? {} : { status: RECORD_STATUS.ENABLED }),
    },
    orderBy: [{ sort: "asc" }, { id: "asc" }],
  });
}

export async function saveDoctor(prisma, idValue, payload, actor) {
  const current = idValue
    ? await prisma.doctor.findFirst({
        where: { id: positiveId(idValue), deletedAt: null },
      })
    : null;
  if (idValue && !current) throw new AppError("医生不存在", 404);
  const data = {
    name: clean(payload.name ?? current?.name, 100, "医生姓名"),
    sort: Number.isInteger(Number(payload.sort ?? current?.sort))
      ? Number(payload.sort ?? current?.sort)
      : 0,
    status:
      Number(payload.status ?? current?.status) === RECORD_STATUS.DISABLED
        ? RECORD_STATUS.DISABLED
        : RECORD_STATUS.ENABLED,
  };
  if (idValue) data.updatedBy = actor?.id ? Number(actor.id) : null;
  else data.createdBy = actor?.id ? Number(actor.id) : null;
  const duplicate = await prisma.doctor.findFirst({
    where: {
      name: data.name,
      deletedAt: null,
      ...(idValue ? { id: { not: positiveId(idValue) } } : {}),
    },
    select: { id: true },
  });
  if (duplicate) throw new AppError("医生姓名已存在", 409);
  const result = idValue
    ? await prisma.doctor.update({ where: { id: positiveId(idValue) }, data })
    : await prisma.doctor.create({ data });
  await recordOperation(prisma, actor, {
    module: "doctor",
    action: idValue ? "update" : "create",
    targetId: result.id,
    description: idValue
      ? describeChanges(current, result, [
          { key: "name", label: "医生姓名" },
          { key: "sort", label: "排序" },
          { key: "status", label: "状态", values: { 0: "停用", 1: "启用" } },
        ])
      : "新增医生",
  });
  return result;
}

export async function deleteDoctor(prisma, idValue, actor) {
  const id = positiveId(idValue);
  const doctor = await prisma.doctor.findUnique({
    where: { id, deletedAt: null },
    select: { _count: { select: { prescriptions: true } } },
  });
  if (!doctor) throw new AppError("医生不存在", 404);
  if (doctor._count.prescriptions)
    throw new AppError("医生已被处方使用，请改为禁用", 409);
  await prisma.doctor.update({
    where: { id },
    data: {
      updatedBy: actor?.id ? Number(actor.id) : null,
      deletedAt: new Date(),
      deletedBy: actor?.id ? Number(actor.id) : null,
    },
  });
  await recordOperation(prisma, actor, {
    module: "doctor",
    action: "delete",
    targetId: id,
    description: "删除医生",
  });
  return { id };
}
