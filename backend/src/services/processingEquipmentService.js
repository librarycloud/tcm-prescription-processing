import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import {
  EQUIPMENT_STATUS,
  EQUIPMENT_TYPE,
  EQUIPMENT_TYPE_NAMES,
} from "../constants/processingWorkflow.js";
import { businessScope, resolveBusinessStoreId } from "./permissionService.js";
import { recordOperation } from "./operationLogService.js";
import {
  newEquipmentScanToken,
  processingEquipmentQrContent,
} from "../utils/processingCode.js";
import { toPositiveInt } from "../utils/validators.js";

const TYPE_VALUES = new Set(Object.values(EQUIPMENT_TYPE));
const STATUS_VALUES = new Set(Object.values(EQUIPMENT_STATUS));

function text(value, max, label, required = false) {
  const result = String(value || "").trim();
  if (required && !result) throw new AppError(`请输入${label}`, 400);
  if (result.length > max) throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return result || null;
}

function equipmentInclude() {
  return {
    store: { select: { id: true, name: true, code: true } },
    currentUsage: {
      include: {
        processingPlan: {
          select: {
            id: true,
            planCode: true,
            prescription: { select: { customerName: true } },
          },
        },
      },
    },
  };
}

function publicEquipment(item) {
  return {
    ...item,
    typeName: EQUIPMENT_TYPE_NAMES[item.type] || item.type,
    qrContent: processingEquipmentQrContent(item.scanToken),
  };
}

function scope(actor, requestedStoreId) {
  return {
    ...businessScope(actor, isSuperAdmin(actor) ? requestedStoreId : undefined),
    deletedAt: null,
  };
}

export async function listProcessingEquipment(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const where = scope(actor, query.storeId);
  if (query.type) {
    if (!TYPE_VALUES.has(String(query.type))) throw new AppError("设备类型不正确", 400);
    where.type = String(query.type);
  }
  if (query.status !== undefined && query.status !== "") {
    const status = Number(query.status);
    if (!STATUS_VALUES.has(status)) throw new AppError("设备状态不正确", 400);
    where.status = status;
  }
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { equipmentNo: { contains: keyword } },
      { name: { contains: keyword } },
    ];
  }
  const [list, total] = await Promise.all([
    prisma.processingEquipment.findMany({
      where,
      include: equipmentInclude(),
      orderBy: [{ type: "asc" }, { equipmentNo: "asc" }, { id: "asc" }],
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.processingEquipment.count({ where }),
  ]);
  return {
    list: list.map(publicEquipment),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
    types: Object.entries(EQUIPMENT_TYPE_NAMES).map(([value, label]) => ({ value, label })),
  };
}

export async function createProcessingEquipment(prisma, actor, payload = {}) {
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  const equipmentNo = text(payload.equipmentNo, 32, "设备编号", true).toUpperCase();
  const name = text(payload.name, 100, "设备名称", true);
  const type = String(payload.type || "");
  if (!TYPE_VALUES.has(type)) throw new AppError("请选择设备类型", 400);
  const status = Number(payload.status ?? EQUIPMENT_STATUS.ENABLED);
  if (!STATUS_VALUES.has(status)) throw new AppError("设备状态不正确", 400);
  try {
    const created = await prisma.processingEquipment.create({
      data: {
        storeId,
        equipmentNo,
        name,
        type,
        status,
        scanToken: newEquipmentScanToken(),
        remark: text(payload.remark, 500, "备注"),
        createdBy: Number(actor.id),
      },
      include: equipmentInclude(),
    });
    await recordOperation(prisma, actor, {
      module: "processing_equipment",
      action: "create",
      targetId: created.id,
      storeId,
      description: `新增${EQUIPMENT_TYPE_NAMES[type]}：${equipmentNo} ${name}`,
    });
    return publicEquipment(created);
  } catch (error) {
    if (error?.code === "P2002") throw new AppError("设备编号已存在", 409);
    throw error;
  }
}

async function getEquipment(prisma, actor, id) {
  const item = await prisma.processingEquipment.findFirst({
    where: { id: Number(id), ...scope(actor) },
    include: equipmentInclude(),
  });
  if (!item) throw new AppError("设备不存在", 404);
  return item;
}

export async function updateProcessingEquipment(prisma, actor, id, payload = {}) {
  const current = await getEquipment(prisma, actor, id);
  const equipmentNo = text(
    payload.equipmentNo ?? current.equipmentNo,
    32,
    "设备编号",
    true,
  ).toUpperCase();
  const name = text(payload.name ?? current.name, 100, "设备名称", true);
  const type = String(payload.type ?? current.type);
  const status = Number(payload.status ?? current.status);
  if (!TYPE_VALUES.has(type)) throw new AppError("请选择设备类型", 400);
  if (!STATUS_VALUES.has(status)) throw new AppError("设备状态不正确", 400);
  if (current.currentUsageId && (type !== current.type || status !== EQUIPMENT_STATUS.ENABLED)) {
    throw new AppError("设备使用中，不能更改类型或停用", 409);
  }
  try {
    const updated = await prisma.processingEquipment.update({
      where: { id: current.id },
      data: {
        equipmentNo,
        name,
        type,
        status,
        remark: text(payload.remark ?? current.remark, 500, "备注"),
        updatedBy: Number(actor.id),
      },
      include: equipmentInclude(),
    });
    await recordOperation(prisma, actor, {
      module: "processing_equipment",
      action: "update",
      targetId: updated.id,
      storeId: updated.storeId,
      description: `更新设备：${equipmentNo} ${name}`,
    });
    return publicEquipment(updated);
  } catch (error) {
    if (error?.code === "P2002") throw new AppError("设备编号已存在", 409);
    throw error;
  }
}

export async function deleteProcessingEquipment(prisma, actor, id) {
  const current = await getEquipment(prisma, actor, id);
  if (current.currentUsageId) throw new AppError("设备使用中，不能删除", 409);
  await prisma.processingEquipment.update({
    where: { id: current.id },
    data: {
      deletedAt: new Date(),
      deletedBy: Number(actor.id),
      status: EQUIPMENT_STATUS.DISABLED,
      updatedBy: Number(actor.id),
    },
  });
  await recordOperation(prisma, actor, {
    module: "processing_equipment",
    action: "delete",
    targetId: current.id,
    storeId: current.storeId,
    description: `删除设备：${current.equipmentNo} ${current.name}`,
  });
  return { id: current.id };
}
