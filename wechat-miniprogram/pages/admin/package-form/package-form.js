import { safeScanCode } from '../../../utils/scanner';
import { createPackage, getPackageDetail, getStores, matchAdminUsers, updatePackage } from '../../../api/admin';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { normalizeExpressTrackingNo, PICKUP_METHOD_OPTIONS, pickupMethodText } from '../../../utils/format';
import { getUser } from '../../../utils/auth';

function isPickedStatus(status) {
  return Number(status) === 1;
}

function isValidPhone(phone) {
  return /^1[3-9]\d{9}$/.test(String(phone || '').trim());
}

Page({
  data: {
    activeTab: 'packages',
    id: null,
    isEdit: false,
    loading: false,
    isSuperAdmin: false,
    stores: [],
    storeIndex: 0,
    pickupMethodOptions: PICKUP_METHOD_OPTIONS,
    pickupMethodIndex: 0,
    matchingUsers: false,
    matchedUsers: [],
    matchedUser: null,
    isNewUser: false,
    form: {
      storeId: null,
      storeName: '',
      itemName: '',
      itemInfo: '',
      receiverName: '',
      receiverPhone: '',
      newUserName: '',
      newUserRemark: '',
      pickupMethod: null,
      pickupMethodText: '',
      expressAddress: '',
      expressTrackingNo: ''
    }
  },

  onTabChange: onAdminTabChange,

  onLoad(options) {
    const user = getUser();
    this.setData({ isSuperAdmin: Number(user.role) === 0 });
    if (options.id) {
      this.setData({ id: options.id, isEdit: true });
      wx.setNavigationBarTitle({ title: '编辑包裹' });
      this.loadDetail();
    } else if (Number(user.role) === 0) {
      this.loadStores();
    }
  },

  async loadStores() {
    const data = await getStores({ page: 1, pageSize: 100, status: 1 });
    this.setData({ stores: data.list || [] });
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
        storeId: item.storeId,
        storeName: item.store?.name || '',
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

  onReceiverPhoneChange(e) {
    const phone = String(e.detail.value || '').trim();
    this.userMatchRequestId = (this.userMatchRequestId || 0) + 1;
    this.setData({
      'form.receiverPhone': phone,
      matchedUsers: [],
      matchedUser: null,
      isNewUser: false,
      matchingUsers: false
    });

    if (phone.length < 7) return;
    this.matchUsers(phone, this.userMatchRequestId);
  },

  async matchUsers(phone, requestId) {
    this.setData({ matchingUsers: true });
    try {
      const users = (await matchAdminUsers(phone)) || [];
      if (requestId !== this.userMatchRequestId) return;
      const matchedUsers = users.filter((user) => Number(user.role) === 1);
      const matchedUser = matchedUsers.find((user) => user.phone === phone) || null;
      this.setData({
        matchedUsers,
        matchedUser,
        isNewUser: isValidPhone(phone) && !matchedUser && matchedUsers.length === 0,
        matchingUsers: false
      });
    } catch (error) {
      if (requestId === this.userMatchRequestId) {
        this.setData({ matchingUsers: false });
      }
    }
  },

  selectMatchedUser(e) {
    const user = this.data.matchedUsers[Number(e.currentTarget.dataset.index)];
    if (!user) return;
    this.setData({
      matchedUser: user,
      isNewUser: false,
      'form.receiverPhone': user.phone
    });
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
    safeScanCode({
      scanType: ['barCode', 'qrCode'],
      success: (res) => {
        this.setData({ 'form.expressTrackingNo': normalizeExpressTrackingNo(res.result) });
      }
    });
  },

  onStoreChange(e) {
    const index = Number(e.detail.value);
    const store = this.data.stores[index];
    this.setData({
      storeIndex: index,
      'form.storeId': store.id,
      'form.storeName': store.name
    });
  },

  async submit() {
    if (this.data.isSuperAdmin && !this.data.isEdit && !this.data.form.storeId) {
      wx.showToast({ title: '请选择所属门店', icon: 'none' });
      return;
    }
    if (this.data.form.pickupMethod === null) {
      wx.showToast({ title: '请选择取货方式', icon: 'none' });
      return;
    }
    if (this.data.form.receiverPhone && !isValidPhone(this.data.form.receiverPhone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    try {
      const data = this.data.isEdit
        ? await updatePackage(this.data.id, this.data.form)
        : await createPackage(this.data.form);
      wx.showToast({ title: this.data.isEdit ? '修改成功' : '新增成功', icon: 'success' });
      setTimeout(() => {
        wx.redirectTo({ url: `/pages/admin/package-detail/package-detail?id=${data.id}` });
      }, 500);
    } finally {
      this.setData({ loading: false });
    }
  }
});
