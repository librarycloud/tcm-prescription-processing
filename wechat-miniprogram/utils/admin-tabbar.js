const ADMIN_TAB_URLS = {
  overview: '/pages/admin/dashboard/dashboard',
  herbs: '/pages/admin/herb-locations/herb-locations',
  processing: '/pages/admin/processing-workbench/processing-workbench',
  packages: '/pages/admin/packages/packages',
  profile: '/pages/admin/profile/profile'
};

export function onAdminTabChange(e) {
  const detail = e.detail || {};
  const value = typeof detail === 'object' ? detail.value : detail;
  const url = ADMIN_TAB_URLS[value];
  if (!url) return;
  const pages = getCurrentPages();
  const currentPath = pages.length ? `/${pages[pages.length - 1].route}` : '';
  if (currentPath === url) return;
  wx.redirectTo({ url });
}
