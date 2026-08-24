import {
  ROLES,
  isStoreMember,
  isSuperAdmin,
} from "../constants/roles.js";
import { AppError } from "../utils/appError.js";
import {
  businessScope,
  resolveBusinessStoreId as resolveStoreId,
} from "./permissionService.js";
import { packageRepository } from "../repositories/packageRepository.js";

export function requirePackageAccess(actor) {
  if (!isSuperAdmin(actor) && !isStoreMember(actor)) {
    throw new AppError("无包裹管理权限", 403);
  }
  if (isStoreMember(actor) && !Number(actor.storeId)) {
    throw new AppError("门店账号未绑定门店", 403);
  }
}

export function packageAccessWhere(actor) {
  requirePackageAccess(actor);
  return { ...businessScope(actor), deletedAt: null };
}

export async function resolveCreateStoreId(prisma, actor, requestedStoreId) {
  return resolveStoreId(prisma, actor, requestedStoreId);
}

export async function getAccessiblePackage(prisma, actor, where, include) {
  const data = await packageRepository.findFirst(prisma, {
    where: { ...where, ...packageAccessWhere(actor) },
    include,
  });
  if (!data) throw new AppError("包裹不存在", 404);
  return data;
}

export function canFilterStore(actor) {
  return Number(actor?.role) === ROLES.SUPER_ADMIN;
}
