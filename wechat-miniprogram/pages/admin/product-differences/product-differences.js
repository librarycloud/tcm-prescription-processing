import {
  getProductDifferenceLogs,
  getProductDifferenceStats,
  getProducts,
  getStores,
  registerProductDifference,
  reverseProductDifference,
  writeOffProductDifference
} from '../../../api/admin';
import { getUser } from '../../../utils/auth';
import { clearResponseCache } from '../../../utils/request';

const REGISTER_TYPES = [
  { value: 'PRE_RECEIPT', label: '先到货未入库' },
  { value: 'PRE_SHIPMENT', label: '先出货未销库' }
];
const OPERATION_OPTIONS = [
  { value: '', label: '全部类型' },
  { value: 'PRE_RECEIPT', label: '先到货未入库' },
  { value: 'PRE_SHIPMENT', label: '先出货未销库' },
  { value: 'WRITE_OFF_RECEIPT', label: '入库销账' },
  { value: 'WRITE_OFF_SHIPMENT', label: '销库销账' },
  { value: 'IMPORT_OPENING', label: '导入期初差异' },
  { value: 'IMPORT_ADJUSTMENT', label: '导入调整' },
  { value: 'REVERSAL', label: '冲销' }
];

function today() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function numberText(value) {
  const number = Number(value || 0);
  return Number.isInteger(number) ? String(number) : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

function signed(value) {
  const number = Number(value || 0);
  return number > 0 ? `+${numberText(number)}` : numberText(number);
}

function operationTheme(value) {
  if (value === 'REVERSAL') return 'default';
  if (String(value).includes('WRITE_OFF')) return 'success';
  if (value === 'PRE_SHIPMENT') return 'danger';
  if (String(value).includes('IMPORT')) return 'primary';
  return 'warning';
}

function operationText(value) {
  return OPERATION_OPTIONS.find((item) => item.value === value)?.label || value || '-';
}

function decorateProduct(item) {
  return {
    ...item,
    retailPriceText: Number(item.retailPrice || 0).toFixed(2),
    signedDiff: signed(item.diffQuantity)
  };
}

function decorateLog(item) {
  return {
    ...item,
    businessDateText: String(item.businessDate || '').slice(0, 10) || '-',
    signedChange: signed(item.changeQuantity),
    signedBalance: signed(item.balanceAfter),
    operationText: operationText(item.operationType),
    operationTheme: operationTheme(item.operationType),
    creatorName: item.creator?.name || item.creator?.nickname || item.creator?.phone || '-',
    canReverse: item.operationType !== 'REVERSAL' && !(item.childLogs || []).length
  };
}

function selectedStore(stores, index) {
  return stores[index] || null;
}

Page({
  data: {
    activeTab: 'current',
    user: {},
    isSuperAdmin: false,
    stores: [],
    storeIndex: 0,
    storeName: '全部门店',
    logStoreIndex: 0,
    logStoreName: '全部门店',
    direction: '',
    keyword: '',
    stats: { total: 0, more: 0, less: 0 },
    list: [],
    loading: false,
    page: 1,
    pages: 1,
    logs: [],
    logsLoading: false,
    logKeyword: '',
    operationOptions: OPERATION_OPTIONS,
    operationIndex: 0,
    logPage: 1,
    logPages: 1,
    registerTypes: REGISTER_TYPES,
    registerTypeIndex: 0,
    registerVisible: false,
    registerStoreIndex: 0,
    registerStoreName: '请选择门店',
    productKeyword: '',
    productOptions: [],
    registerForm: { storeId: null, operationType: 'PRE_RECEIPT', businessDate: today(), supplierName: '', borrowerName: '', remark: '', items: [] },
    writeOffVisible: false,
    selectedProduct: {},
    writeOffForm: { storeId: null, productId: null, businessDate: today(), quantity: 1, systemDocumentNo: '', borrowerName: '', remark: '' },
    reverseVisible: false,
    selectedLog: {},
    reverseReason: '',
    saving: false
  },

  async onPullDownRefresh() {
    clearResponseCache();
    try {
      if (this.data.activeTab === 'logs') await this.loadLogs();
      else await this.loadCurrent();
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  onShow() {
    this.loadBase();
  },

  async loadBase() {
    const user = getUser() || {};
    const isSuperAdmin = Number(user.role) === 0;
    this.setData({ user, isSuperAdmin });
    if (!this.data.stores.length && isSuperAdmin) {
      const data = await getStores({ page: 1, pageSize: 100, status: 1 });
      const stores = [{ id: '', name: '全部门店' }, ...(data?.list || [])];
      this.setData({ stores });
    }
    await this.loadCurrent();
  },

  storeId() {
    if (this.data.isSuperAdmin) return selectedStore(this.data.stores, this.data.storeIndex)?.id || '';
    return this.data.user.storeId || '';
  },

  async loadCurrent() {
    this.setData({ loading: true });
    try {
      const params = { page: this.data.page, pageSize: 10, keyword: this.data.keyword, onlyDifference: '1', direction: this.data.direction };
      if (this.storeId()) params.storeId = this.storeId();
      const [data, stats] = await Promise.all([getProducts(params), getProductDifferenceStats(this.storeId() ? { storeId: this.storeId() } : {})]);
      this.setData({ list: (data?.list || []).map(decorateProduct), pages: Math.max(data?.pagination?.pages || 1, 1), stats: stats || { total: 0, more: 0, less: 0 } });
    } finally {
      this.setData({ loading: false });
    }
  },

  showCurrent() { this.setData({ activeTab: 'current' }); this.loadCurrent(); },

  showLogs() { this.setData({ activeTab: 'logs', logPage: 1 }); this.loadLogs(); },

  setDirection(e) { this.setData({ direction: e.currentTarget.dataset.direction || '', page: 1 }); this.loadCurrent(); },

  onKeywordChange(e) { this.setData({ keyword: e.detail.value || '' }); },
  searchCurrent() { this.setData({ page: 1 }); this.loadCurrent(); },

  onStoreChange(e) {
    const index = Number(e.detail.value);
    this.setData({ storeIndex: index, storeName: selectedStore(this.data.stores, index)?.name || '全部门店', page: 1 });
    this.loadCurrent();
  },

  onLogStoreChange(e) {
    const index = Number(e.detail.value);
    this.setData({ logStoreIndex: index, logStoreName: selectedStore(this.data.stores, index)?.name || '全部门店', logPage: 1 });
    this.loadLogs();
  },

  async loadLogs() {
    this.setData({ logsLoading: true });
    try {
      const store = selectedStore(this.data.stores, this.data.logStoreIndex);
      const data = await getProductDifferenceLogs({ page: this.data.logPage, pageSize: 10, keyword: this.data.logKeyword, operationType: this.data.operationOptions[this.data.operationIndex].value, ...(store ? { storeId: store.id } : {}) });
      this.setData({ logs: (data?.list || []).map(decorateLog), logPages: Math.max(data?.pagination?.pages || 1, 1) });
    } finally {
      this.setData({ logsLoading: false });
    }
  },

  onLogKeywordChange(e) { this.setData({ logKeyword: e.detail.value || '' }); },
  searchLogs() { this.setData({ logPage: 1 }); this.loadLogs(); },
  onOperationChange(e) { this.setData({ operationIndex: Number(e.detail.value), logPage: 1 }); this.loadLogs(); },
  openProductLogs(e) { const item = this.data.list[e.currentTarget.dataset.index]; this.setData({ activeTab: 'logs', logPage: 1, logKeyword: item.productCode }); this.loadLogs(); },

  prevPage() { if (this.data.page > 1) { this.setData({ page: this.data.page - 1 }); this.loadCurrent(); } },
  nextPage() { if (this.data.page < this.data.pages) { this.setData({ page: this.data.page + 1 }); this.loadCurrent(); } },
  prevLogPage() { if (this.data.logPage > 1) { this.setData({ logPage: this.data.logPage - 1 }); this.loadLogs(); } },
  nextLogPage() { if (this.data.logPage < this.data.logPages) { this.setData({ logPage: this.data.logPage + 1 }); this.loadLogs(); } },

  emptyItem() { return { key: `${Date.now()}-${Math.random()}`, productId: null, productIndex: 0, productLabel: '', quantity: 1, batchNote: '' }; },

  openRegister() {
    const store = this.data.isSuperAdmin ? selectedStore(this.data.stores, this.data.storeIndex) : null;
    this.setData({ registerVisible: true, registerStoreIndex: this.data.storeIndex, registerStoreName: store?.name || (this.data.isSuperAdmin ? '请选择门店' : this.data.user.storeName || '当前门店'), registerTypeIndex: 0, productKeyword: '', productOptions: [], registerForm: { storeId: store?.id || this.data.user.storeId || null, operationType: 'PRE_RECEIPT', businessDate: today(), supplierName: '', borrowerName: '', remark: '', items: [this.emptyItem()] } });
    this.loadProducts();
  },

  closeRegister() { this.setData({ registerVisible: false }); },
  noop() {},
  onRegisterStoreChange(e) { const index = Number(e.detail.value); const store = selectedStore(this.data.stores, index); this.setData({ registerStoreIndex: index, registerStoreName: store?.name || '请选择门店', 'registerForm.storeId': store?.id || null, 'registerForm.items': [this.emptyItem()] }); this.loadProducts(); },
  onRegisterTypeChange(e) { const index = Number(e.detail.value); this.setData({ registerTypeIndex: index, 'registerForm.operationType': REGISTER_TYPES[index].value }); },
  onRegisterDateChange(e) { this.setData({ 'registerForm.businessDate': e.detail.value }); },
  onRegisterFieldChange(e) { this.setData({ [`registerForm.${e.currentTarget.dataset.field}`]: e.detail.value || '' }); },
  addRegisterItem() { this.setData({ 'registerForm.items': [...this.data.registerForm.items, this.emptyItem()] }); },
  removeRegisterItem(e) { const items = this.data.registerForm.items.filter((_, index) => index !== Number(e.currentTarget.dataset.index)); this.setData({ 'registerForm.items': items }); },
  onRegisterQuantityChange(e) { this.setData({ [`registerForm.items[${e.currentTarget.dataset.index}].quantity`]: e.detail.value }); },
  onRegisterBatchChange(e) { this.setData({ [`registerForm.items[${e.currentTarget.dataset.index}].batchNote`]: e.detail.value || '' }); },
  async loadProducts() { const storeId = this.data.registerForm.storeId; if (!storeId) return; const data = await getProducts({ storeId, keyword: this.data.productKeyword, page: 1, pageSize: 200 }); const productOptions = (data?.list || []).map((item) => ({ ...item, label: `${item.productCode} · ${item.name}${item.specification ? ` · ${item.specification}` : ''}` })); this.setData({ productOptions }); },
  onProductKeywordChange(e) { this.setData({ productKeyword: e.detail.value || '' }); },
  searchProducts() { this.loadProducts(); },
  onProductChange(e) { const index = Number(e.currentTarget.dataset.index); const optionIndex = Number(e.detail.value); const product = this.data.productOptions[optionIndex]; this.setData({ [`registerForm.items[${index}].productId`]: product?.id || null, [`registerForm.items[${index}].productIndex`]: optionIndex, [`registerForm.items[${index}].productLabel`]: product?.label || '' }); },

  async saveRegister() {
    const form = this.data.registerForm;
    if (!form.storeId) return wx.showToast({ title: '请选择门店', icon: 'none' });
    if (form.items.some((item) => !item.productId || Number(item.quantity) <= 0)) return wx.showToast({ title: '请完整填写商品和数量', icon: 'none' });
    if (new Set(form.items.map((item) => item.productId)).size !== form.items.length) return wx.showToast({ title: '商品不能重复', icon: 'none' });
    this.setData({ saving: true });
    try { await registerProductDifference({ ...form, items: form.items.map((item) => ({ productId: item.productId, quantity: item.quantity, batchNote: item.batchNote })) }); wx.showToast({ title: '登记成功', icon: 'success' }); this.closeRegister(); await this.loadCurrent(); } finally { this.setData({ saving: false }); }
  },

  openWriteOff(e) { const product = this.data.list[e.currentTarget.dataset.index]; this.setData({ selectedProduct: product, writeOffVisible: true, writeOffForm: { storeId: product.storeId, productId: product.id, businessDate: today(), quantity: Math.abs(Number(product.diffQuantity)), systemDocumentNo: '', borrowerName: '', remark: '' } }); },
  closeWriteOff() { this.setData({ writeOffVisible: false }); },
  onWriteOffDateChange(e) { this.setData({ 'writeOffForm.businessDate': e.detail.value }); },
  onWriteOffFieldChange(e) { this.setData({ [`writeOffForm.${e.currentTarget.dataset.field}`]: e.detail.value || '' }); },
  async saveWriteOff() { const form = this.data.writeOffForm; if (!Number(form.quantity) || Number(form.quantity) <= 0) return wx.showToast({ title: '请输入销账数量', icon: 'none' }); this.setData({ saving: true }); try { await writeOffProductDifference(form); wx.showToast({ title: '销账成功', icon: 'success' }); this.closeWriteOff(); await this.loadCurrent(); } finally { this.setData({ saving: false }); } },

  openReverse(e) { this.setData({ selectedLog: this.data.logs[e.currentTarget.dataset.index], reverseReason: '', reverseVisible: true }); },
  closeReverse() { this.setData({ reverseVisible: false }); },
  onReverseReasonChange(e) { this.setData({ reverseReason: e.detail.value || '' }); },
  async saveReverse() { if (!this.data.reverseReason.trim()) return wx.showToast({ title: '请输入冲销原因', icon: 'none' }); this.setData({ saving: true }); try { await reverseProductDifference(this.data.selectedLog.id, { reason: this.data.reverseReason }); wx.showToast({ title: '冲销成功', icon: 'success' }); this.closeReverse(); await this.loadLogs(); } finally { this.setData({ saving: false }); } }
});
