import {
  createPrescription,
  getDictionaries,
  getDoctors,
  getPrescriptionDetail,
  getStores,
  updatePrescription
} from '../../../api/admin';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';

const EXTERNAL_OPTIONS = [
  { label: '本方', value: false },
  { label: '外方', value: true }
];

const STATUS_OPTIONS = [
  { label: '进行中', value: 0 },
  { label: '已取消', value: 2 }
];

function isValidPhone(phone) {
  return !phone || /^1[3-9]\d{9}$/.test(String(phone).trim());
}

Page({
  data: {
    activeTab: '',
    id: null,
    isEdit: false,
    isSuperAdmin: false,
    loading: false,
    stores: [],
    storeIndex: 0,
    doctors: [],
    doctorIndex: 0,
    sources: [],
    sourceIndex: 0,
    externalOptions: EXTERNAL_OPTIONS,
    externalIndex: 0,
    statusOptions: STATUS_OPTIONS,
    statusIndex: 0,
    form: {
      storeId: null,
      storeName: '',
      customerName: '',
      phone: '',
      doctorId: null,
      doctorName: '',
      sourceId: null,
      sourceName: '',
      isExternal: false,
      externalHospital: '',
      externalDoctor: '',
      externalRemark: '',
      remark: '',
      status: 0,
      statusText: '进行中'
    }
  },

  onTabChange: onAdminTabChange,

  async onLoad(options) {
    const user = getUser();
    const isEdit = Boolean(options.id);
    this.setData({
      id: options.id || null,
      isEdit,
      isSuperAdmin: Number(user.role) === 0
    });
    wx.setNavigationBarTitle({ title: isEdit ? '编辑处方' : '新建处方' });
    await this.loadOptions();
    if (isEdit) await this.loadDetail();
  },

  async loadOptions() {
    const tasks = [getDoctors(), getDictionaries('PrescriptionSource')];
    if (this.data.isSuperAdmin)
      tasks.push(getStores({ page: 1, pageSize: 100, status: 1 }));
    const [doctors, sources, stores] = await Promise.all(tasks);
    this.setData({
      doctors: doctors || [],
      sources: sources || [],
      stores: stores?.list || []
    });
  },

  async loadDetail() {
    const item = await getPrescriptionDetail(this.data.id);
    const doctorIndex = Math.max(
      0,
      this.data.doctors.findIndex((entry) => entry.id === item.doctorId)
    );
    const sourceIndex = Math.max(
      0,
      this.data.sources.findIndex((entry) => entry.id === item.sourceId)
    );
    const statusIndex = Math.max(
      0,
      STATUS_OPTIONS.findIndex((entry) => entry.value === Number(item.status))
    );
    this.setData({
      doctorIndex,
      sourceIndex,
      externalIndex: item.isExternal ? 1 : 0,
      statusIndex,
      form: {
        storeId: item.storeId,
        storeName: item.store?.name || '',
        customerName: item.customerName || '',
        phone: item.phone || '',
        doctorId: item.doctorId,
        doctorName: item.doctor?.name || '',
        sourceId: item.sourceId,
        sourceName: item.source?.name || '',
        isExternal: Boolean(item.isExternal),
        externalHospital: item.externalHospital || '',
        externalDoctor: item.externalDoctor || '',
        externalRemark: item.externalRemark || '',
        remark: item.remark || '',
        status: Number(item.status),
        statusText: STATUS_OPTIONS[statusIndex]?.label || '进行中'
      }
    });
  },

  onChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  onStoreChange(e) {
    const storeIndex = Number(e.detail.value);
    const store = this.data.stores[storeIndex];
    this.setData({
      storeIndex,
      'form.storeId': store.id,
      'form.storeName': store.name
    });
  },

  onDoctorChange(e) {
    const doctorIndex = Number(e.detail.value);
    const doctor = this.data.doctors[doctorIndex];
    this.setData({
      doctorIndex,
      'form.doctorId': doctor.id,
      'form.doctorName': doctor.name
    });
  },

  onSourceChange(e) {
    const sourceIndex = Number(e.detail.value);
    const source = this.data.sources[sourceIndex];
    this.setData({
      sourceIndex,
      'form.sourceId': source.id,
      'form.sourceName': source.name
    });
  },

  onExternalChange(e) {
    const externalIndex = Number(e.detail.value);
    this.setData({
      externalIndex,
      'form.isExternal': EXTERNAL_OPTIONS[externalIndex].value
    });
  },

  onStatusChange(e) {
    const statusIndex = Number(e.detail.value);
    const status = STATUS_OPTIONS[statusIndex];
    this.setData({
      statusIndex,
      'form.status': status.value,
      'form.statusText': status.label
    });
  },

  async submit() {
    const form = this.data.form;
    if (this.data.isSuperAdmin && !this.data.isEdit && !form.storeId) {
      return wx.showToast({ title: '请选择所属门店', icon: 'none' });
    }
    if (!String(form.customerName || '').trim()) {
      return wx.showToast({ title: '请输入顾客姓名', icon: 'none' });
    }
    if (!isValidPhone(form.phone)) {
      return wx.showToast({ title: '请输入正确手机号', icon: 'none' });
    }
    if (!form.doctorId)
      return wx.showToast({ title: '请选择医生', icon: 'none' });
    if (!form.sourceId)
      return wx.showToast({ title: '请选择处方来源', icon: 'none' });

    const payload = {
      customerName: String(form.customerName).trim(),
      phone: String(form.phone || '').trim(),
      doctorId: form.doctorId,
      sourceId: form.sourceId,
      isExternal: form.isExternal,
      externalHospital: form.externalHospital,
      externalDoctor: form.externalDoctor,
      externalRemark: form.externalRemark,
      remark: form.remark,
      ...(this.data.isEdit
        ? { status: form.status }
        : { storeId: form.storeId })
    };
    this.setData({ loading: true });
    try {
      const result = this.data.isEdit
        ? await updatePrescription(this.data.id, payload)
        : await createPrescription(payload);
      wx.showToast({
        title: this.data.isEdit ? '修改成功' : '新增成功',
        icon: 'success'
      });
      setTimeout(() => {
        wx.redirectTo({
          url: `/pages/admin/prescription-detail/prescription-detail?id=${result.id}`
        });
      }, 500);
    } finally {
      this.setData({ loading: false });
    }
  }
});
