import { getMyPackageDetail } from '../../../api/user';
import { formatDate, formatPickupCode, pickupMethodText, statusText, statusTheme } from '../../../utils/format';

function isPickedStatus(status) {
  return Number(status) === 1;
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
    const item = await getMyPackageDetail(this.data.id);
    this.setData({
      detail: {
        ...item,
        pickupCode: formatPickupCode(item.pickupCode),
        createdAtText: formatDate(item.createdAt),
        pickedAtText: formatDate(item.pickedAt),
        pickupMethodText: pickupMethodText(item.pickupMethod),
        statusText: statusText(item.status),
        statusTheme: statusTheme(item.status),
        isPicked: isPickedStatus(item.status)
      }
    });
  }
});
