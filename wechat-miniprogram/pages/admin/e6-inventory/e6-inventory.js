import { getE6PharmacyProducts, getStores } from '../../../api/admin';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';

let searchTimer;
let searchRequestId = 0;

function canSearchKeyword(value) {
  const keyword = String(value || '').trim();
  return (keyword.match(/[\u4e00-\u9fff]/g) || []).length >= 2 || /\d{4}/.test(keyword);
}

function numberText(value) {
  const number = Number(value || 0);
  return Number.isInteger(number) ? String(number) : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

function dateText(value) {
  return value ? String(value).slice(0, 10) : '-';
}

function decorateProduct(product) {
  return {
    ...product,
    totalQuantityText: numberText(product.totalQuantity),
    inventories: (product.inventories || []).map((item) => ({
      ...item,
      quantityText: numberText(item.quantity),
      amountText: Number(item.amount || 0).toFixed(2),
      productionDateText: dateText(item.productionDate),
      expiryDateText: dateText(item.expiryDate),
      inboundDateText: dateText(item.inboundDate)
    }))
  };
}

Page({
  data: {
    activeTab: 'overview',
    isSuperAdmin: false,
    stores: [],
    storeIndex: 0,
    storeId: '',
    storeName: '全部门店',
    keyword: '',
    searchLoading: false,
    detailLoading: false,
    searched: false,
    products: [],
    selectedProduct: null
  },

  onTabChange: onAdminTabChange,

  onKeywordChange(e) {
    const keyword = e.detail.value || '';
    this.setData({ keyword }, () => {
      clearTimeout(searchTimer);
      if (!canSearchKeyword(keyword)) {
        searchRequestId += 1;
        this.setData({ searched: false, products: [], selectedProduct: null });
        return;
      }
      searchTimer = setTimeout(() => this.search(), 300);
    });
  },

  async search(force = false, autoSelect = false) {
    const keyword = this.data.keyword.trim();
    if (!keyword) {
      searchRequestId += 1;
      this.setData({ searched: false, products: [], selectedProduct: null });
      return;
    }
    if (!force && !canSearchKeyword(keyword)) {
      wx.showToast({ title: '请输入至少2个中文或4位数字', icon: 'none' });
      return;
    }
    const requestId = ++searchRequestId;
    this.setData({ searchLoading: true, searched: true, products: [], selectedProduct: null });
    try {
      const data = await getE6PharmacyProducts({ keyword, storeId: this.data.storeId || undefined, page: 1, pageSize: 50 });
      if (requestId !== searchRequestId) return;
      const products = data?.list || [];
      const selectedProduct = autoSelect
        ? products.find((item) => String(item.barcode || '').trim() === keyword) || (products.length === 1 ? products[0] : null)
        : null;
      this.setData({
        products: selectedProduct ? [] : products,
        searched: selectedProduct ? false : true,
        selectedProduct: selectedProduct ? decorateProduct(selectedProduct) : null
      });
    } finally {
      if (requestId === searchRequestId) this.setData({ searchLoading: false });
    }
  },

  async onShow() {
    const user = getUser();
    const isSuperAdmin = Number(user?.role) === 0;
    this.setData({ isSuperAdmin });
    if (isSuperAdmin && !this.data.stores.length) {
      const data = await getStores({ page: 1, pageSize: 100 });
      this.setData({ stores: [{ id: '', name: '全部门店' }, ...(data.list || [])], storeName: '全部门店' });
    }
  },

  onStoreChange(e) {
    const storeIndex = Number(e.detail.value || 0);
    const store = this.data.stores[storeIndex] || {};
    this.setData({ storeIndex, storeId: store.id || '', storeName: store.name || '全部门店', products: [], selectedProduct: null, searched: false });
    if (this.data.keyword.trim()) this.search(true);
  },

  scan() {
    wx.scanCode({
      scanType: ['barCode'],
      success: (res) => {
        this.setData({ keyword: String(res.result || '').trim() }, () => this.search(true, true));
      }
    });
  },

  async selectProduct(e) {
    const product = this.data.products[Number(e.currentTarget.dataset.index)];
    if (!product) return;
    clearTimeout(searchTimer);
    searchRequestId += 1;
    this.setData({ detailLoading: true, searched: false, products: [], selectedProduct: null });
    try {
      const data = await getE6PharmacyProducts({ keyword: product.productCode, storeId: this.data.storeId || undefined, page: 1, pageSize: 50 });
      const detail = (data?.list || []).find((item) => item.id === product.id);
      if (!detail) return wx.showToast({ title: '商品库存已更新，请重新查询', icon: 'none' });
      this.setData({ products: [], searched: false, selectedProduct: decorateProduct(detail) });
    } finally {
      this.setData({ detailLoading: false });
    }
  }
});
