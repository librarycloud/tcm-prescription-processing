import { safeScanCode } from '../../../utils/scanner';
import { getPackageByPickupCode, verifyPackage } from '../../../api/admin';
import {
  formatDate,
  formatPickupCode,
  normalizeExpressTrackingNo,
  normalizePickupCode,
  PICKUP_METHOD_OPTIONS,
  pickupMethodText,
  statusText,
  statusTheme
} from '../../../utils/format';
import { copyToClipboard } from '../../../utils/wechat';


Page({
  data: {
    pickupCode: '',
    pickupQrContent: '',
    pickupMethodOptions: PICKUP_METHOD_OPTIONS,
    pickupMethodIndex: 0,
    pickupMethod: null,
    pickupMethodText: '',
    expressTrackingNo: '',
    packageInfo: null,
    lookupLoading: false,
    verifyLoading: false
  },

  onLoad(options) {
    const qrContent = String(options.pickupQrContent || options.qrContent || '').trim();
    const qrMatch = qrContent.match(/^TCM:PICKUP:1:\d+:(\d{6}):[A-Za-z0-9_-]+$/);
    const pickupCode = normalizePickupCode(qrMatch ? qrMatch[1] : options.pickupCode || '');
    if (/^\d{6}$/.test(pickupCode)) {
      this.setData({ pickupCode: formatPickupCode(pickupCode), pickupQrContent: qrMatch ? qrContent : '' }, () => this.lookup());
    }
  },

  onCodeChange(e) {
    const pickupCode = formatPickupCode(e.detail.value);
    this.setData({
      pickupCode,
      pickupQrContent: '',
      packageInfo: normalizePickupCode(this.data.packageInfo?.pickupCode) === normalizePickupCode(pickupCode) ? this.data.packageInfo : null
    });
  },

  onCopy(e) {
    const text = e.currentTarget.dataset.text;
    const name = e.currentTarget.dataset.name || '内容';
    if (!text) return;
    copyToClipboard(text, name);
  },


  scan() {
    safeScanCode({
      onlyFromCamera: false,
      success: (res) => {
        const value = String(res.result || '').trim();
        const qrMatch = value.match(/^TCM:PICKUP:1:\d+:(\d{6}):[A-Za-z0-9_-]+$/);
        const pickupCode = normalizePickupCode(qrMatch ? qrMatch[1] : value);
        this.setData({
          pickupCode: formatPickupCode(pickupCode),
          pickupQrContent: qrMatch ? value : ''
        }, () => this.lookup());
      }
    });
  },

  async lookup() {
    const pickupCode = normalizePickupCode(this.data.pickupCode);
    if (!/^\d{6}$/.test(pickupCode)) {
      wx.showToast({ title: '请输入6位数字取货码', icon: 'none' });
      return;
    }
    this.setData({ pickupCode: formatPickupCode(pickupCode), lookupLoading: true });
    try {
      const item = await getPackageByPickupCode(pickupCode);
      const methodIndex = PICKUP_METHOD_OPTIONS.findIndex(
        (option) => option.value === Number(item.pickupMethod)
      );
      this.setData({
        pickupMethodIndex: Math.max(0, methodIndex),
        pickupMethod: methodIndex >= 0 ? item.pickupMethod : null,
        pickupMethodText: pickupMethodText(item.pickupMethod),
        expressTrackingNo: item.expressTrackingNo || '',
        packageInfo: {
          ...item,
          pickupCode: formatPickupCode(item.pickupCode),
          createdAtText: formatDate(item.createdAt),
          statusText: statusText(item.status),
          statusTheme: statusTheme(item.status),
          isPicked: Number(item.status) === 1
        }
      });
      if (Number(item.status) === 1) {
        wx.showToast({ title: '该包裹已经核销', icon: 'none' });
      }
    } finally {
      this.setData({ lookupLoading: false });
    }
  },

  onPickupMethodChange(e) {
    const index = Number(e.detail.value);
    const option = PICKUP_METHOD_OPTIONS[index];
    this.setData({
      pickupMethodIndex: index,
      pickupMethod: option.value,
      pickupMethodText: option.label
    });
  },

  onTrackingNoChange(e) {
    this.setData({ expressTrackingNo: normalizeExpressTrackingNo(e.detail.value) });
  },

  scanTrackingNo() {
    safeScanCode({
      scanType: ['barCode', 'qrCode'],
      success: (res) => {
        this.setData({ expressTrackingNo: normalizeExpressTrackingNo(res.result) });
      }
    });
  },

  submit() {
    if (!this.data.packageInfo || this.data.packageInfo.isPicked) return;
    if (
      this.data.pickupMethod === null ||
      ![0, 1, 2].includes(Number(this.data.pickupMethod))
    ) {
      wx.showToast({ title: '请选择取货方式', icon: 'none' });
      return;
    }
    if (Number(this.data.pickupMethod) === 2 && !this.data.expressTrackingNo) {
      wx.showToast({ title: '请录入或扫描快递单号', icon: 'none' });
      return;
    }

    wx.showModal({
      title: '再次确认核销',
      content: `确认将“${this.data.packageInfo.itemName}”按“${this.data.pickupMethodText}”方式核销吗？`,
      confirmText: '确认核销',
      success: (res) => {
        if (res.confirm) this.confirmVerify();
      }
    });
  },

  async confirmVerify() {
    this.setData({ verifyLoading: true });
    try {
      const data = await verifyPackage(
        normalizePickupCode(this.data.packageInfo.pickupCode),
        this.data.pickupMethod,
        this.data.expressTrackingNo,
        this.data.pickupQrContent
      );
      wx.showToast({ title: '核销成功', icon: 'success' });
      setTimeout(() => {
        wx.redirectTo({ url: `/pages/admin/package-detail/package-detail?id=${data.id}` });
      }, 500);
    } finally {
      this.setData({ verifyLoading: false });
    }
  }
});
