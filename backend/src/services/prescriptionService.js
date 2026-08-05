import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import {
  DICTIONARY_TYPES,
  PLAN_STATUS,
  PRESCRIPTION_STATUS,
} from "../constants/processing.js";
import { normalizeOptionalPhone, toPositiveInt } from "../utils/validators.js";
import { businessScope, resolveBusinessStoreId } from "./permissionService.js";
import { nextPrescriptionNo } from "./prescriptionNoService.js";
import { describeChanges, recordOperation } from "./operationLogService.js";
import { prescriptionRepository } from "../repositories/prescriptionRepository.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";

export const PRESCRIPTION_ATTACHMENT_MAX_SIZE = 5 * 1024 * 1024;

const PRESCRIPTION_ATTACHMENT_METADATA = {
  id: true,
  originalName: true,
  mimeType: true,
  fileSize: true,
  createdAt: true,
  updatedAt: true,
};

function scope(actor) {
  return { ...businessScope(actor), deletedAt: null };
}

function dayRange(value = new Date()) {
  let start;
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split("-").map(Number);
    start = new Date(year, month - 1, day);
  } else {
    start = new Date(value);
  }
  if (Number.isNaN(start.getTime())) throw new AppError("日期格式不正确", 400);
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { start, end };
}

function text(value, max, label, required = false) {
  const result = String(value || "").trim();
  if (required && !result) throw new AppError(`请输入${label}`, 400);
  if (result.length > max)
    throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return result || null;
}

function decimalOrNull(value, label) {
  if (value === undefined || value === null || value === "") return null;
  const normalized = String(value).trim();
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(normalized))
    throw new AppError(`${label}格式不正确`, 400);
  return normalized;
}

function include() {
  return {
    doctor: true,
    source: true,
    store: { select: { id: true, name: true, code: true } },
    creator: { select: { id: true, nickname: true, phone: true } },
    attachment: { select: PRESCRIPTION_ATTACHMENT_METADATA },
    plans: {
      where: { deletedAt: null },
      include: { processType: true, package: true },
      orderBy: [{ batchNo: "asc" }, { createdAt: "asc" }],
    },
  };
}

function detectPrescriptionMimeType(buffer) {
  if (!buffer?.length) return null;
  const bytes = Buffer.from(buffer);
  if (bytes.subarray(0, 5).toString("ascii") === "%PDF-") return "application/pdf";
  if (bytes.subarray(0, 3).equals(Buffer.from([0xff, 0xd8, 0xff]))) return "image/jpeg";
  if (bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])))
    return "image/png";
  const header = bytes.subarray(0, 6).toString("ascii");
  if (header === "GIF87a" || header === "GIF89a") return "image/gif";
  if (bytes.subarray(0, 2).toString("ascii") === "BM") return "image/bmp";
  if (
    bytes.subarray(0, 4).toString("ascii") === "RIFF" &&
    bytes.subarray(8, 12).toString("ascii") === "WEBP"
  ) {
    return "image/webp";
  }
  return null;
}

function normalizeAttachmentName(value) {
  const name = String(value || "处方文件")
    .replace(/[\\/\0]/g, "_")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .trim();
  return (name || "处方文件").slice(0, 255);
}

function withTotals(item) {
  const plans = item.plans || [];
  return {
    ...item,
    totalDose: plans.reduce((sum, plan) => sum + plan.totalDose, 0),
    takenDose: plans.reduce((sum, plan) => sum + plan.takenDose, 0),
    remainingDose: plans.reduce((sum, plan) => sum + plan.remainingDose, 0),
  };
}

async function validateReferences(prisma, doctorId, sourceId) {
  const [doctor, source] = await Promise.all([
    prisma.doctor.findFirst({
      where: { id: Number(doctorId), deletedAt: null },
    }),
    prisma.dictionary.findFirst({
      where: { id: Number(sourceId), deletedAt: null },
    }),
  ]);
  if (!doctor || doctor.status !== RECORD_STATUS.ENABLED)
    throw new AppError("请选择有效医生", 400);
  if (
    !source ||
    source.status !== RECORD_STATUS.ENABLED ||
    source.type !== DICTIONARY_TYPES.PRESCRIPTION_SOURCE
  ) {
    throw new AppError("请选择有效处方来源", 400);
  }
}

function normalizeData(payload) {
  const isExternal = payload.isExternal ? 1 : 0;
  return {
    customerName: text(payload.customerName, 64, "顾客姓名", true),
    phone: normalizeOptionalPhone(payload.phone),
    doctorId: Number(payload.doctorId),
    sourceId: Number(payload.sourceId),
    isExternal,
    externalHospital: isExternal
      ? text(payload.externalHospital, 150, "医院名称")
      : null,
    externalDoctor: isExternal
      ? text(payload.externalDoctor, 100, "外方医生")
      : null,
    externalRemark: isExternal
      ? text(payload.externalRemark, 500, "外方备注")
      : null,
    remark: text(payload.remark, 500, "备注"),
    totalPrice: decimalOrNull(payload.totalPrice, "总价"),
  };
}

export async function listPrescriptions(prisma, actor, query) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const where = { ...scope(actor) };
  if (query.status !== undefined && query.status !== "")
    where.status = Number(query.status);
  if (query.doctorId) where.doctorId = Number(query.doctorId);
  if (isSuperAdmin(actor) && query.storeId)
    where.storeId = Number(query.storeId);
  if (query.createdDate) {
    const { start, end } = dayRange(String(query.createdDate));
    where.createdAt = { gte: start, lt: end };
  }
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { prescriptionNo: { contains: keyword } },
      { customerName: { contains: keyword } },
      { phone: { contains: keyword } },
      { doctor: { name: { contains: keyword } } },
    ];
  }
  const [list, total] = await Promise.all([
    prescriptionRepository.findMany(prisma, {
      where,
      include: include(),
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prescriptionRepository.count(prisma, { where }),
  ]);
  return {
    list: list.map(withTotals),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function getPrescription(prisma, actor, idValue) {
  const item = await prescriptionRepository.findFirst(prisma, {
    where: { id: Number(idValue), ...scope(actor) },
    include: include(),
  });
  if (!item) throw new AppError("处方不存在", 404);
  return withTotals(item);
}

export async function uploadPrescriptionAttachment(
  prisma,
  actor,
  idValue,
  file,
) {
  const current = await getPrescription(prisma, actor, idValue);
  const buffer = Buffer.from(file?.buffer || []);
  if (!buffer.length) throw new AppError("请选择处方文件", 400);
  if (buffer.length > PRESCRIPTION_ATTACHMENT_MAX_SIZE)
    throw new AppError("处方文件不能超过 5MB", 400);

  const mimeType = detectPrescriptionMimeType(buffer);
  if (!mimeType) throw new AppError("仅支持 JPG、PNG、GIF、WEBP、BMP 图片或 PDF 文件", 400);

  const attachment = await prisma.prescriptionAttachment.upsert({
    where: { prescriptionId: current.id },
    update: {
      originalName: normalizeAttachmentName(file.filename),
      mimeType,
      fileSize: buffer.length,
      data: buffer,
      createdBy: Number(actor.id),
    },
    create: {
      prescriptionId: current.id,
      originalName: normalizeAttachmentName(file.filename),
      mimeType,
      fileSize: buffer.length,
      data: buffer,
      createdBy: Number(actor.id),
    },
    select: PRESCRIPTION_ATTACHMENT_METADATA,
  });
  await recordOperation(prisma, actor, {
    module: "prescription",
    action: "upload_attachment",
    targetId: current.id,
    storeId: current.storeId,
    description: "上传处方原件",
  });
  return attachment;
}

export async function getPrescriptionAttachment(prisma, actor, idValue) {
  const current = await getPrescription(prisma, actor, idValue);
  const attachment = await prisma.prescriptionAttachment.findUnique({
    where: { prescriptionId: current.id },
    select: { ...PRESCRIPTION_ATTACHMENT_METADATA, data: true },
  });
  if (!attachment) throw new AppError("该处方暂无原件", 404);
  return attachment;
}

export async function createPrescriptionRecord(prisma, actor, payload, options = {}) {
  const data = normalizeData(payload);
  await validateReferences(prisma, data.doctorId, data.sourceId);
  data.storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  data.createdBy = Number(actor.id);
  data.status = PRESCRIPTION_STATUS.ACTIVE;
  data.prescriptionNo = await nextPrescriptionNo(prisma, prisma);
  const result = await prescriptionRepository.create(prisma, {
    data,
    include: include(),
  });
  await recordOperation(prisma, actor, {
    module: "prescription",
    action: "create",
    targetId: result.id,
    storeId: result.storeId,
    description: options.description || "新增处方",
  });
  return withTotals(result);
}

export async function createPrescription(prisma, actor, payload) {
  const created = await prisma.$transaction((tx) =>
    createPrescriptionRecord(tx, actor, payload),
  );
  return created;
}

export async function updatePrescription(prisma, actor, idValue, payload) {
  const current = await getPrescription(prisma, actor, idValue);
  if (current.status === PRESCRIPTION_STATUS.COMPLETED)
    throw new AppError("已完成处方不能修改", 409);
  const merged = { ...current, ...payload };
  const data = normalizeData(merged);
  await validateReferences(prisma, data.doctorId, data.sourceId);
  if (payload.status !== undefined) {
    const status = Number(payload.status);
    if (
      ![PRESCRIPTION_STATUS.ACTIVE, PRESCRIPTION_STATUS.CANCELLED].includes(
        status,
      )
    ) {
      throw new AppError("处方状态不正确", 400);
    }
    data.status = status;
  }
  data.updatedBy = Number(actor.id);
  const updated = await prescriptionRepository.update(prisma, {
    where: { id: current.id },
    data,
    include: include(),
  });
  await recordOperation(prisma, actor, {
    module: "prescription",
    action: "update",
    targetId: updated.id,
    storeId: updated.storeId,
    description: describeChanges(current, updated, [
      { key: "customerName", label: "顾客姓名" },
      { key: "phone", label: "手机号" },
      { label: "医生", get: (item) => item?.doctor?.name || item?.doctorId },
      { label: "处方来源", get: (item) => item?.source?.name || item?.sourceId },
      { key: "isExternal", label: "外方", values: { 0: "否", 1: "是" } },
      { key: "externalHospital", label: "外方医院" },
      { key: "externalDoctor", label: "外方医生" },
      { key: "externalRemark", label: "外方备注" },
      { key: "remark", label: "备注" },
      { key: "totalPrice", label: "总价" },
      {
        key: "status",
        label: "状态",
        values: { 0: "进行中", 1: "已完成", 2: "已取消" },
      },
    ]),
  });
  return withTotals(updated);
}

export async function deletePrescription(prisma, actor, idValue) {
  const current = await getPrescription(prisma, actor, idValue);
  if (current.plans.length)
    throw new AppError("处方已有加工计划，不能删除", 409);
  await prescriptionRepository.update(prisma, {
    where: { id: current.id },
    data: { deletedAt: new Date(), deletedBy: actor.id, updatedBy: actor.id },
  });
  await recordOperation(prisma, actor, {
    module: "prescription",
    action: "delete",
    targetId: current.id,
    storeId: current.storeId,
    description: "删除处方",
  });
  return { id: current.id };
}

export async function syncPrescriptionStatus(
  prisma,
  prescriptionId,
  updatedBy,
) {
  if (!prisma?.prescription || typeof prisma.prescription.findUnique !== "function") {
    return;
  }
  const prescription = await prescriptionRepository.findUnique(prisma, {
    where: { id: Number(prescriptionId) },
    select: {
      id: true,
      status: true,
      plans: { where: { deletedAt: null }, select: { status: true } },
    },
  });
  if (!prescription || prescription.status === PRESCRIPTION_STATUS.CANCELLED)
    return;
  const completed =
    prescription.plans.length > 0 &&
    prescription.plans.every((plan) =>
      [PLAN_STATUS.FINISHED, PLAN_STATUS.PICKED].includes(plan.status),
    );
  await prescriptionRepository.update(prisma, {
    where: { id: prescription.id },
    data: {
      status: completed
        ? PRESCRIPTION_STATUS.COMPLETED
        : PRESCRIPTION_STATUS.ACTIVE,
      ...(updatedBy ? { updatedBy: Number(updatedBy) } : {}),
    },
  });
}
