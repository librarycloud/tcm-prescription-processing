import { getMyPackages } from '../../../api/user';
import { formatDate, pickupMethodText, statusText, statusTheme } from '../../../utils/format';
import { onUserTabChange } from '../../../utils/user-tabbar';
import { clearResponseCache } from '../../../utils/request';

Page({
  data: {
    activeTab: 'packages',
    loading: false,
    list: []
  },

  onTabChange: onUserTabChange,

  onShow() {
    this.load();
  },

  async onPullDownRefresh() {
    clearResponseCache();
    try {
      await this.load();
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  async load() {
    this.setData({ loading: true });
    try {
      const list = await getMyPackages();
      this.setData({
        list: (list || []).map((item) => ({
          ...item,
          createdAtText: formatDate(item.createdAt),
          pickedAtText: formatDate(item.pickedAt),
          pickupMethodText: pickupMethodText(item.pickupMethod),
          statusText: statusText(item.status),
          statusTheme: statusTheme(item.status)
        }))
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  goDetail(e) {
    wx.navigateTo({ url: `/pages/user/package-detail/package-detail?id=${e.currentTarget.dataset.id}` });
  }
});
