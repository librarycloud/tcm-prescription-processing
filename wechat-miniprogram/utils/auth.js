import { invalidateRefData } from './reference';

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
  invalidateRefData(); // 清除内存中的参考数据缓存
}

export function redirectByRole(user) {
  const role = Number(user?.role);
  if (![0, 2, 3].includes(role)) {
    clearSession();
    wx.showToast({ title: '仅限工作人员登录', icon: 'none' });
    setTimeout(() => {
      wx.reLaunch({ url: '/pages/login/login' });
    }, 1500);
    return;
  }

  const url = '/pages/admin/e6-inventory/e6-inventory';
  wx.reLaunch({ url });
}
