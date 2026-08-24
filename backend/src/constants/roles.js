export const ROLES = Object.freeze({
  SUPER_ADMIN: 0,
  USER: 1,
  STORE_ADMIN: 2,
  STORE_STAFF: 3
});

export function isSuperAdmin(user) {
  return Number(user?.role) === ROLES.SUPER_ADMIN;
}

export function isStoreAdmin(user) {
  return Number(user?.role) === ROLES.STORE_ADMIN;
}

export function isStoreStaff(user) {
  return Number(user?.role) === ROLES.STORE_STAFF;
}

export function isStoreMember(user) {
  return isStoreAdmin(user) || isStoreStaff(user);
}

export function isAdmin(user) {
  return isSuperAdmin(user) || isStoreMember(user);
}

export function isManager(user) {
  return isSuperAdmin(user) || isStoreAdmin(user);
}
