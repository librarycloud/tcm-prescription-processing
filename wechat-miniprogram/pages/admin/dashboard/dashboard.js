import { getStats } from '../../../api/admin';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';
import { clearResponseCache } from '../../../utils/request';

Page({
  data: {
    activeTab: 'overview',
    user: {},
    stats: {},
    cards: [],
    processingCards: []
  },

  onTabChange: onAdminTabChange,

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
    const user = getUser();
    const stats = await getStats();
    this.setData({
      user,
      stats,
      cards: [
        { key: 'pendingCount', label: '待取货数量', value: stats.pendingCount || 0, status: 0, dateScope: '', sortBy: 'createdAt' },
        { key: 'todayAdded', label: '今日新增', value: stats.todayAdded || 0, status: '', dateScope: 'today', sortBy: 'createdAt' },
        { key: 'todayPicked', label: '今日已取', value: stats.todayPicked || 0, status: 1, dateScope: 'today-picked', sortBy: 'pickedAt' },
        { key: 'totalCount', label: '总包裹', value: stats.totalCount || 0, status: '', dateScope: '', sortBy: 'createdAt' },
        ...(Number(user.role) === 0
          ? [{ key: 'storeCount', label: '门店数量', value: stats.storeCount || 0, disabled: true }]
          : [])
      ],
      processingCards: [
        { key: 'waitingCount', label: '今日待加工', value: stats.waitingCount || 0, view: 'today-waiting' },
        { key: 'overdueCount', label: '逾期未开工', value: stats.overdueCount || 0, view: 'overdue' },
        { key: 'processingCount', label: '加工中', value: stats.processingCount || 0, view: 'processing' },
        { key: 'todayFinished', label: '今日完成', value: stats.todayFinished || 0, view: 'today-finished' }
      ]
    });
  },

  goPrescriptions() {
    wx.navigateTo({ url: '/pages/admin/prescriptions/prescriptions' });
  },

  goProcessingView(e) {
    const view = e.currentTarget.dataset.view;
    wx.navigateTo({
      url: `/pages/admin/processing-workbench/processing-workbench?view=${encodeURIComponent(view)}`
    });
  },

  goTransfers() {
    wx.navigateTo({ url: '/pages/admin/store-transfers/store-transfers' });
  },

  goHerbLocations() {
    wx.navigateTo({ url: '/pages/admin/herb-locations/herb-locations' });
  },

  goProductDifferences() {
    wx.navigateTo({ url: '/pages/admin/product-differences/product-differences' });
  },

  goE6Inventory() {
    wx.navigateTo({ url: '/pages/admin/e6-inventory/e6-inventory' });
  },

  goGoodsChecks() {
    wx.navigateTo({ url: '/pages/admin/yd-goods-checks/yd-goods-checks' });
  },

  goPackageList(e) {
    const { status, dateScope, sortBy, disabled } = e.currentTarget.dataset;
    if (disabled) return;
    const params = [];
    if (status !== undefined && status !== '') params.push(`status=${status}`);
    if (dateScope) params.push(`dateScope=${dateScope}`);
    if (sortBy) params.push(`sortBy=${sortBy}`);
    const query = params.length ? `?${params.join('&')}` : '';
    wx.navigateTo({ url: `/pages/admin/packages/packages${query}` });
  }
});
