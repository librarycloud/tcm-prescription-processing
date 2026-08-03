import { getMyPackages } from '../../../api/user';
import { formatDate, pickupMethodText, statusText, statusTheme } from '../../../utils/format';
import { onUserTabChange } from '../../../utils/user-tabbar';

Page({
  data: {
    activeTab: 'packages',
    list: []
  },

  onTabChange: onUserTabChange,

  onShow() {
    this.load();
  },

  async load() {
    const list = await getMyPackages();
    this.setData({
      list: list.map((item) => ({
        ...item,
        createdAtText: formatDate(item.createdAt),
        pickedAtText: formatDate(item.pickedAt),
        pickupMethodText: pickupMethodText(item.pickupMethod),
        statusText: statusText(item.status),
        statusTheme: statusTheme(item.status)
      }))
    });
  },

  goDetail(e) {
    wx.navigateTo({ url: `/pages/user/package-detail/package-detail?id=${e.currentTarget.dataset.id}` });
  }
});
