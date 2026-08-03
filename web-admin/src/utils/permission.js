export const ROLES = Object.freeze({ SUPER_ADMIN: 0, USER: 1, STORE_ADMIN: 2 });

export function isSuperAdmin(user) {
  return Number(user?.role) === ROLES.SUPER_ADMIN;
}

export function isStoreAdmin(user) {
  return Number(user?.role) === ROLES.STORE_ADMIN;
}

export function isManager(user) {
  return isSuperAdmin(user) || isStoreAdmin(user);
}

export function roleText(user) {
  if (isSuperAdmin(user)) return '全局管理员';
  if (isStoreAdmin(user)) return '门店管理员';
  return '用户';
}

export function getHomePath(user) {
  return isManager(user) ? '/admin/dashboard' : '/login';
}
