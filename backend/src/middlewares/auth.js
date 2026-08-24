import { AppError } from '../utils/appError.js';
import { isAdmin, isManager, isStoreMember, isStoreStaff, isSuperAdmin } from '../constants/roles.js';

export async function verifyToken(request) {
  try {
    await request.jwtVerify();
  } catch {
    throw new AppError('登录已过期，请重新登录', 401);
  }

  const role = Number(request.user.role);
  const accountType = String(request.user.accountType || (role === 1 ? 'user' : 'admin'));
  const storeId = request.user.storeId == null ? null : Number(request.user.storeId);
  if (
    !['admin', 'user'].includes(accountType) ||
    ![0, 1, 2, 3].includes(role) ||
    !Number.isInteger(Number(request.user.id)) ||
    (accountType === 'user' && role !== 1) ||
    (accountType === 'admin' && role === 1)
  ) {
    throw new AppError('登录凭证无效，请重新登录', 401);
  }
  request.user = {
    id: Number(request.user.id),
    phone: String(request.user.phone || ''),
    accountType,
    role,
    storeId,
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
