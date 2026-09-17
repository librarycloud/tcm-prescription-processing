import { getE6PharmacyProducts } from '../../../api/admin';
import { getUser } from '../../../utils/auth';

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
    productCode: '',
    productId: null,
    storeId: '',
    loading: false,
    isSuperAdmin: false,
    product: null
  },

  onLoad(options = {}) {
    const user = getUser();
    const isSuperAdmin = Number(user?.role) === 0;
    const productCode = decodeURIComponent(options.productCode || '');
    const productId = Number(options.productId) || null;
    const storeId = decodeURIComponent(options.storeId || '');

    this.setData({
      productCode,
      productId,
      storeId,
      isSuperAdmin
    });

    if (productCode) {
      this.load();
    }
  },

  async onPullDownRefresh() {
    try {
      await this.load(true);
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  async load(forceRefresh = false) {
    if (!this.data.productCode) return;
    this.setData({ loading: true });
    try {
      const data = await getE6PharmacyProducts({
        keyword: this.data.productCode,
        storeId: this.data.storeId || undefined,
        page: 1,
        pageSize: 50,
        forceRefresh
      });
      const list = data?.list || [];
      const matched = this.data.productId
        ? list.find((item) => Number(item.id) === this.data.productId) || list[0]
        : list[0];
      this.setData({
        product: matched ? decorateProduct(matched) : null
      });
      if (matched?.name) {
        wx.setNavigationBarTitle({ title: matched.name });
      }
    } catch (error) {
      wx.showToast({ title: '加载库存失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  }
});
