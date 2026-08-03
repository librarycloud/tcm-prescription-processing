import { AppError } from '../utils/appError.js';
import { isManager, isStoreAdmin, isSuperAdmin } from '../constants/roles.js';

export async function verifyToken(request) {
  try {
    await request.jwtVerify();
  } catch {
    throw new AppError('登录已过期，请重新登录', 401);
  }

  const role = Number(request.user.role);
  const storeId = request.user.storeId == null ? null : Number(request.user.storeId);
  if (![0, 1, 2].includes(role) || !Number.isInteger(Number(request.user.id))) {
    throw new AppError('登录凭证无效，请重新登录', 401);
  }
  request.user = {
    id: Number(request.user.id),
    phone: String(request.user.phone || ''),
    role,
    storeId,
    ip: request.ip || null,
    userAgent: request.headers?.['user-agent'] || null
  };
  if (isStoreAdmin(request.user) && (!Number.isInteger(storeId) || storeId <= 0)) {
    throw new AppError('门店管理员未绑定门店', 403);
  }
}

export async function verifyManager(request) {
  if (!isManager(request.user)) {
    throw new AppError('无管理员权限', 403);
  }
}

export async function verifySuperAdmin(request) {
  if (!isSuperAdmin(request.user)) {
    throw new AppError('仅全局管理员可执行此操作', 403);
  }
}
