const USER_TAB_URLS = {
  packages: '/pages/user/packages/packages',
  profile: '/pages/user/profile/profile'
};

export function onUserTabChange(e) {
  const detail = e.detail || {};
  const value = typeof detail === 'object' ? detail.value : detail;
  const url = USER_TAB_URLS[value];
  if (!url || value === this.data.activeTab) return;
  wx.redirectTo({ url });
}
