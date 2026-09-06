import {
  addStoreTransferReturns,
  cancelStoreTransfer,
  confirmStoreTransferOutbound,
  confirmStoreTransferReturn,
  createStoreTransfer,
  getStoreTransfer,
  getStoreTransferStats,
  getStoreTransfers,
  getTransferStores,
  updateExpectedReturnDate,
  updateStoreTransfer,
  updateStoreTransferReturn
} from '../../../api/admin';
import { getUser } from '../../../utils/auth';
import { clearResponseCache } from '../../../utils/request';
import { copyToClipboard } from '../../../utils/wechat';


const TRANSFER_STATUS = Object.freeze({
  BORROWING: 0,
  PART_RETURNED: 1,
  RETURNED: 2,
  CANCELLED: 3
});
const TRANSFER_OUTBOUND_STATUS = Object.freeze({ PENDING: 0, CONFIRMED: 1 });
const TRANSFER_RETURN_STATUS = Object.freeze({ PENDING: 0, CONFIRMED: 1 });

const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '借出中', value: TRANSFER_STATUS.BORROWING },
  { label: '部分归还', value: TRANSFER_STATUS.PART_RETURNED },
  { label: '已调平', value: TRANSFER_STATUS.RETURNED },
  { label: '已取消', value: TRANSFER_STATUS.CANCELLED }
];

function localDate(offset = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offset);
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function statusType(status) {
  return {
    [TRANSFER_STATUS.BORROWING]: 'primary',
    [TRANSFER_STATUS.PART_RETURNED]: 'warning',
    [TRANSFER_STATUS.RETURNED]: 'success',
    [TRANSFER_STATUS.CANCELLED]: 'default'
  }[Number(status)] || 'default';
}

function statusText(status) {
  return STATUS_OPTIONS.find((item) => item.value === Number(status))?.label || status || '-';
}

function operatorName(operator) {
  return operator?.nickname || operator?.name || operator?.phone || '-';
}

function numberText(value) {
  const number = Number(value || 0);
  return Number.isInteger(number) ? String(number) : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

function emptyItem() {
  return { itemName: '', specification: '', batchNo: '', quantity: 1, unit: '', remark: '' };
}

function decorateDetail(detail) {
  if (!detail) return detail;
  return {
    ...detail,
    outboundPending: Number(detail.outboundStatus) === TRANSFER_OUTBOUND_STATUS.PENDING,
    statusLabel: statusText(detail.status),
    statusTheme: statusType(detail.status),
    transferDateText: detail.transferDate ? String(detail.transferDate).slice(0, 10) : '-',
    expectedReturnDateText: detail.expectedReturnDate ? String(detail.expectedReturnDate).slice(0, 10) : '-',
    creatorName: operatorName(detail.creator),
    items: (detail.items || []).map((item) => ({
      ...item,
      quantityText: numberText(item.quantity),
      remainingQuantityText: numberText(item.remainingQuantity)
    })),
    returnRecords: (detail.returnRecords || []).map((item) => ({
      ...item,
      quantityText: numberText(item.quantity),
      returnDateText: item.returnDate ? String(item.returnDate).slice(0, 10) : '-',
      statusText: Number(item.status) === TRANSFER_RETURN_STATUS.CONFIRMED ? '已确认' : '待确认',
      canConfirm: Number(item.status) === TRANSFER_RETURN_STATUS.PENDING
    }))
  };
}

Page({
  data: {
    initialTransferId: null,
    user: {},
    isSuperAdmin: false,
    keyword: '',
    status: '',
    statusOptions: STATUS_OPTIONS,
    statusIndex: 0,
    stats: { borrowing: 0, partReturned: 0, pending: 0, overdue: 0 },
    stores: [],
    storeIndex: 0,
    storeId: '',
    fromStoreIndex: 0,
    toStoreIndex: 0,
    fromStoreName: '请选择',
    toStoreName: '请选择',
    page: 1,
    pageSize: 8,
    pages: 1,
    list: [],
    loading: false,
    saving: false,
    createVisible: false,
    formMode: 'create',
    detailVisible: false,
    returnVisible: false,
    returnMode: 'create',
    editingReturnId: null,
    dateVisible: false,
    detail: null,
    createForm: { fromStoreId: '', toStoreId: '', transferDate: localDate(), expectedReturnDate: localDate(7), remark: '', items: [emptyItem()] },
    returnForm: { returnDate: localDate(), remark: '', items: [] },
    dateValue: localDate(7)
  },

  async onPullDownRefresh() {
    clearResponseCache();
    try {
      await this.reload();
      if (this.data.detail?.id) await this.loadTransferDetail(this.data.detail.id);
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  onLoad(options = {}) {
    const transferId = Number(options.transferId);
    if (transferId) this.setData({ initialTransferId: transferId });
  },

  async onShow() {
    const user = getUser();
    const isSuperAdmin = Number(user.role) === 0;
    this.setData({ user, isSuperAdmin });
    try {
      if (!this.data.stores.length) {
        const stores = await getTransferStores();
        this.setData({ stores: stores || [] });
      }
      await this.reload();
      if (this.data.initialTransferId && !this.data.detail) {
        await this.loadTransferDetail(this.data.initialTransferId);
      }
    } catch (error) {
      console.error('load store transfers failed', error);
    }
  },

  async reload() {
    await Promise.all([this.loadList(), this.loadStats()]);
  },

  async loadList() {
    this.setData({ loading: true });
    try {
      const data = await getStoreTransfers({
        keyword: this.data.keyword.trim(),
        status: this.data.status,
        ...(this.data.isSuperAdmin && this.data.storeId ? { storeId: this.data.storeId } : {}),
        page: this.data.page,
        pageSize: this.data.pageSize
      });
      this.setData({
        list: (data?.list || []).map((item) => ({
          ...item,
          outboundPending: Number(item.outboundStatus) === TRANSFER_OUTBOUND_STATUS.PENDING,
          statusLabel: statusText(item.status),
          statusTheme: statusType(item.status),
          itemSummary: (item.items || []).slice(0, 2).map((row) => row.itemName).join('、') || '-',
          itemCount: (item.items || []).length,
          transferDateText: item.transferDate ? String(item.transferDate).slice(0, 10) : '-',
          expectedReturnDateText: item.expectedReturnDate ? String(item.expectedReturnDate).slice(0, 10) : '-'
        })),
        pages: data?.pagination?.pages || 1
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  async loadStats() {
    const stats = await getStoreTransferStats(
      this.data.isSuperAdmin && this.data.storeId ? { storeId: this.data.storeId } : {}
    );
    this.setData({ stats: { ...this.data.stats, ...(stats || {}) } });
  },

  onKeywordChange(e) { this.setData({ keyword: e.detail.value }); },
  search() { this.setData({ page: 1 }); this.loadList(); },

  onStatusChange(e) {
    const statusIndex = Number(e.detail.value);
    this.setData({ statusIndex, status: STATUS_OPTIONS[statusIndex].value, page: 1 });
    this.loadList();
  },

  onStoreChange(e) {
    const index = Number(e.detail.value);
    const store = this.data.stores[index];
    this.setData({ storeIndex: index, storeId: store?.id || '', page: 1 });
    this.loadList();
  },

  prevPage() {
    if (this.data.page <= 1) return;
    this.setData({ page: this.data.page - 1 });
    this.loadList();
  },

  nextPage() {
    if (this.data.page >= this.data.pages) return;
    this.setData({ page: this.data.page + 1 });
    this.loadList();
  },

  openCreate() {
    const currentStoreId = Number(this.data.user.storeId) || '';
    const currentStoreIndex = Math.max(0, this.data.stores.findIndex((item) => Number(item.id) === currentStoreId));
    this.setData({
      createVisible: true,
      formMode: 'create',
      createForm: {
        fromStoreId: '',
        toStoreId: this.data.isSuperAdmin ? '' : currentStoreId,
        transferDate: localDate(),
        expectedReturnDate: localDate(7),
        remark: '',
        items: [emptyItem()]
      },
      fromStoreIndex: 0,
      toStoreIndex: currentStoreIndex,
      fromStoreName: '请选择',
      toStoreName: this.data.isSuperAdmin ? '请选择' : this.storeNameById(currentStoreId)
    });
  },

  closeCreate() {
    this.setData({
      createVisible: false,
      detailVisible: this.data.formMode === 'edit' && Boolean(this.data.detail)
    });
  },

  onCopyTransferNo(e) {
    const no = e.currentTarget.dataset.no;
    if (!no) return;
    copyToClipboard(no, '调拨单号');
  },


  openEdit() {
    const detail = this.data.detail;
    if (!detail?.permissions?.canUpdate) return;
    const fromStoreIndex = Math.max(0, this.data.stores.findIndex((item) => Number(item.id) === Number(detail.fromStoreId)));
    const toStoreIndex = Math.max(0, this.data.stores.findIndex((item) => Number(item.id) === Number(detail.toStoreId)));
    this.setData({
      createVisible: true,
      detailVisible: false,
      formMode: 'edit',
      fromStoreIndex,
      toStoreIndex,
      fromStoreName: this.storeNameById(detail.fromStoreId),
      toStoreName: this.storeNameById(detail.toStoreId),
      createForm: {
        fromStoreId: Number(detail.fromStoreId),
        toStoreId: Number(detail.toStoreId),
        transferDate: String(detail.transferDate || '').slice(0, 10),
        expectedReturnDate: String(detail.expectedReturnDate || '').slice(0, 10),
        remark: detail.remark || '',
        items: (detail.items || []).map((item) => ({
          itemName: item.itemName || '',
          specification: item.specification || '',
          batchNo: item.batchNo || '',
          quantity: Number(item.quantity),
          unit: item.unit || '',
          remark: item.remark || ''
        }))
      }
    });
  },

  onCreateFieldChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`createForm.${field}`]: e.detail.value });
  },

  onCreateStoreChange(e) {
    const field = e.currentTarget.dataset.field;
    const index = Number(e.detail.value);
    const store = this.data.stores[index];
    this.setData({
      [`createForm.${field}`]: store?.id || '',
      [`${field === 'fromStoreId' ? 'fromStoreIndex' : 'toStoreIndex'}`]: index,
      [`${field === 'fromStoreId' ? 'fromStoreName' : 'toStoreName'}`]: store?.name || '请选择'
    });
  },

  onCreateDateChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`createForm.${field}`]: e.detail.value });
  },

  onCreateItemChange(e) {
    const { index, field } = e.currentTarget.dataset;
    this.setData({ [`createForm.items[${Number(index)}].${field}`]: e.detail.value });
  },

  addItem() { this.setData({ 'createForm.items': [...this.data.createForm.items, emptyItem()] }); },

  removeItem(e) {
    if (this.data.createForm.items.length <= 1) return;
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ 'createForm.items': this.data.createForm.items.filter((_, itemIndex) => itemIndex !== index) });
  },

  async saveCreate() {
    const form = this.data.createForm;
    if (!form.fromStoreId || !form.toStoreId) return wx.showToast({ title: '请选择调出和调入门店', icon: 'none' });
    if (Number(form.fromStoreId) === Number(form.toStoreId)) return wx.showToast({ title: '两个门店不能相同', icon: 'none' });
    if (form.expectedReturnDate < form.transferDate) return wx.showToast({ title: '预计归还日期不能早于调拨日期', icon: 'none' });
    if (form.items.some((item) => !item.itemName.trim() || !item.unit.trim() || Number(item.quantity) <= 0)) {
      return wx.showToast({ title: '请填写名称、数量和单位', icon: 'none' });
    }
    this.setData({ saving: true });
    try {
      const payload = { ...form, fromStoreId: Number(form.fromStoreId), toStoreId: Number(form.toStoreId) };
      if (this.data.formMode === 'edit') {
        const detail = decorateDetail(await updateStoreTransfer(this.data.detail.id, payload));
        this.setData({ detail, createVisible: false, detailVisible: true });
        wx.showToast({ title: '调拨已修改', icon: 'success' });
      } else {
        await createStoreTransfer(payload);
        wx.showToast({ title: '调拨申请已提交', icon: 'success' });
        this.setData({ createVisible: false });
      }
      await this.reload();
    } finally { this.setData({ saving: false }); }
  },

  async openDetail(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    this.loadTransferDetail(id);
  },

  async loadTransferDetail(id) {
    this.setData({ detailVisible: true, detail: null, loading: true });
    try { this.setData({ detail: decorateDetail(await getStoreTransfer(id)) }); }
    finally { this.setData({ loading: false }); }
  },

  closeDetail() {
    this.setData({ detailVisible: false, detail: null, initialTransferId: null });
  },

  async confirmOutbound() {
    const detail = this.data.detail;
    if (!detail?.permissions?.canConfirmOutbound) return;
    const result = await new Promise((resolve) => wx.showModal({ title: '确认调出', content: `确认调出 ${detail.transferNo} 吗？`, success: resolve }));
    if (!result.confirm) return;
    this.setData({ saving: true });
    try { this.setData({ detail: decorateDetail(await confirmStoreTransferOutbound(detail.id)) }); await this.reload(); }
    finally { this.setData({ saving: false }); }
  },

  openReturn() {
    const detail = this.data.detail;
    if (!detail?.permissions?.canSubmitReturn) return;
    this.setData({
      returnVisible: true,
      returnMode: 'create',
      editingReturnId: null,
      returnForm: {
        returnDate: localDate(),
        remark: '',
        items: (detail.items || []).filter((item) => Number(item.availableReturnQuantity) > 0).map((item) => ({
          transferItemId: item.id,
          itemName: item.itemName,
          unit: item.unit,
          available: item.availableReturnQuantity,
          quantity: 0
        }))
      }
    });
  },

  openReturnEdit(e) {
    const row = this.data.detail.returnRecords[Number(e.currentTarget.dataset.index)];
    if (
      !row ||
      Number(row.status) !== TRANSFER_RETURN_STATUS.PENDING ||
      !this.data.detail?.permissions?.canSubmitReturn
    )
      return;
    const item = this.data.detail.items.find((candidate) => Number(candidate.id) === Number(row.transferItemId));
    this.setData({
      returnVisible: true,
      returnMode: 'edit',
      editingReturnId: row.id,
      returnForm: {
        returnDate: String(row.returnDate || '').slice(0, 10),
        remark: row.remark || '',
        items: [{
          transferItemId: row.transferItemId,
          itemName: row.itemName,
          unit: item?.unit || '',
          available: Math.round((Number(item?.availableReturnQuantity || 0) + Number(row.quantity)) * 1000) / 1000,
          quantity: Number(row.quantity)
        }]
      }
    });
  },

  onReturnDateChange(e) { this.setData({ 'returnForm.returnDate': e.detail.value }); },
  onReturnRemarkChange(e) { this.setData({ 'returnForm.remark': e.detail.value }); },
  onReturnQuantityChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ [`returnForm.items[${index}].quantity`]: e.detail.value });
  },

  async saveReturn() {
    const form = this.data.returnForm;
    const items = form.items.filter((item) => Number(item.quantity) > 0).map((item) => ({ transferItemId: item.transferItemId, quantity: Number(item.quantity) }));
    if (!items.length) return wx.showToast({ title: '请填写归还数量', icon: 'none' });
    if (items.some((item) => item.quantity > Number(form.items.find((row) => row.transferItemId === item.transferItemId).available))) return wx.showToast({ title: '归还数量不能超过可归还数量', icon: 'none' });
    this.setData({ saving: true });
    try {
      const detail = this.data.returnMode === 'edit'
        ? await updateStoreTransferReturn(this.data.detail.id, this.data.editingReturnId, {
          returnDate: form.returnDate,
          remark: form.remark,
          quantity: items[0].quantity
        })
        : await addStoreTransferReturns(this.data.detail.id, { returnDate: form.returnDate, remark: form.remark, items });
      this.setData({ detail: decorateDetail(detail), returnVisible: false });
      wx.showToast({ title: this.data.returnMode === 'edit' ? '归还记录已修改' : '归还申请已提交', icon: 'success' });
      await this.reload();
    } finally { this.setData({ saving: false }); }
  },

  async confirmReturn(e) {
    const row = this.data.detail.returnRecords[Number(e.currentTarget.dataset.index)];
    if (
      !this.data.detail?.permissions?.canConfirmReturn ||
      Number(row.status) !== TRANSFER_RETURN_STATUS.PENDING
    )
      return;
    const result = await new Promise((resolve) => wx.showModal({ title: '确认收货', content: `确认收到 ${row.itemName} ${numberText(row.quantity)} 吗？`, success: resolve }));
    if (!result.confirm) return;
    this.setData({ saving: true });
    try { this.setData({ detail: decorateDetail(await confirmStoreTransferReturn(this.data.detail.id, row.id)) }); await this.reload(); }
    finally { this.setData({ saving: false }); }
  },

  openDateEditor() {
    this.setData({ dateVisible: true, dateValue: String(this.data.detail.expectedReturnDate || '').slice(0, 10) });
  },
  onDateEditorChange(e) { this.setData({ dateValue: e.detail.value }); },
  async saveExpectedDate() {
    if (!this.data.dateValue) return;
    this.setData({ saving: true });
    try { this.setData({ detail: decorateDetail(await updateExpectedReturnDate(this.data.detail.id, { expectedReturnDate: this.data.dateValue })), dateVisible: false }); await this.reload(); }
    finally { this.setData({ saving: false }); }
  },

  async cancelTransfer() {
    const input = await new Promise((resolve) => wx.showModal({ title: '取消调拨', editable: true, placeholderText: '请输入取消原因', content: `确认取消 ${this.data.detail.transferNo} 吗？`, success: resolve }));
    if (!input.confirm) return;
    this.setData({ saving: true });
    try { this.setData({ detail: decorateDetail(await cancelStoreTransfer(this.data.detail.id, { reason: input.content || '管理员取消' })) }); await this.reload(); }
    finally { this.setData({ saving: false }); }
  },

  closeReturn() { this.setData({ returnVisible: false }); },
  closeDateEditor() { this.setData({ dateVisible: false }); },
  noop() {},
  storeNameById(id) { return this.data.stores.find((store) => Number(store.id) === Number(id))?.name || '请选择'; },

  formatDate(value) { return value ? String(value).slice(0, 10) : '-'; },
  statusText,
  numberText
});
