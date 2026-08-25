import bcrypt from "bcrypt";
import { ROLES, isManager, isSuperAdmin } from "../constants/roles.js";
import { AppError } from "../utils/appError.js";
import {
  RECORD_STATUS,
  RECORD_STATUS_VALUES,
} from "../constants/recordStatus.js";
import { toPositiveInt, validatePhone, normalizeOptionalUsername } from "../utils/validators.js";
import { describeChanges, recordOperation } from "./operationLogService.js";

const select = {
  id: true,
  username: true,
  phone: true,
  role: true,
  status: true,
  storeId: true,
  nickname: true,
  name: true,
  email: true,
  emailVerifiedAt: true,
  remark: true,
  createdAt: true,
  updatedAt: true,
  store: { select: { id: true, name: true, code: true, status: true } },
};

const STORE_MEMBER_ROLES = [ROLES.STORE_ADMIN, ROLES.STORE_STAFF];

function assertStoreAccountManager(actor) {
  if (!isManager(actor)) throw new AppError("无门店账号管理权限", 403);
}

function memberRole(value, fallback = ROLES.STORE_ADMIN) {
  const role = Number(value ?? fallback);
  if (!STORE_MEMBER_ROLES.includes(role))
    throw new AppError("门店账号角色不正确", 400);
  return role;
}

async function requireStore(prisma, storeIdValue) {
  const storeId = Number(storeIdValue);
  if (!Number.isInteger(storeId) || storeId <= 0)
    throw new AppError("请选择所属门店", 400);
  const store = await prisma.store.findUnique({ where: { id: storeId } });
  if (!store || store.deletedAt) throw new AppError("所属门店不存在", 404);
  return storeId;
}

async function resolveStoreId(prisma, actor, requestedStoreId, current = null) {
  const requested = requestedStoreId ?? current?.storeId;
  if (isSuperAdmin(actor)) return requireStore(prisma, requested);

  const storeId = Number(actor.storeId);
  if (!Number.isInteger(storeId) || storeId <= 0)
    throw new AppError("门店账号未绑定门店", 403);
  if (requestedStoreId !== undefined && Number(requestedStoreId) !== storeId)
    throw new AppError("只能管理本门店账号", 403);
  return storeId;
}

async function normalizeData(prisma, actor, payload, current = null, creating = false) {
  const data = {};
  if (creating || payload.phone !== undefined) {
    const phone = String(payload.phone || "").trim();
    validatePhone(phone);
    const owner = await prisma.admin.findFirst({
      where: { phone, ...(current?.id ? { id: { not: current.id } } : {}) },
      select: { id: true },
    });
    if (owner) throw new AppError("手机号已被使用", 409);
    data.phone = phone;
  }
  if (creating || payload.username !== undefined) {
    const username = payload.username === undefined && current
      ? current.username || null
      : normalizeOptionalUsername(payload.username);
    if (username) {
      const owner = await prisma.admin.findFirst({
        where: { username, ...(current?.id ? { id: { not: current.id } } : {}) },
        select: { id: true },
      });
      if (owner) throw new AppError("用户名已被使用", 409);
    }
    data.username = username;
  }
  if (creating || payload.storeId !== undefined) {
    data.storeId = await resolveStoreId(prisma, actor, payload.storeId, current);
  }
  if (payload.nickname !== undefined || creating) {
    const nickname = String(payload.nickname || "").trim();
    if (nickname.length > 64) throw new AppError("昵称不能超过 64 个字符", 400);
    data.nickname = nickname || null;
  }
  for (const field of ["name", "remark"]) {
    if (payload[field] !== undefined) {
      const value = String(payload[field] || "").trim();
      const max = field === "name" ? 64 : 500;
      if (value.length > max)
        throw new AppError(
          `${field === "name" ? "姓名" : "备注"}内容过长`,
          400,
        );
      data[field] = value || null;
    }
  }
  if (payload.email !== undefined) {
    throw new AppError("邮箱必须由用户通过验证码绑定", 400);
  }
  if (creating || payload.password) {
    const password = String(payload.password || "");
    if (password.length < 6 || password.length > 32) {
      throw new AppError("密码长度必须为 6-32 位", 400);
    }
    data.password = await bcrypt.hash(password, 10);
  }
  if (payload.status !== undefined || creating) {
    const status =
      payload.status === undefined
        ? RECORD_STATUS.ENABLED
        : Number(payload.status);
    if (!RECORD_STATUS_VALUES.includes(status))
      throw new AppError("账号状态不正确", 400);
    data.status = status;
  }
  data.role = memberRole(payload.role, current?.role);
  return data;
}

function scopedWhere(actor, query) {
  const where = { role: { in: STORE_MEMBER_ROLES } };
  if (isSuperAdmin(actor)) {
    if (query.storeId !== undefined && query.storeId !== "")
      where.storeId = Number(query.storeId);
  } else {
    where.storeId = Number(actor.storeId);
  }
  return where;
}

async function assertScope(actor, current) {
  if (!isSuperAdmin(actor) && Number(current.storeId) !== Number(actor.storeId))
    throw new AppError("只能管理本门店账号", 403);
}

async function assertStoreKeepsManager(prisma, current, data) {
  const nextRole = data.role ?? current.role;
  const nextStatus = data.status ?? current.status;
  const nextStoreId = data.storeId ?? current.storeId;
  const remainsEnabledManager =
    Number(nextRole) === ROLES.STORE_ADMIN &&
    Number(nextStatus) === RECORD_STATUS.ENABLED &&
    Number(nextStoreId) === Number(current.storeId);
  if (
    Number(current.role) !== ROLES.STORE_ADMIN ||
    Number(current.status) !== RECORD_STATUS.ENABLED ||
    remainsEnabledManager
  ) {
    return;
  }
  const remaining = await prisma.admin.count({
    where: {
      storeId: current.storeId,
      role: ROLES.STORE_ADMIN,
      status: RECORD_STATUS.ENABLED,
      id: { not: current.id },
    },
  });
  if (remaining === 0)
    throw new AppError("每个门店至少保留一名启用的门店管理员", 409);
}

export async function listStoreAdmins(prisma, query, actor) {
  assertStoreAccountManager(actor);
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 10), 100);
  const keyword = String(query.keyword || "").trim();
  const where = scopedWhere(actor, query);
  if (query.status !== undefined && query.status !== "")
    where.status = Number(query.status);
  if (keyword) {
    where.OR = [
      { phone: { contains: keyword } },
      { username: { contains: keyword } },
      { nickname: { contains: keyword } },
      { name: { contains: keyword } },
      { store: { name: { contains: keyword } } },
    ];
  }
  const [list, total] = await Promise.all([
    prisma.admin.findMany({
      where,
      select,
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.admin.count({ where }),
  ]);
  return {
    list,
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function createStoreAdmin(prisma, payload, actor, authSessions = null) {
  assertStoreAccountManager(actor);
  if (
    payload.userId !== undefined &&
    payload.userId !== null &&
    payload.userId !== ""
  ) {
    const userId = Number(payload.userId);
    if (!Number.isInteger(userId) || userId <= 0)
      throw new AppError("用户 ID 不正确", 400);
    const current = await prisma.user.findUnique({ where: { id: userId } });
    if (!current) throw new AppError("用户不存在", 404);
    const data = await normalizeData(prisma, actor, payload, current, true);
    if (authSessions) {
      await authSessions.revokeAccount({ accountType: 'user', accountId: userId });
    }
    const updated = await prisma.$transaction(async (tx) => {
      const created = await tx.admin.create({
        data: {
          ...data,
          username: data.username,
          phone: current.phone,
          password: data.password || current.password,
          email: current.email,
          emailVerifiedAt: current.emailVerifiedAt,
          createdAt: current.createdAt,
          createdBy: actor?.id ? Number(actor.id) : null,
          updatedBy: actor?.id ? Number(actor.id) : null,
        },
        select,
      });
      await tx.user.delete({ where: { id: userId } });
      return created;
    });
    await recordOperation(prisma, actor, {
      module: "store-admin",
      action: "create",
      targetId: updated.id,
      storeId: updated.storeId,
      description: data.role === ROLES.STORE_STAFF ? "创建门店员工" : "创建门店管理员",
    });
    return updated;
  }

  const data = await normalizeData(prisma, actor, payload, null, true);
  data.createdBy = actor?.id ? Number(actor.id) : null;
  const created = await prisma.admin.create({ data, select });
  await recordOperation(prisma, actor, {
    module: "store-admin",
    action: "create",
    targetId: created.id,
    storeId: created.storeId,
    description: data.role === ROLES.STORE_STAFF ? "创建门店员工" : "创建门店管理员",
  });
  return created;
}

export async function updateStoreAdmin(prisma, id, payload, actor, authSessions = null) {
  assertStoreAccountManager(actor);
  const userId = Number(id);
  const current = await prisma.admin.findFirst({
    where: { id: userId, role: { in: STORE_MEMBER_ROLES } },
    select,
  });
  if (!current) throw new AppError("门店账号不存在", 404);
  await assertScope(actor, current);
  const data = await normalizeData(prisma, actor, payload, current);
  if (Number(actor.id) === current.id && Number(data.role) !== Number(current.role))
    throw new AppError("不能修改当前登录账号的角色", 400);
  await assertStoreKeepsManager(prisma, current, data);
  const shouldRevokeTokens = Boolean(
    data.password ||
    data.username !== undefined && data.username !== current.username ||
    data.phone !== current.phone ||
    Number(data.role) !== Number(current.role) ||
    Number(data.status) !== Number(current.status) ||
    Number(data.storeId) !== Number(current.storeId)
  );
  if (shouldRevokeTokens && authSessions) {
    await authSessions.revokeAccount({ accountType: 'admin', accountId: userId });
  }
  data.updatedBy = actor?.id ? Number(actor.id) : null;
  const updated = await prisma.admin.update({
    where: { id: userId },
    data,
    select,
  });
  await recordOperation(prisma, actor, {
    module: "store-admin",
    action: "update",
    targetId: updated.id,
    storeId: updated.storeId,
    description: (() => {
      const changes = describeChanges(current, updated, [
        {
          key: "role",
          label: "角色",
          values: {
            [ROLES.STORE_ADMIN]: "门店管理员",
            [ROLES.STORE_STAFF]: "门店员工",
          },
        },
        { key: "phone", label: "手机号" },
        { key: "username", label: "用户名" },
        { key: "nickname", label: "昵称" },
        { key: "name", label: "姓名" },
        { label: "所属门店", get: (item) => item?.store?.name || item?.storeId },
        { key: "remark", label: "备注" },
        { key: "status", label: "状态", values: { 0: "禁用", 1: "启用" } },
      ]);
      if (!payload.password) return changes;
      return changes === "未检测到字段变化"
        ? "密码：已修改"
        : `${changes}；密码：已修改`;
    })(),
  });
  return updated;
}

export async function deleteStoreAdmin(prisma, id, actor, authSessions = null) {
  if (!isSuperAdmin(actor)) throw new AppError("仅全局管理员可删除门店管理员", 403);
  const userId = Number(id);
  if (userId === Number(actor?.id))
    throw new AppError("不能删除当前登录账号", 400);
  const current = await prisma.admin.findFirst({
    where: { id: userId, role: ROLES.STORE_ADMIN },
    select: { id: true, storeId: true },
  });
  if (!current) throw new AppError("门店管理员不存在", 404);
  const packageCount = await prisma.package.count({
    where: {
      OR: [
        { createdBy: userId },
        { verifiedBy: userId },
        { modifiedBy: userId },
      ],
    },
  });
  if (packageCount)
    throw new AppError("该管理员存在包裹审计记录，请改为禁用账号", 409);
  if (authSessions) {
    await authSessions.revokeAccount({ accountType: 'admin', accountId: userId });
  }
  await prisma.admin.delete({ where: { id: userId } });
  await recordOperation(prisma, actor, {
    module: "store-admin",
    action: "delete",
    targetId: userId,
    storeId: current.storeId,
    description: "删除门店管理员",
  });
  return { id: userId };
}
