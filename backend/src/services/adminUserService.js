import bcrypt from "bcrypt";
import { AppError } from "../utils/appError.js";
import { toPositiveInt, validatePhone } from "../utils/validators.js";
import { isStoreAdmin, isSuperAdmin, ROLES } from "../constants/roles.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import { describeChanges, recordOperation } from "./operationLogService.js";
import { assertManager } from "./permissionService.js";

const userSelect = {
  id: true,
  username: true,
  phone: true,
  status: true,
  nickname: true,
  name: true,
  email: true,
  emailVerifiedAt: true,
  remark: true,
  createdAt: true,
  updatedAt: true,
};

function buildWhere(query) {
  const keyword = String(query.keyword || "").trim();
  if (!keyword) return {};

  return {
    OR: [
      { nickname: { contains: keyword } },
      { name: { contains: keyword } },
      { email: { contains: keyword } },
      { remark: { contains: keyword } },
      { phone: { contains: keyword } },
      { username: { contains: keyword } },
    ],
  };
}

export async function lookupUsers(prisma, phone) {
  const keyword = String(phone || "").trim();
  if (keyword.length < 7) return [];
  const users = await prisma.user.findMany({
    where: { phone: { startsWith: keyword } },
    select: {
      id: true,
      phone: true,
      status: true,
      nickname: true,
      name: true,
      remark: true,
    },
    orderBy: { phone: "asc" },
    take: 10,
  });
  return users.map((user) => ({ ...user, role: 1 }));
}

export async function listUsers(prisma, ipLookup, query, actor) {
  assertManager(actor);
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 10), 100);
  const where = buildWhere(query);

  const [list, total] = await Promise.all([
    prisma.user.findMany({
      where,
      select: userSelect,
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.user.count({ where }),
  ]);

  const recentLogs = list.length
    ? await prisma.loginLog.findMany({
        where: { userId: { in: list.map((user) => user.id) }, accountType: 'user', success: 1 },
        orderBy: { createdAt: "desc" },
        distinct: ["userId"],
      })
    : [];
  const logMap = new Map(recentLogs.map((log) => [log.userId, log]));

  const result = list.map((user) => {
    const loginLog = logMap.get(user.id);
    return {
      ...user,
      role: 1,
      emailVerified: Boolean(user.emailVerifiedAt),
      lastLoginAt: loginLog?.createdAt || null,
      lastLoginIp: loginLog?.ip || null,
      lastLoginLocation: loginLog ? ipLookup.lookup(loginLog.ip) : null,
    };
  });

  return {
    list: result,
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function updateUser(prisma, id, payload, actor, authSessions = null) {
  assertManager(actor);
  const userId = Number(id);
  if (!Number.isInteger(userId) || userId <= 0)
    throw new AppError("用户 ID 不正确", 400);
  const current = await prisma.user.findUnique({ where: { id: userId } });
  if (!current) throw new AppError("用户不存在", 404);
  // Compatibility for legacy callers; persisted users no longer carry a role.
  if (isStoreAdmin(actor) && current.role !== undefined && current.role !== ROLES.USER) {
    throw new AppError("门店管理员只能修改普通用户信息", 403);
  }

  const data = {};
  let phoneChanged = false;
  if (payload.nickname !== undefined) {
    const nickname = String(payload.nickname || "").trim();
    if (nickname.length > 64) throw new AppError("昵称不能超过 64 个字符", 400);
    data.nickname = nickname || null;
  }

  if (payload.name !== undefined) {
    const name = String(payload.name || "").trim();
    if (name.length > 64) throw new AppError("姓名不能超过 64 个字符", 400);
    data.name = name || null;
  }

  if (payload.remark !== undefined) {
    const remark = String(payload.remark || "").trim();
    if (remark.length > 500) throw new AppError("备注不能超过 500 个字符", 400);
    data.remark = remark || null;
  }

  if (payload.email !== undefined) {
    throw new AppError("邮箱必须由用户通过验证码绑定", 400);
  }

  if (payload.phone !== undefined) {
    const phone = String(payload.phone).trim();
    validatePhone(phone);
    const owner = await prisma.user.findFirst({
      where: { phone, id: { not: userId } },
      select: { id: true },
    });
    if (owner) throw new AppError("手机号已被其他用户使用", 409);
    phoneChanged = phone !== current.phone;
    data.phone = phone;
    data.username = phone;
  }

  if (payload.password) {
    const password = String(payload.password);
    if (password.length < 6 || password.length > 32) {
      throw new AppError("密码长度必须为 6-32 位", 400);
    }
    data.password = await bcrypt.hash(password, 10);
  }

  if (!Object.keys(data).length) throw new AppError("没有可更新的内容", 400);
  if (data.password || phoneChanged || (data.status !== undefined && Number(data.status) !== Number(current.status))) {
    if (authSessions) await authSessions.revokeAccount({ accountType: 'user', accountId: userId });
  }
  data.updatedBy = actor?.id ? Number(actor.id) : null;

  const updated = await prisma.$transaction(async (tx) => {
    const updated = await tx.user.update({
      where: { id: userId },
      data,
      select: userSelect,
    });

    if (phoneChanged) {
      await tx.package.updateMany({
        where: { receiverPhone: current.phone },
        data: { receiverPhone: data.phone },
      });
    }

    return updated;
  });
  const changeDescription = describeChanges(current, updated, [
    { key: "phone", label: "手机号" },
    { key: "nickname", label: "昵称" },
    { key: "name", label: "姓名" },
    { key: "remark", label: "备注" },
    { key: "status", label: "状态", values: { 0: "禁用", 1: "启用" } },
  ]);
  await recordOperation(prisma, actor, {
    module: "user",
    action: "update",
    targetId: updated.id,
    description:
      payload.password && changeDescription === "未检测到字段变化"
        ? "密码：已修改"
        : payload.password
          ? `${changeDescription}；密码：已修改`
          : changeDescription,
  });
  return updated;
}

export async function deleteUser(prisma, id, actor, authSessions = null) {
  if (!isSuperAdmin(actor))
    throw new AppError("仅全局管理员可以删除用户", 403);
  const userId = Number(id);
  if (!Number.isInteger(userId) || userId <= 0)
    throw new AppError("用户 ID 不正确", 400);
  const current = await prisma.user.findFirst({
    where: { id: userId },
    select: { id: true },
  });
  if (!current) throw new AppError("用户不存在", 404);

  if (authSessions) await authSessions.revokeAccount({ accountType: 'user', accountId: userId });
  await prisma.user.delete({ where: { id: userId } });
  await recordOperation(prisma, actor, {
    module: "user",
    action: "delete",
    targetId: userId,
    description: "删除用户",
  });
  return { id: userId };
}

export async function createUser(prisma, payload, actor) {
  if (!isSuperAdmin(actor))
    throw new AppError("仅全局管理员可以新增用户", 403);
  const phone = String(payload.phone || "").trim();
  validatePhone(phone);
  const password = String(payload.password || "");
  if (password.length < 6 || password.length > 32) {
    throw new AppError("密码长度必须为 6-32 位", 400);
  }
  const owner = await prisma.user.findUnique({
    where: { phone },
    select: { id: true },
  });
  if (owner) throw new AppError("手机号已被使用", 409);
  const nickname = String(payload.nickname || "").trim();
  const name = String(payload.name || "").trim();
  const remark = String(payload.remark || "").trim();
  if (payload.email) throw new AppError("邮箱必须由用户通过验证码绑定", 400);
  const created = await prisma.user.create({
    data: {
      username: phone,
      phone,
      password: await bcrypt.hash(password, 10),
      nickname: nickname || null,
      name: name || null,
      email: null,
      remark: remark || null,
      status: RECORD_STATUS.ENABLED,
      createdBy: actor?.id ? Number(actor.id) : null,
    },
    select: userSelect,
  });
  await recordOperation(prisma, actor, {
    module: "user",
    action: "create",
    targetId: created.id,
    description: "新增用户",
  });
  return created;
}
