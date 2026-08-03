export function getToken() {
  return wx.getStorageSync('token');
}

export function setSession(data) {
  wx.setStorageSync('token', data.token);
  wx.setStorageSync('user', data.user);
}

export function getUser() {
  return wx.getStorageSync('user');
}

export function clearSession() {
  wx.removeStorageSync('token');
  wx.removeStorageSync('user');
}

export function redirectByRole(user) {
  const role = Number(user?.role);
  if (![0, 1, 2].includes(role)) {
    clearSession();
    wx.reLaunch({ url: '/pages/login/login' });
    return;
  }

  const url = role === 0 || role === 2
    ? '/pages/admin/dashboard/dashboard'
    : '/pages/user/packages/packages';
  wx.reLaunch({ url });
}
