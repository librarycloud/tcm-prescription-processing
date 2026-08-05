import {
  PRINT_FIELD_DEFINITIONS,
  PRINT_FONT_FAMILIES,
  PRINT_TEMPLATE_TYPES,
  PRINT_TEMPLATE_TYPE_NAMES,
  defaultPackagingFields,
  defaultPickupFields,
  defaultProcessingFields,
  defaultEquipmentFields,
} from "../constants/printTemplate.js";
import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import {
  assertBusinessStore,
  businessScope,
  resolveBusinessStoreId,
} from "./permissionService.js";

const TEMPLATE_TYPE_VALUES = Object.values(PRINT_TEMPLATE_TYPES);
const ALIGN_VALUES = ["left", "center", "right"];
const FONT_FAMILY_VALUES = new Set(
  PRINT_FONT_FAMILIES.map((item) => item.value),
);
const CUSTOM_FIELD_ID_PATTERN = /^custom_[a-zA-Z0-9_-]{1,64}$/;

const DEFAULT_TEMPLATES = [
  {
    templateType: PRINT_TEMPLATE_TYPES.PACKAGE_PICKUP,
    name: "取货标签（横版）",
    widthMm: 70,
    heightMm: 50,
    fields: defaultPickupFields("landscape"),
    isDefault: 1,
  },
  {
    templateType: PRINT_TEMPLATE_TYPES.PACKAGE_PICKUP,
    name: "取货标签（竖版）",
    widthMm: 50,
    heightMm: 70,
    fields: defaultPickupFields("portrait"),
    isDefault: 0,
  },
  {
    templateType: PRINT_TEMPLATE_TYPES.PROCESSING,
    name: "加工标签（70×50扫码版）",
    widthMm: 70,
    heightMm: 50,
    fields: defaultProcessingFields(),
    isDefault: 1,
  },
  {
    templateType: PRINT_TEMPLATE_TYPES.PROCESSING,
    name: "加工标签（75×50热敏裁切顶端补偿版）",
    widthMm: 75,
    heightMm: 50,
    fields: defaultProcessingFields("thermal-75"),
    isDefault: 0,
    seedWhenTypeExists: true,
  },
  {
    templateType: PRINT_TEMPLATE_TYPES.EQUIPMENT,
    name: "设备标签（70×50扫码版）",
    widthMm: 70,
    heightMm: 50,
    fields: defaultEquipmentFields(),
    isDefault: 1,
  },
  {
    templateType: PRINT_TEMPLATE_TYPES.PROCESSING,
    name: "加工标签（50×70竖版）",
    widthMm: 50,
    heightMm: 70,
    fields: defaultProcessingFields("portrait"),
    isDefault: 0,
  },
  {
    templateType: PRINT_TEMPLATE_TYPES.PACKAGING,
    name: "包装标签（标准）",
    widthMm: 70,
    heightMm: 42,
    fields: defaultPackagingFields(),
    isDefault: 1,
  },
  {
    templateType: PRINT_TEMPLATE_TYPES.PACKAGING,
    name: "包装标签（50×70竖版）",
    widthMm: 50,
    heightMm: 70,
    fields: defaultPackagingFields("portrait"),
    isDefault: 0,
  },
];

function requireType(value) {
  const type = String(value || "").trim();
  if (!TEMPLATE_TYPE_VALUES.includes(type))
    throw new AppError("不支持的打印模板类型", 400);
  return type;
}

function text(value, max, label) {
  const result = String(value || "").trim();
  if (!result || result.length > max)
    throw new AppError(`${label}不能为空且不能超过${max}个字符`, 400);
  return result;
}

function decimal(value, label) {
  const result = Number(value);
  if (!Number.isFinite(result) || result <= 0 || result > 300) {
    throw new AppError(`${label}必须在0到300之间`, 400);
  }
  return Math.round(result * 100) / 100;
}

function parseLayout(value) {
  if (Array.isArray(value)) return { fields: value };
  if (value && typeof value === "object") return value;
  try {
    return JSON.parse(String(value || "{}"));
  } catch {
    throw new AppError("打印模板布局数据格式不正确", 400);
  }
}

function normalizeFields(type, fields, widthMm, heightMm) {
  const definitions = PRINT_FIELD_DEFINITIONS[type] || [];
  const definitionMap = new Map(definitions.map((item) => [item.id, item]));
  if (!Array.isArray(fields) || !fields.length || fields.length > 30) {
    throw new AppError("打印模板至少需要一个字段", 400);
  }
  const seen = new Set();
  return fields.map((item) => {
    const fieldId = String(item?.id || "");
    const isCustom = CUSTOM_FIELD_ID_PATTERN.test(fieldId);
    const field = isCustom
      ? { id: fieldId, label: "自定义文本" }
      : definitionMap.get(fieldId);
    if (!field || seen.has(field.id))
      throw new AppError("打印模板字段不正确或重复", 400);
    seen.add(field.id);
    const customText = isCustom ? String(item.text || "").trim() : "";
    if (isCustom && (!customText || customText.length > 200)) {
      throw new AppError("自定义文本不能为空且不能超过200个字符", 400);
    }
    const x = Number(item.x);
    const y = Number(item.y);
    const fieldWidth = Number(item.width);
    const fieldHeight = Number(item.height);
    const fontSize = Number(item.fontSize || 3);
    if (
      ![x, y, fieldWidth, fieldHeight].every(Number.isFinite) ||
      x < 0 ||
      y < 0 ||
      fieldWidth <= 0 ||
      fieldHeight <= 0
    ) {
      throw new AppError(`字段“${field.label}”的位置或尺寸不正确`, 400);
    }
    if (x + fieldWidth > widthMm || y + fieldHeight > heightMm) {
      throw new AppError(`字段“${field.label}”超出纸张范围`, 400);
    }
    if (!Number.isFinite(fontSize) || fontSize <= 0 || fontSize > 50) {
      throw new AppError(`字段“${field.label}”字号不正确`, 400);
    }
    return {
      id: field.id,
      x: Math.round(x * 100) / 100,
      y: Math.round(y * 100) / 100,
      width: Math.round(fieldWidth * 100) / 100,
      height: Math.round(fieldHeight * 100) / 100,
      fontSize: Math.round(fontSize * 100) / 100,
      fontFamily: FONT_FAMILY_VALUES.has(item.fontFamily)
        ? item.fontFamily
        : "system",
      align: ALIGN_VALUES.includes(item.align) ? item.align : "left",
      bold: Boolean(item.bold),
      wrap: Boolean(item.wrap),
      visible: item.visible !== false,
      ...(isCustom ? { text: customText } : {}),
    };
  });
}

function publicTemplate(item) {
  let layout;
  try {
    layout = parseLayout(item.layoutJson);
  } catch {
    layout = { fields: [] };
  }
  return {
    id: item.id,
    storeId: item.storeId,
    store: item.store || null,
    templateType: item.templateType,
    templateTypeName:
      PRINT_TEMPLATE_TYPE_NAMES[item.templateType] || item.templateType,
    name: item.name,
    widthMm: Number(item.widthMm),
    heightMm: Number(item.heightMm),
    fields: layout.fields || [],
    enabled: item.enabled === 1,
    isDefault: item.isDefault === 1,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  };
}

function throwPrintTemplateUniqueError(error) {
  if (error?.code !== "P2002") throw error;
  const target = Array.isArray(error?.meta?.target)
    ? error.meta.target.join(",")
    : String(error?.meta?.target || "");
  if (
    target.includes("default_scope") ||
    target.includes("one_default_per_scope")
  )
    throw new AppError("当前门店和模板类型已存在默认模板，请刷新后重试", 409);
  throw new AppError("同类型模板名称已存在", 409);
}

function defaultScope(storeId, templateType, isDefault) {
  return isDefault ? `${storeId}:${templateType}` : null;
}

async function ensureDefaults(prisma, storeId) {
  if (!storeId) return;
  const existing = await prisma.printTemplate.findMany({
    where: { storeId, templateType: { in: TEMPLATE_TYPE_VALUES } },
    select: { templateType: true, widthMm: true, heightMm: true },
  });
  const missing = DEFAULT_TEMPLATES.filter((template) => {
    const sameType = existing.filter((item) => item.templateType === template.templateType);
    if (!sameType.length) return true;
    if (!template.seedWhenTypeExists) return false;
    return !sameType.some(
      (item) =>
        Number(item.widthMm) === template.widthMm &&
        Number(item.heightMm) === template.heightMm,
    );
  });
  if (!missing.length) return;
  await prisma.printTemplate.createMany({
    data: missing.map((template) => ({
      templateType: template.templateType,
      storeId,
      name: template.name,
      widthMm: template.widthMm,
      heightMm: template.heightMm,
      layoutJson: JSON.stringify({ version: 1, fields: template.fields }),
      enabled: 1,
      isDefault: template.isDefault,
      defaultScope: defaultScope(
        storeId,
        template.templateType,
        template.isDefault,
      ),
    })),
  });
}

export async function getPrintTemplateSettings(prisma, actor, query = {}) {
  const requestedStoreId = query.storeId;
  const where = businessScope(actor, requestedStoreId);
  const selectedStoreId = where.storeId ? Number(where.storeId) : null;
  if (selectedStoreId)
    await assertBusinessStore(prisma, actor, selectedStoreId);
  await ensureDefaults(prisma, selectedStoreId);
  const type = query.type ? requireType(query.type) : null;
  const templates = await prisma.printTemplate.findMany({
    where: {
      ...where,
      ...(type ? { templateType: type } : {}),
      ...(query.all === "1" ? {} : { enabled: 1 }),
    },
    include: { store: { select: { id: true, name: true, code: true } } },
    orderBy: [
      { storeId: "asc" },
      { templateType: "asc" },
      { isDefault: "desc" },
      { id: "asc" },
    ],
  });
  return {
    templates: templates.map(publicTemplate),
    types: TEMPLATE_TYPE_VALUES.map((value) => ({
      value,
      label: PRINT_TEMPLATE_TYPE_NAMES[value],
    })),
    fields: PRINT_FIELD_DEFINITIONS,
    fonts: PRINT_FONT_FAMILIES,
  };
}

function normalizedPayload(payload, current = null) {
  const templateType = requireType(
    payload.templateType ?? current?.templateType,
  );
  const widthMm = decimal(payload.widthMm ?? current?.widthMm, "纸张宽度");
  const heightMm = decimal(payload.heightMm ?? current?.heightMm, "纸张高度");
  const layout = parseLayout(
    payload.layout ?? payload.fields ?? current?.layoutJson,
  );
  return {
    templateType,
    name: text(payload.name ?? current?.name, 100, "模板名称"),
    widthMm,
    heightMm,
    layoutJson: JSON.stringify({
      version: 1,
      fields: normalizeFields(templateType, layout.fields, widthMm, heightMm),
    }),
    enabled:
      payload.enabled === undefined
        ? (current?.enabled ?? 1)
        : payload.enabled
          ? 1
          : 0,
    isDefault:
      payload.isDefault === undefined
        ? (current?.isDefault ?? 0)
        : payload.isDefault
          ? 1
          : 0,
  };
}

export async function createPrintTemplate(prisma, actor, payload = {}) {
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  const data = normalizedPayload(payload, payload);
  try {
    const created = await prisma.$transaction(async (tx) => {
      if (data.isDefault)
        await tx.printTemplate.updateMany({
          where: { storeId, templateType: data.templateType },
          data: { isDefault: 0, defaultScope: null },
        });
      return tx.printTemplate.create({
        data: {
          ...data,
          storeId,
          defaultScope: defaultScope(
            storeId,
            data.templateType,
            data.isDefault,
          ),
          createdBy: Number(actor.id),
          updatedBy: Number(actor.id),
        },
        include: { store: true },
      });
    });
    return publicTemplate(created);
  } catch (error) {
    throwPrintTemplateUniqueError(error);
  }
}

export async function updatePrintTemplate(
  prisma,
  actor,
  idValue,
  payload = {},
) {
  const id = Number(idValue);
  if (!Number.isInteger(id) || id <= 0)
    throw new AppError("打印模板 ID 不正确", 400);
  const current = await prisma.printTemplate.findUnique({ where: { id } });
  if (!current) throw new AppError("打印模板不存在", 404);
  if (!isSuperAdmin(actor) && Number(current.storeId) !== Number(actor.storeId))
    throw new AppError("无权修改其它门店的打印模板", 403);
  const data = normalizedPayload(payload, current);
  try {
    const updated = await prisma.$transaction(async (tx) => {
      if (data.isDefault)
        await tx.printTemplate.updateMany({
          where: {
            storeId: current.storeId,
            templateType: data.templateType,
            id: { not: id },
          },
          data: { isDefault: 0, defaultScope: null },
        });
      return tx.printTemplate.update({
        where: { id },
        data: {
          ...data,
          storeId: current.storeId,
          defaultScope: defaultScope(
            current.storeId,
            data.templateType,
            data.isDefault,
          ),
          updatedBy: Number(actor.id),
        },
        include: { store: true },
      });
    });
    return publicTemplate(updated);
  } catch (error) {
    throwPrintTemplateUniqueError(error);
  }
}

export async function deletePrintTemplate(prisma, actor, idValue) {
  const id = Number(idValue);
  if (!Number.isInteger(id) || id <= 0)
    throw new AppError("打印模板 ID 不正确", 400);
  const current = await prisma.printTemplate.findUnique({ where: { id } });
  if (!current) throw new AppError("打印模板不存在", 404);
  if (!isSuperAdmin(actor) && Number(current.storeId) !== Number(actor.storeId))
    throw new AppError("无权删除其它门店的打印模板", 403);
  if (current.isDefault === 1)
    throw new AppError("默认模板不能删除，请先设置其它默认模板", 400);
  await prisma.printTemplate.delete({ where: { id } });
  return { id };
}
