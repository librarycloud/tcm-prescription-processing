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
    retailPriceText: Number(product.retailPrice || 0).toFixed(2),
    inventories: (product.inventories || []).map((item) => ({
      ...item,
      quantityText: numberText(item.quantity),
      productionDateText: dateText(item.productionDate),
      expiryDateText: dateText(item.expiryDate),
      inboundDateText: dateText(item.inboundDate)
    }))
  };
}

Page({
  data: {
    initialProductCode: '',
    initialProductId: null,
    initialStoreId: '',
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

  onLoad(options = {}) {
    if (options.productCode) {
      const productCode = String(options.productCode);
      this.setData({
        initialProductCode: productCode,
        initialProductId: Number(options.productId) || null,
        initialStoreId: String(options.storeId || ''),
        storeId: String(options.storeId || ''),
        keyword: productCode
      });
    }
  },

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
      const products = (data?.list || []).map(decorateProduct);
      const exactMatches = products.filter((item) =>
        String(item.productCode || '').trim() === keyword || String(item.barcode || '').trim() === keyword,
      );
      const selectedProduct = this.data.initialProductId
        ? products.find((item) => Number(item.id) === this.data.initialProductId) || null
        : exactMatches.length === 1 ? exactMatches[0] : null;
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
      const stores = [{ id: '', name: '全部门店' }, ...(data.list || [])];
      const storeIndex = Math.max(0, stores.findIndex((item) => String(item.id || '') === this.data.initialStoreId));
      this.setData({ stores, storeIndex, storeName: stores[storeIndex]?.name || '全部门店' });
    }
    if (this.data.initialProductCode && !this.data.selectedProduct) await this.search(true);
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
    wx.navigateTo({
      url: `/pages/admin/e6-inventory/e6-inventory?productCode=${encodeURIComponent(product.productCode)}&productId=${product.id}&storeId=${encodeURIComponent(this.data.storeId || '')}`
    });
  }
});
