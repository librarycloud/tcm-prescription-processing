import {
  deletePrescription,
  getPrescriptions,
  getStores
} from '../../../api/admin';
import { formatDate, maskPhone } from '../../../utils/format';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';

const STATUS_META = {
  0: { text: '进行中', theme: 'primary' },
  1: { text: '已完成', theme: 'success' },
  2: { text: '已取消', theme: 'default' }
};

Page({
  data: {
    activeTab: '',
    loading: false,
    keyword: '',
    status: '',
    isSuperAdmin: false,
    stores: [],
    storeIndex: 0,
    storeId: '',
    storeName: '全部门店',
    page: 1,
    pageSize: 10,
    pages: 1,
    list: []
  },

  onTabChange: onAdminTabChange,

  async onShow() {
    const user = getUser();
    const isSuperAdmin = Number(user.role) === 0;
    this.setData({ isSuperAdmin });
    if (isSuperAdmin && !this.data.stores.length) {
      const data = await getStores({ page: 1, pageSize: 100 });
      this.setData({
        stores: [{ id: '', name: '全部门店' }, ...(data.list || [])]
      });
    }
    await this.load();
  },

  async load() {
    this.setData({ loading: true });
    try {
      const data = await getPrescriptions({
        keyword: this.data.keyword,
        status: this.data.status,
        storeId: this.data.storeId,
        page: this.data.page,
        pageSize: this.data.pageSize
      });
      this.setData({
        list: (data.list || []).map((item) => {
          const status = STATUS_META[Number(item.status)] || STATUS_META[0];
          return {
            ...item,
            phoneMasked: maskPhone(item.phone),
            statusText: status.text,
            statusTheme: status.theme,
            createdAtText: formatDate(item.createdAt),
            storeName: item.store ? item.store.name : '',
            doctorName: item.doctor ? item.doctor.name : '-',
            sourceName: item.source ? item.source.name : '-',
            plansCount: item.plans ? item.plans.length : 0,
            canEdit: Number(item.status) !== 1,
            canDelete: !(item.plans && item.plans.length)
          };
        }),
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

  setStatus(e) {
    const value = e.currentTarget.dataset.status;
    this.setData({ status: value === '' ? '' : Number(value), page: 1 });
    this.load();
  },

  onStoreChange(e) {
    const storeIndex = Number(e.detail.value);
    const store = this.data.stores[storeIndex];
    this.setData({
      storeIndex,
      storeId: store.id,
      storeName: store.name,
      page: 1
    });
    this.load();
  },

  prevPage() {
    if (this.data.page <= 1) return;
    this.setData({ page: this.data.page - 1 });
    this.load();
  },

  nextPage() {
    if (this.data.page >= this.data.pages) return;
    this.setData({ page: this.data.page + 1 });
    this.load();
  },

  goDetail(e) {
    wx.navigateTo({
      url: `/pages/admin/prescription-detail/prescription-detail?id=${e.currentTarget.dataset.id}`
    });
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/admin/prescription-form/prescription-form' });
  },

  goEdit(e) {
    wx.navigateTo({
      url: `/pages/admin/prescription-form/prescription-form?id=${e.currentTarget.dataset.id}`
    });
  },

  remove(e) {
    const id = e.currentTarget.dataset.id;
    const no = e.currentTarget.dataset.no;
    wx.showModal({
      title: '删除处方',
      content: `确认删除处方 ${no}？`,
      confirmColor: '#d54941',
      success: async (result) => {
        if (!result.confirm) return;
        await deletePrescription(id);
        wx.showToast({ title: '已删除', icon: 'success' });
        this.setData({ page: 1 });
        await this.load();
      }
    });
  }
});
