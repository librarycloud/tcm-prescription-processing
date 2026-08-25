import { AppError } from '../utils/appError.js';
import { isAdmin, isManager, isStoreMember, isStoreStaff, isSuperAdmin } from '../constants/roles.js';
import { RECORD_STATUS } from '../constants/recordStatus.js';

export async function verifyToken(request) {
  try {
    await request.jwtVerify();
  } catch {
    throw new AppError('登录已过期，请重新登录', 401);
  }

  const role = Number(request.user.role);
  const accountType = String(request.user.accountType || (role === 1 ? 'user' : 'admin'));
  const storeId = request.user.storeId == null ? null : Number(request.user.storeId);
  const jti = String(request.user.jti || '');
  if (
    !['admin', 'user'].includes(accountType) ||
    ![0, 1, 2, 3].includes(role) ||
    !Number.isInteger(Number(request.user.id)) ||
    !jti ||
    (accountType === 'user' && role !== 1) ||
    (accountType === 'admin' && role === 1)
  ) {
    throw new AppError('登录凭证无效，请重新登录', 401);
  }

  const active = await request.server.authSessions.has({
    accountType,
    accountId: Number(request.user.id),
    jti
  });
  if (!active) {
    throw new AppError('登录已失效，请重新登录', 401);
  }

  // JWT claims are intentionally treated as a cache. Re-check the account and
  // store on every request so disabling a user/store takes effect immediately.
  const prisma = request.server.prisma;
  if (prisma?.admin?.findUnique && prisma?.user?.findUnique) {
    const repository = accountType === 'admin' ? prisma.admin : prisma.user;
    const account = await repository.findUnique({
      where: { id: Number(request.user.id) },
      ...(accountType === 'admin' ? { include: { store: true } } : {}),
    });
    if (!account || account.status !== RECORD_STATUS.ENABLED) {
      if (request.server.authSessions.revokeAccount) {
        await request.server.authSessions.revokeAccount({ accountType, accountId: Number(request.user.id) });
      }
      throw new AppError('账号已停用，请重新登录', 401);
    }
    if (accountType === 'admin') {
      const roleChanged = Number(account.role) !== role;
      const storeChanged = (account.storeId == null ? null : Number(account.storeId)) !== storeId;
      if (roleChanged || storeChanged) {
        if (request.server.authSessions.revokeAccount) {
          await request.server.authSessions.revokeAccount({ accountType, accountId: Number(request.user.id) });
        }
        throw new AppError('登录权限已变更，请重新登录', 401);
      }
      if (
        isStoreMember(account) &&
        (!account.store || account.store.status !== RECORD_STATUS.ENABLED || account.store.deletedAt)
      ) {
        if (request.server.authSessions.revokeAccount) {
          await request.server.authSessions.revokeAccount({ accountType, accountId: Number(request.user.id) });
        }
        throw new AppError('账号所属门店已停用，请重新登录', 403);
      }
      request.user.phone = String(account.phone || '');
    } else {
      request.user.phone = String(account.phone || '');
    }
  }

  request.user = {
    id: Number(request.user.id),
    phone: String(request.user.phone || ''),
    accountType,
    role,
    storeId,
    jti,
    ip: request.ip || null,
    userAgent: request.headers?.['user-agent'] || null
  };
  if (isStoreMember(request.user) && (!Number.isInteger(storeId) || storeId <= 0)) {
    throw new AppError('门店账号未绑定门店', 403);
  }
}

export async function verifyAdmin(request) {
  if (request.user.accountType !== 'admin' || !isAdmin(request.user)) {
    throw new AppError('无后台账号权限', 403);
  }
}

export async function verifyStoreStaffRoute(request) {
  if (!isStoreStaff(request.user)) return;
  if (request.routeOptions?.config?.storeStaff === true) return;
  throw new AppError('门店员工无权执行此操作', 403);
}

export async function verifyManager(request) {
  if (request.user.accountType !== 'admin' || !isManager(request.user)) {
    throw new AppError('无管理员权限', 403);
  }
}

export async function verifySuperAdmin(request) {
  if (request.user.accountType !== 'admin' || !isSuperAdmin(request.user)) {
    throw new AppError('仅全局管理员可执行此操作', 403);
  }
}

export async function verifyUser(request) {
  if (request.user.accountType !== 'user') {
    throw new AppError('无普通用户权限', 403);
  }
}
