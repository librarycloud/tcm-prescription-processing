import { getPackageDetail, updatePackage } from '../../../api/admin';
import { normalizeExpressTrackingNo, PICKUP_METHOD_OPTIONS, pickupMethodText } from '../../../utils/format';

function isPickedStatus(status) {
  return Number(status) === 1;
}

Page({
  data: {
    id: null,
    loading: false,
    pickupMethodOptions: PICKUP_METHOD_OPTIONS,
    pickupMethodIndex: 0,
    form: {
      itemName: '',
      itemInfo: '',
      receiverName: '',
      receiverPhone: '',
      pickupMethod: null,
      pickupMethodText: '',
      expressAddress: '',
      expressTrackingNo: ''
    }
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.loadDetail();
  },

  async loadDetail() {
    const item = await getPackageDetail(this.data.id);
    if (isPickedStatus(item.status)) {
      wx.showToast({ title: '已取包裹不能修改', icon: 'none' });
      setTimeout(() => {
        wx.redirectTo({ url: `/pages/admin/package-detail/package-detail?id=${item.id}` });
      }, 500);
      return;
    }

    this.setData({
      pickupMethodIndex: Math.max(
        0,
        PICKUP_METHOD_OPTIONS.findIndex((option) => option.value === Number(item.pickupMethod))
      ),
      form: {
        itemName: item.itemName || '',
        itemInfo: item.itemInfo || '',
        receiverName: item.receiverName || '',
        receiverPhone: item.receiverPhone || '',
        pickupMethod: item.pickupMethod ?? null,
        pickupMethodText: pickupMethodText(item.pickupMethod),
        expressAddress: item.expressAddress || '',
        expressTrackingNo: item.expressTrackingNo || ''
      }
    });
  },

  onChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  onPickupMethodChange(e) {
    const index = Number(e.detail.value);
    const option = PICKUP_METHOD_OPTIONS[index];
    this.setData({
      pickupMethodIndex: index,
      'form.pickupMethod': option.value,
      'form.pickupMethodText': option.label,
      ...(![1, 2].includes(option.value)
        ? { 'form.expressAddress': '', 'form.expressTrackingNo': '' }
        : {})
    });
  },

  scanTrackingNo() {
    wx.scanCode({
      scanType: ['barCode', 'qrCode'],
      success: (res) => {
        this.setData({ 'form.expressTrackingNo': normalizeExpressTrackingNo(res.result) });
      }
    });
  },

  async submit() {
    if (this.data.form.pickupMethod === null) {
      wx.showToast({ title: '请选择取货方式', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    try {
      const data = await updatePackage(this.data.id, this.data.form);
      wx.showToast({ title: '修改成功', icon: 'success' });
      setTimeout(() => {
        wx.redirectTo({ url: `/pages/admin/package-detail/package-detail?id=${data.id}` });
      }, 500);
    } finally {
      this.setData({ loading: false });
    }
  }
});
