export const USER_ROLE = 1;

export function isRegularUser(user) {
  return Number(user?.role) === USER_ROLE;
}

export function getHomePath(user) {
  return isRegularUser(user) ? '/user/packages' : '/login';
}
