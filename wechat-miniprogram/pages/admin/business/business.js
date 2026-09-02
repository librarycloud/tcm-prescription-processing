import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';

Page({
  data: {
    activeTab: 'business',
    userRole: -1
  },

  onShow() {
    const user = getUser() || {};
    this.setData({ userRole: Number(user.role ?? -1) });
  },

  onTabChange: onAdminTabChange,

  openPage(e) {
    const url = e.currentTarget.dataset.url;
    if (url) wx.navigateTo({ url });
  }
});
