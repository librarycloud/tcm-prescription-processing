import { getPackageDetail } from '../../../api/admin';
import { formatDate, formatPickupCode, pickupMethodText, statusText, statusTheme } from '../../../utils/format';
import { copyToClipboard } from '../../../utils/wechat';

function isPendingStatus(status) {
  return Number(status) === 0;
}

Page({
  data: {
    id: null,
    detail: null
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.load();
  },

  async load() {
    const item = await getPackageDetail(this.data.id);
    this.setData({
      detail: {
        ...item,
        pickupCode: formatPickupCode(item.pickupCode),
        pickupQrContent: item.pickupQrContent || item.pickupCode,
        createdAtText: formatDate(item.createdAt),
        pickedAtText: formatDate(item.pickedAt),
        modifiedAtText: formatDate(item.modifiedAt),
        pickupMethodText: pickupMethodText(item.pickupMethod),
        statusText: statusText(item.status),
        statusTheme: statusTheme(item.status),
        isPending: isPendingStatus(item.status)
      }
    });
  },

  onCopy(e) {
    const text = e.currentTarget.dataset.text;
    const name = e.currentTarget.dataset.name || '内容';
    if (!text) return;
    copyToClipboard(text, name);
  },

  verify() {
    wx.navigateTo({
      url: `/pages/admin/verify/verify?pickupCode=${encodeURIComponent(this.data.detail.pickupCode)}&pickupQrContent=${encodeURIComponent(this.data.detail.pickupQrContent || '')}`
    });
  },

  goWorkflow() {
    wx.navigateTo({
      url: `/pages/admin/processing-operation/processing-operation?id=${this.data.detail.processingPlan.id}`
    });
  }
});

