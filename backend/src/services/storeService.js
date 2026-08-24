import { AppError } from "../utils/appError.js";
import {
  RECORD_STATUS,
  RECORD_STATUS_VALUES,
} from "../constants/recordStatus.js";
import { toPositiveInt } from "../utils/validators.js";
import { describeChanges, recordOperation } from "./operationLogService.js";

function normalizeStatus(value, fallback = 1) {
  if (value === undefined || value === null || value === "") return fallback;
  const status = Number(value);
  if (!RECORD_STATUS_VALUES.includes(status))
    throw new AppError("门店状态不正确", 400);
  return status;
}

function normalizeStoreData(payload, partial = false) {
  const data = {};
  if (!partial || payload.name !== undefined) {
    const name = String(payload.name || "").trim();
    if (!name) throw new AppError("请输入门店名称", 400);
    if (name.length > 100)
      throw new AppError("门店名称不能超过 100 个字符", 400);
    data.name = name;
  }
  if (!partial || payload.code !== undefined) {
    const code = String(payload.code || "")
      .trim()
      .toUpperCase();
    if (!/^[A-Z0-9_-]{2,50}$/.test(code)) {
      throw new AppError("门店编码仅支持 2-50 位字母、数字、横线和下划线", 400);
    }
    data.code = code;
  }
  if (payload.address !== undefined || !partial) {
    const address = String(payload.address || "").trim();
    if (address.length > 255)
      throw new AppError("门店地址不能超过 255 个字符", 400);
    data.address = address || null;
  }
  if (payload.phone !== undefined || !partial) {
    const phone = String(payload.phone || "").trim();
    if (phone && !/^[0-9+()-]{5,20}$/.test(phone)) {
      throw new AppError("门店联系电话格式不正确", 400);
    }
    data.phone = phone || null;
  }
  if (payload.status !== undefined || !partial) {
    data.status = normalizeStatus(payload.status);
  }
  return data;
}

function storeInclude() {
  return { _count: { select: { admins: true, packages: true, herbs: true, herbLocations: true } } };
}

export async function listStores(prisma, query) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 10), 100);
  const keyword = String(query.keyword || "").trim();
  const where = { deletedAt: null };
  if (keyword) {
    where.OR = [
      { name: { contains: keyword } },
      { code: { contains: keyword } },
      { address: { contains: keyword } },
      { phone: { contains: keyword } },
    ];
  }
  if (query.status !== undefined && query.status !== "") {
    where.status = normalizeStatus(query.status);
  }

  const [list, total] = await Promise.all([
    prisma.store.findMany({
      where,
      include: storeInclude(),
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.store.count({ where }),
  ]);
  return {
    list,
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function getStore(prisma, id) {
  const store = await prisma.store.findFirst({
    where: { id: Number(id), deletedAt: null },
    include: storeInclude(),
  });
  if (!store) throw new AppError("门店不存在", 404);
  return store;
}

export async function createStore(prisma, payload, actor) {
  const data = normalizeStoreData(payload);
  data.createdBy = actor?.id ? Number(actor.id) : null;
  const duplicate = await prisma.store.findFirst({
    where: { OR: [{ name: data.name }, { code: data.code }] },
    select: { id: true },
  });
  if (duplicate) throw new AppError("门店名称或编码已存在", 409);
  const created = await prisma.store.create({ data, include: storeInclude() });
  await recordOperation(prisma, actor, {
    module: "store",
    action: "create",
    targetId: created.id,
    storeId: created.id,
    description: "新增门店",
  });
  return created;
}

export async function updateStore(prisma, id, payload, actor) {
  const storeId = Number(id);
  const current = await getStore(prisma, storeId);
  const data = normalizeStoreData(payload, true);
  if (!Object.keys(data).length) throw new AppError("没有可更新的内容", 400);
  data.updatedBy = actor?.id ? Number(actor.id) : null;
  if (data.name || data.code) {
    const duplicate = await prisma.store.findFirst({
      where: {
        id: { not: storeId },
        OR: [
          data.name ? { name: data.name } : undefined,
          data.code ? { code: data.code } : undefined,
        ].filter(Boolean),
      },
      select: { id: true },
    });
    if (duplicate) throw new AppError("门店名称或编码已存在", 409);
  }
  const updated = await prisma.store.update({
    where: { id: storeId },
    data,
    include: storeInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "store",
    action: "update",
    targetId: updated.id,
    storeId: updated.id,
    description: describeChanges(current, updated, [
      { key: "name", label: "门店名称" },
      { key: "code", label: "门店编码" },
      { key: "address", label: "地址" },
      { key: "phone", label: "电话" },
      { key: "status", label: "状态", values: { 0: "禁用", 1: "启用" } },
    ]),
  });
  return updated;
}

export async function deleteStore(prisma, id, actor) {
  const store = await getStore(prisma, id);
  if (store._count.admins > 0 || store._count.packages > 0 || store._count.herbs > 0) {
    throw new AppError("门店存在管理员、业务数据或斗谱，不能删除，可改为停用", 409);
  }
  await prisma.store.update({
    where: { id: store.id },
    data: {
      status: RECORD_STATUS.DISABLED,
      updatedBy: actor?.id ? Number(actor.id) : null,
      deletedAt: new Date(),
      deletedBy: actor?.id ? Number(actor.id) : null,
    },
  });
  await recordOperation(prisma, actor, {
    module: "store",
    action: "delete",
    targetId: store.id,
    storeId: store.id,
    description: "删除门店",
  });
  return { id: store.id };
}
