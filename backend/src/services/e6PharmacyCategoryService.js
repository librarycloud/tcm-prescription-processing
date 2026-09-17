import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import { describeChanges, recordOperation } from "./operationLogService.js";

function clean(value, max, label) {
  const text = String(value ?? "").trim();
  if (!text) throw new AppError(`请输入${label}`, 400);
  if (text.length > max) throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return text;
}

function idValue(value) {
  const id = Number(value);
  if (!Number.isInteger(id) || id <= 0) throw new AppError("映射 ID 不正确", 400);
  return id;
}

function ensureSuperAdmin(actor) {
  if (!isSuperAdmin(actor)) throw new AppError("仅全局管理员可修改 E6 商品分类映射", 403);
}

export async function listE6PharmacyCategoryMappings(prisma) {
  return prisma.e6PharmacyCategoryMapping.findMany({
    orderBy: [{ categoryCode: "asc" }],
  });
}

export async function saveE6PharmacyCategoryMapping(prisma, idValueOrNull, payload, actor) {
  ensureSuperAdmin(actor);
  const id = idValueOrNull == null ? null : idValue(idValueOrNull);
  const current = id
    ? await prisma.e6PharmacyCategoryMapping.findUnique({ where: { id } })
    : null;
  if (id && !current) throw new AppError("E6 商品分类映射不存在", 404);
  const categoryCode = clean(payload.categoryCode ?? current?.categoryCode, 64, "分类编号");
  const categoryName = clean(payload.categoryName ?? current?.categoryName, 100, "分类名称");
  const duplicate = await prisma.e6PharmacyCategoryMapping.findFirst({
    where: { categoryCode, ...(id ? { id: { not: id } } : {}) },
    select: { id: true },
  });
  if (duplicate) throw new AppError("分类编号映射已存在", 409);
  const data = {
    categoryCode,
    categoryName,
    ...(id ? { updatedBy: Number(actor.id) } : { createdBy: Number(actor.id) }),
  };
  const result = id
    ? await prisma.e6PharmacyCategoryMapping.update({ where: { id }, data })
    : await prisma.e6PharmacyCategoryMapping.create({ data });
  await recordOperation(prisma, actor, {
    module: "e6_pharmacy_category_mapping",
    action: id ? "update" : "create",
    targetId: result.id,
    description: id
      ? describeChanges(current, result, [
          { key: "categoryCode", label: "分类编号" },
          { key: "categoryName", label: "分类名称" },
        ])
      : "新增 E6 商品分类映射",
  });
  return result;
}

export async function deleteE6PharmacyCategoryMapping(prisma, idValueOrNull, actor) {
  ensureSuperAdmin(actor);
  const id = idValue(idValueOrNull);
  const current = await prisma.e6PharmacyCategoryMapping.findUnique({ where: { id } });
  if (!current) throw new AppError("E6 商品分类映射不存在", 404);
  await prisma.e6PharmacyCategoryMapping.delete({ where: { id } });
  await recordOperation(prisma, actor, {
    module: "e6_pharmacy_category_mapping",
    action: "delete",
    targetId: id,
    description: `删除 E6 商品分类映射：${current.categoryCode} / ${current.categoryName}`,
  });
  return { id };
}
