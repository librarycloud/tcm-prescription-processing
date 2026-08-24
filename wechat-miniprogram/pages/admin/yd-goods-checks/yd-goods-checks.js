import {
  addInitialGoodsCheckCount,
  createGoodsCheck,
  getGoodsCheckCandidates,
  getGoodsChecks,
  getStores,
  recountGoodsCheckItem
} from '../../../api/admin';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';

let candidateSearchTimer;
let candidateRequestId = 0;

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

function decorateInventory(row) {
  const firstCountQty = row.firstCountQty === null || row.firstCountQty === undefined ? null : Number(row.firstCountQty);
  const recountQty = row.recountQty === null || row.recountQty === undefined ? null : Number(row.recountQty);
  const systemQty = recountQty === null
    ? Number(row.systemQty ?? row.quantity ?? 0)
    : Number(row.recountSystemQty ?? row.quantity ?? 0);
  const effectiveCountQty = recountQty === null ? firstCountQty : recountQty;
  const difference = effectiveCountQty === null ? null : effectiveCountQty - systemQty;
  return {
    ...row,
    productionDateText: dateText(row.productionDate),
    expiryDateText: dateText(row.expiryDate),
    priceText: row.price === null || row.price === undefined ? '-' : Number(row.price).toFixed(2),
    firstCountText: firstCountQty === null ? '-' : numberText(firstCountQty),
    recountText: recountQty === null ? '-' : numberText(recountQty),
    differenceText: difference === null ? '-' : numberText(difference),
  };
}

function candidateProducts(rows) {
  const products = new Map();
  rows.forEach((row) => {
    if (!row.product || products.has(row.product.id)) return;
    const inventories = rows.filter((item) => item.productId === row.product.id && !item.manualBatch);
    const totalQuantity = inventories.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
    products.set(row.product.id, {
      ...row.product,
      inventoryCount: inventories.length,
      batchCount: inventories.length,
      totalQuantity,
      totalQuantityText: numberText(totalQuantity)
    });
  });
  return [...products.values()];
}

Page({
  data: {
    activeTab: 'overview',
    checksLoading: false,
    checks: [],
    selectedCheck: null,
    isSuperAdmin: false,
    isStoreStaff: false,
    stores: [],
    createStoreIndex: 0,
    createStoreId: null,
    createStoreName: '请选择门店',
    createVisible: false,
    checkName: '',
    creating: false,
    keyword: '',
    candidateSearched: false,
    candidateLoading: false,
    candidates: [],
    candidateProducts: [],
    selectedProduct: null,
    selectedInventories: [],
    countVisible: false,
    countForm: { mode: 'initial', itemId: null, product: null, batchNo: '', locationName: '', locationEditing: false, systemQty: '0', countQty: '', manualBatch: false },
    saving: false
  },

  onTabChange: onAdminTabChange,

  async onShow() {
    const user = getUser() || {};
    const isSuperAdmin = Number(user.role) === 0;
    const isStoreStaff = Number(user.role) === 3;
    this.setData({ isSuperAdmin, isStoreStaff });
    if (isSuperAdmin && !this.data.stores.length) {
      const data = await getStores({ page: 1, pageSize: 100, status: 1 });
      this.setData({ stores: data?.list || [] });
    }
    await this.loadChecks();
  },

  async loadChecks() {
    this.setData({ checksLoading: true });
    try {
      const data = await getGoodsChecks({ page: 1, pageSize: 50 });
      const checks = (data?.list || []).filter((item) => Number(item.status) !== 2);
      this.setData({ checks });
    } finally {
      this.setData({ checksLoading: false });
    }
  },

  selectCheck(e) {
    const selectedCheck = this.data.checks[Number(e.currentTarget.dataset.index)];
    if (!selectedCheck) return;
    this.setData({
      selectedCheck,
      keyword: '',
      candidateSearched: false,
      candidates: [],
      candidateProducts: [],
      selectedProduct: null,
      selectedInventories: []
    });
  },

  backToChecks() {
    this.setData({ selectedCheck: null, candidateSearched: false, candidates: [], candidateProducts: [], selectedProduct: null, selectedInventories: [] });
  },

  openCreate() {
    if (this.data.isStoreStaff) return;
    this.setData({ createVisible: true, checkName: '', createStoreIndex: 0, createStoreId: null, createStoreName: '请选择门店' });
  },

  closeCreate() {
    this.setData({ createVisible: false });
  },

  onCheckNameChange(e) {
    this.setData({ checkName: e.detail.value || '' });
  },

  onCreateStoreChange(e) {
    const createStoreIndex = Number(e.detail.value);
    const store = this.data.stores[createStoreIndex];
    this.setData({ createStoreIndex, createStoreId: store?.id || null, createStoreName: store?.name || '请选择门店' });
  },

  async createCheck() {
    const checkName = this.data.checkName.trim();
    if (!checkName) return wx.showToast({ title: '请输入盘点名称', icon: 'none' });
    const storeId = this.data.isSuperAdmin ? this.data.createStoreId : undefined;
    if (this.data.isSuperAdmin && !storeId) return wx.showToast({ title: '请选择门店', icon: 'none' });
    this.setData({ creating: true });
    try {
      const selectedCheck = await createGoodsCheck({ checkName, checkType: 1, ...(storeId ? { storeId } : {}) });
      this.closeCreate();
      this.setData({ selectedCheck });
      await this.loadChecks();
      wx.showToast({ title: '盘点单已创建', icon: 'success' });
    } finally {
      this.setData({ creating: false });
    }
  },

  onKeywordChange(e) {
    const keyword = e.detail.value || '';
    this.setData({ keyword }, () => {
      clearTimeout(candidateSearchTimer);
      if (!canSearchKeyword(keyword)) {
        candidateRequestId += 1;
        this.setData({ candidateSearched: false, candidates: [], candidateProducts: [], selectedProduct: null, selectedInventories: [] });
        return;
      }
      candidateSearchTimer = setTimeout(() => this.searchCandidates(), 300);
    });
  },

  async searchCandidates(preserveProduct = false, force = false, autoSelect = false) {
    const keyword = this.data.keyword.trim();
    if (!keyword) {
      candidateRequestId += 1;
      this.setData({ candidateSearched: false, candidates: [], candidateProducts: [], selectedProduct: null, selectedInventories: [] });
      return;
    }
    if (!force && !canSearchKeyword(keyword)) {
      wx.showToast({ title: '请输入至少2个中文或4位数字', icon: 'none' });
      return;
    }
    const productId = preserveProduct ? this.data.selectedProduct?.id : null;
    const requestId = ++candidateRequestId;
    this.setData({ candidateLoading: true, candidateSearched: true, candidates: [], candidateProducts: [], selectedProduct: null, selectedInventories: [] });
    try {
      const candidates = await getGoodsCheckCandidates(this.data.selectedCheck.id, { keyword });
      if (requestId !== candidateRequestId) return;
      const products = candidateProducts(candidates || []);
      const exactMatches = products.filter((item) =>
        String(item.productCode || '').trim() === keyword || String(item.barcode || '').trim() === keyword,
      );
      const selectedProduct = productId
        ? products.find((item) => item.id === productId) || null
        : exactMatches.length === 1 ? exactMatches[0] : null;
      const shouldShowInventories = Boolean(selectedProduct);
      this.setData({
        candidates: candidates || [],
        candidateProducts: shouldShowInventories ? [] : products,
        candidateSearched: !shouldShowInventories,
        selectedProduct,
        selectedInventories: shouldShowInventories ? (candidates || []).filter((item) => item.productId === selectedProduct.id && !item.manualBatch).map(decorateInventory) : []
      });
    } finally {
      if (requestId === candidateRequestId) this.setData({ candidateLoading: false });
    }
  },

  search() {
    this.searchCandidates();
  },

  scan() {
    wx.scanCode({
      scanType: ['barCode'],
      success: (res) => {
        this.setData({ keyword: String(res.result || '').trim() }, () => this.searchCandidates(false, true, true));
      }
    });
  },

  selectProduct(e) {
    const selectedProduct = this.data.candidateProducts[Number(e.currentTarget.dataset.index)];
    if (!selectedProduct) return;
    clearTimeout(candidateSearchTimer);
    candidateRequestId += 1;
    this.setData({
      candidateProducts: [],
      candidateSearched: false,
      selectedProduct,
      selectedInventories: this.data.candidates.filter((item) => item.productId === selectedProduct.id && !item.manualBatch).map(decorateInventory)
    });
  },

  openCount(e) {
    if (this.data.isStoreStaff && !this.data.selectedCheck) return;
    const row = this.data.selectedInventories[Number(e.currentTarget.dataset.index)];
    if (!row) return;
    const status = Number(row.checkStatus || 0);
    const isRecount = status === 2 && row.checkItemId;
    if (row.counted && !isRecount) return;
    const systemQty = isRecount && row.recountSystemQty !== null && row.recountSystemQty !== undefined
      ? row.recountSystemQty
      : row.quantity;
    this.setData({
      countVisible: true,
      countForm: {
        mode: isRecount ? 'recount' : 'initial',
        itemId: isRecount ? row.checkItemId : null,
        product: row.product,
        batchNo: row.batchNo || '',
        locationName: row.countLocationName || row.locationName || '',
        locationEditing: false,
        systemQty: numberText(systemQty),
        countQty: numberText(isRecount && row.recountQty !== null && row.recountQty !== undefined ? row.recountQty : (isRecount ? row.firstCountQty : row.quantity)),
        manualBatch: false
      }
    });
  },

  openManualCount() {
    const product = this.data.selectedProduct;
    if (!product) return;
    this.setData({ countVisible: true, countForm: { mode: 'initial', itemId: null, product, batchNo: '', locationName: '', locationEditing: false, systemQty: '0', countQty: '', manualBatch: true } });
  },

  closeCount() {
    this.setData({ countVisible: false });
  },

  noop() {},

  editLocation() {
    this.setData({ 'countForm.locationEditing': true });
  },

  onCountFieldChange(e) {
    this.setData({ [`countForm.${e.currentTarget.dataset.field}`]: e.detail.value || '' });
  },

  async saveCount() {
    const form = this.data.countForm;
    if (form.manualBatch && !form.batchNo.trim()) return wx.showToast({ title: '请输入批号', icon: 'none' });
    if (form.countQty === '' || Number(form.countQty) < 0 || !Number.isFinite(Number(form.countQty))) return wx.showToast({ title: '请输入有效盘点数量', icon: 'none' });
    this.setData({ saving: true });
    try {
      if (form.mode === 'recount') {
        await recountGoodsCheckItem(form.itemId, { recountQty: Number(form.countQty) });
      } else {
        await addInitialGoodsCheckCount(this.data.selectedCheck.id, {
          productId: form.product.id,
          batchNo: form.batchNo,
          locationName: form.locationName || undefined,
          firstCountQty: Number(form.countQty)
        });
      }
      this.closeCount();
      await this.searchCandidates(true);
      await this.loadChecks();
      wx.showToast({ title: form.mode === 'recount' ? '复盘已保存' : '盘点已保存', icon: 'success' });
    } finally {
      this.setData({ saving: false });
    }
  }
});
