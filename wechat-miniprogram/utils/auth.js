const CACHE_PREFIX = 'api-cache:';

function clearCachedResponses() {
  try {
    const keys = wx.getStorageInfoSync().keys || [];
    keys.filter((key) => key.startsWith(CACHE_PREFIX)).forEach((key) => wx.removeStorageSync(key));
  } catch (error) {
    // Cache cleanup is best effort.
  }
}

export function getToken() {
  return wx.getStorageSync('token');
}

export function setSession(data) {
  if (getToken() && getToken() !== data.token) clearCachedResponses();
  wx.setStorageSync('token', data.token);
  wx.setStorageSync('user', data.user);
}

export function getUser() {
  return wx.getStorageSync('user');
}

export function clearSession() {
  wx.removeStorageSync('token');
  wx.removeStorageSync('user');
  clearCachedResponses();
}

export function redirectByRole(user) {
  const role = Number(user?.role);
  if (![0, 1, 2, 3].includes(role)) {
    clearSession();
    wx.reLaunch({ url: '/pages/login/login' });
    return;
  }

  const url = role === 0 || role === 2 || role === 3
    ? '/pages/admin/dashboard/dashboard'
    : '/pages/user/packages/packages';
  wx.reLaunch({ url });
}
