import { getPackages, getStores } from '../../../api/admin';
import { formatDate, formatPickupCode, maskPhone, pickupMethodText, statusText, statusTheme } from '../../../utils/format';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';
import { clearResponseCache } from '../../../utils/request';

function isPendingStatus(status) {
  return Number(status) === 0;
}

Page({
  data: {
    activeTab: 'packages',
    keyword: '',
    status: '',
    dateScope: '',
    sortBy: 'createdAt',
    sortByText: '录入时间',
    isSuperAdmin: false,
    stores: [],
    storeIndex: 0,
    storeId: '',
    storeName: '全部门店',
    page: 1,
    pageSize: 15,
    pages: 1,
    loading: false,
    loadingMore: false,
    list: []
  },

  onTabChange: onAdminTabChange,

  onLoad(options = {}) {
    const status = ['0', '1'].includes(String(options.status)) ? Number(options.status) : '';
    const dateScope = ['today', 'today-picked', 'overdue', 'dashboard', 'pickup-workbench'].includes(options.dateScope)
      ? options.dateScope
      : '';
    const sortBy = ['createdAt', 'pickedAt'].includes(options.sortBy) ? options.sortBy : 'createdAt';
    this.setData({
      status,
      dateScope,
      sortBy,
      sortByText: sortBy === 'pickedAt' ? '取货时间' : '录入时间'
    });
  },

  async onShow() {
    const user = getUser();
    const isSuperAdmin = Number(user.role) === 0;
    this.setData({ isSuperAdmin });
    if (isSuperAdmin && !this.data.stores.length) {
      const data = await getStores({ page: 1, pageSize: 100 });
      this.setData({ stores: [{ id: '', name: '全部门店' }, ...(data.list || [])] });
    }
    await this.load();
  },

  async onPullDownRefresh() {
    clearResponseCache();
    this.setData({ page: 1 });
    try {
      await this.load(1);
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  async load(page = 1) {
    this.setData({ loading: true, page });
    try {
      const data = await getPackages({
        keyword: this.data.keyword,
        status: this.data.status,
        dateScope: this.data.dateScope,
        sortBy: this.data.sortBy,
        sortOrder: 'desc',
        storeId: this.data.storeId,
        page,
        pageSize: this.data.pageSize
      });

      this.setData({
        list: (data.list || []).map((item) => ({
          ...item,
          pickupCode: formatPickupCode(item.pickupCode),
          storeName: item.store ? item.store.name : '',
          receiverPhoneMasked: maskPhone(item.receiverPhone),
          createdAtText: formatDate(item.createdAt),
          pickedAtText: formatDate(item.pickedAt),
          pickupMethodText: pickupMethodText(item.pickupMethod),
          statusText: statusText(item.status),
          statusTheme: statusTheme(item.status),
          isPending: isPendingStatus(item.status)
        })),
        pages: data.pagination?.pages || 1
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  onKeywordChange(e) {
    this.setData({ keyword: e.detail.value });
  },

  search() {
    this.setData({ page: 1 });
    this.load();
  },

  setAll() {
    this.setData({ status: '', dateScope: '', page: 1 });
    this.load();
  },

  setPending() {
    this.setData({ status: 0, dateScope: '', page: 1 });
    this.load();
  },

  setPicked() {
    this.setData({ status: 1, dateScope: '', page: 1 });
    this.load();
  },

  toggleSort() {
    const next = this.data.sortBy === 'createdAt' ? 'pickedAt' : 'createdAt';
    this.setData({ sortBy: next, sortByText: next === 'createdAt' ? '录入时间' : '取货时间', page: 1 });
    this.load();
  },

  onStoreChange(e) {
    const storeId = e.detail.storeId !== undefined
      ? e.detail.storeId
      : (this.data.stores[Number(e.detail.value)]?.id || '');
    this.setData({
      storeId,
      page: 1
    });
    this.load();
  },

  async onReachBottom() {
    if (this.data.loading || this.data.loadingMore || this.data.page >= this.data.pages) return;
    this.setData({ loadingMore: true });
    try {
      const nextPage = this.data.page + 1;
      const data = await getPackages({
        keyword: this.data.keyword,
        status: this.data.status,
        dateScope: this.data.dateScope,
        sortBy: this.data.sortBy,
        sortOrder: 'desc',
        storeId: this.data.storeId,
        page: nextPage,
        pageSize: this.data.pageSize
      });
      const newItems = (data.list || []).map((item) => ({
        ...item,
        pickupCode: formatPickupCode(item.pickupCode),
        storeName: item.store ? item.store.name : '',
        receiverPhoneMasked: maskPhone(item.receiverPhone),
        createdAtText: formatDate(item.createdAt),
        pickedAtText: formatDate(item.pickedAt),
        pickupMethodText: pickupMethodText(item.pickupMethod),
        statusText: statusText(item.status),
        statusTheme: statusTheme(item.status),
        isPending: isPendingStatus(item.status)
      }));
      this.setData({
        list: this.data.list.concat(newItems),
        page: nextPage,
        pages: data.pagination?.pages || 1
      });
    } finally {
      this.setData({ loadingMore: false });
    }
  },

  goDetail(e) {
    wx.navigateTo({ url: `/pages/admin/package-detail/package-detail?id=${e.currentTarget.dataset.id}` });
  },

  goEdit(e) {
    wx.navigateTo({ url: `/pages/admin/package-edit/package-edit?id=${e.currentTarget.dataset.id}` });
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/admin/package-form/package-form' });
  },

  goVerify() {
    wx.navigateTo({ url: '/pages/admin/verify/verify' });
  }
});
