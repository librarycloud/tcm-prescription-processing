import {
  createProcessingPlanBatch,
  getDictionaries,
  getDoctors,
  getPrescriptions,
  getStores
} from '../../../api/admin';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';
import { PICKUP_METHOD_OPTIONS } from '../../../utils/format';

const MODE_OPTIONS = [
  { label: '选择已有处方', value: 'existing' },
  { label: '新建处方', value: 'new' }
];
const EXTERNAL_OPTIONS = [
  { label: '本方', value: false },
  { label: '外方', value: true }
];
const PRIORITY_OPTIONS = [
  { label: '普通', value: 0 },
  { label: '加急', value: 1 }
];
const SCHEDULE_OPTIONS = [
  { label: '指定日期', value: 1 },
  { label: '等待顾客通知', value: 2 }
];
const PAYMENT_OPTIONS = [
  { label: '已收费', value: 1 },
  { label: '未收费', value: 0 }
];
let planKey = 0;

function todayText() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function prescriptionLabel(item) {
  return `${item.prescriptionNo} · ${item.customerName} · ${item.phone || '无手机号'}`;
}

function emptyPlan(batchNo = 1) {
  return {
    key: ++planKey,
    batchNo,
    processTypeId: null,
    processTypeName: '',
    processTypeIndex: 0,
    isDecoction: false,
    pickupMethod: PICKUP_METHOD_OPTIONS[0].value,
    pickupMethodText: PICKUP_METHOD_OPTIONS[0].label,
    pickupMethodIndex: 0,
    expressAddress: '',
    totalDose: 1,
    bagCount: '',
    volumeMl: '',
    priority: 0,
    priorityText: PRIORITY_OPTIONS[0].label,
    priorityIndex: 0,
    scheduleType: 1,
    scheduleText: SCHEDULE_OPTIONS[0].label,
    scheduleIndex: 0,
    processDate: todayText(),
    notifyType: null,
    notifyTypeName: '不提醒',
    notifyTypeIndex: 0,
    paymentStatus: 1,
    paymentText: PAYMENT_OPTIONS[0].label,
    paymentIndex: 0,
    processRemark: '',
    remark: ''
  };
}

function isValidPhone(phone) {
  return !phone || /^1[3-9]\d{9}$/.test(String(phone).trim());
}

Page({
  data: {
    activeTab: 'processing',
    loading: false,
    searching: false,
    isSuperAdmin: false,
    modeOptions: MODE_OPTIONS,
    prescriptionMode: 'existing',
    searchKeyword: '',
    prescriptions: [],
    prescriptionIndex: 0,
    prescriptionId: null,
    prescriptionLabel: '',
    stores: [],
    storeIndex: 0,
    doctors: [],
    doctorIndex: 0,
    sources: [],
    sourceIndex: 0,
    externalOptions: EXTERNAL_OPTIONS,
    externalIndex: 0,
    processTypes: [],
    notifyTypes: [],
    priorityOptions: PRIORITY_OPTIONS,
    scheduleOptions: SCHEDULE_OPTIONS,
    paymentOptions: PAYMENT_OPTIONS,
    pickupMethodOptions: PICKUP_METHOD_OPTIONS,
    prescription: {
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
      remark: ''
    },
    plans: [emptyPlan(1)]
  },

  onTabChange: onAdminTabChange,

  async onLoad() {
    const user = getUser();
    this.user = user;
    this.setData({ isSuperAdmin: Number(user.role) === 0 });
    wx.setNavigationBarTitle({ title: '新建加工计划' });
    await this.loadOptions();

    const preset = wx.getStorageSync('processingPlanPreset');
    wx.removeStorageSync('processingPlanPreset');
    if (preset?.prescriptionId) this.applyPreset(preset);
  },

  async loadOptions() {
    const tasks = [
      getDictionaries('ProcessType'),
      getDictionaries('NotifyType'),
      getDoctors(),
      getDictionaries('PrescriptionSource')
    ];
    if (this.data.isSuperAdmin) tasks.push(getStores({ page: 1, pageSize: 100, status: 1 }));
    const [processTypes, notifyTypes, doctors, sources, stores] = await Promise.all(tasks);
    const normalizedNotifyTypes = (notifyTypes || []).length
      ? [...notifyTypes].sort((left, right) => (left.code === 'NONE' ? -1 : right.code === 'NONE' ? 1 : 0))
      : [];
    const defaultNotifyType = normalizedNotifyTypes.find((item) => item.code === 'NONE');
    this.setData({
      processTypes: processTypes || [],
      notifyTypes: normalizedNotifyTypes,
      doctors: doctors || [],
      sources: sources || [],
      stores: stores?.list || [],
      plans: this.data.plans.map((plan) =>
        plan.notifyType === null ? { ...plan, notifyType: defaultNotifyType?.id ?? null, notifyTypeName: defaultNotifyType?.name || '不提醒' } : plan
      )
    });
    await this.loadPrescriptions();
  },

  async loadPrescriptions(keyword = '') {
    this.setData({ searching: true });
    try {
      const data = await getPrescriptions({
        page: 1,
        pageSize: 100,
        status: 0,
        keyword: String(keyword || '').trim()
      });
      this.setData({
        prescriptions: (data.list || []).map((item) => ({
          ...item,
          label: prescriptionLabel(item)
        }))
      });
    } finally {
      this.setData({ searching: false });
    }
  },

  applyPreset(preset) {
    const selected = preset.prescription || {};
    let prescriptions = this.data.prescriptions;
    if (selected.id && !prescriptions.some((item) => Number(item.id) === Number(selected.id))) {
      prescriptions = [{ ...selected, label: prescriptionLabel(selected) }, ...prescriptions];
    }
    const prescriptionIndex = Math.max(
      0,
      prescriptions.findIndex((item) => Number(item.id) === Number(preset.prescriptionId))
    );
    const nextBatchNo = Math.max(
      0,
      ...(selected.plans || []).map((plan) => Number(plan.batchNo) || 0)
    ) + 1;
    this.setData({
      prescriptions,
      prescriptionMode: 'existing',
      prescriptionIndex,
      prescriptionId: Number(preset.prescriptionId),
      prescriptionLabel: prescriptions[prescriptionIndex]?.label || `处方 #${preset.prescriptionId}`,
      plans: [emptyPlan(nextBatchNo)]
    });
  },

  switchPrescriptionMode(e) {
    this.setData({ prescriptionMode: e.currentTarget.dataset.mode });
  },

  onSearchKeywordChange(e) { this.setData({ searchKeyword: e.detail.value }); },
  searchPrescriptions() { this.loadPrescriptions(this.data.searchKeyword); },

  onPrescriptionChange(e) {
    const prescriptionIndex = Number(e.detail.value);
    const item = this.data.prescriptions[prescriptionIndex];
    const nextBatchNo = Math.max(0, ...(item.plans || []).map((plan) => Number(plan.batchNo) || 0)) + 1;
    const plans = [...this.data.plans];
    if (plans.length === 1) plans[0] = { ...plans[0], batchNo: nextBatchNo };
    this.setData({
      prescriptionIndex,
      prescriptionId: item.id,
      prescriptionLabel: item.label,
      plans
    });
  },

  onPrescriptionFieldChange(e) {
    this.setData({ [`prescription.${e.currentTarget.dataset.field}`]: e.detail.value });
  },

  onStoreChange(e) {
    const storeIndex = Number(e.detail.value);
    const item = this.data.stores[storeIndex];
    this.setData({ storeIndex, 'prescription.storeId': item.id, 'prescription.storeName': item.name });
  },

  onDoctorChange(e) {
    const doctorIndex = Number(e.detail.value);
    const item = this.data.doctors[doctorIndex];
    this.setData({ doctorIndex, 'prescription.doctorId': item.id, 'prescription.doctorName': item.name });
  },

  onSourceChange(e) {
    const sourceIndex = Number(e.detail.value);
    const item = this.data.sources[sourceIndex];
    this.setData({ sourceIndex, 'prescription.sourceId': item.id, 'prescription.sourceName': item.name });
  },

  onExternalChange(e) {
    const externalIndex = Number(e.detail.value);
    this.setData({ externalIndex, 'prescription.isExternal': EXTERNAL_OPTIONS[externalIndex].value });
  },

  onPlanFieldChange(e) {
    const { index, field } = e.currentTarget.dataset;
    this.setData({ [`plans[${Number(index)}].${field}`]: e.detail.value });
  },

  onProcessTypeChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    const optionIndex = Number(e.detail.value);
    const item = this.data.processTypes[optionIndex];
    const isDecoction = item.code === 'DECOCTION';
    this.setData({
      [`plans[${index}].processTypeIndex`]: optionIndex,
      [`plans[${index}].processTypeId`]: item.id,
      [`plans[${index}].processTypeName`]: item.name,
      [`plans[${index}].isDecoction`]: isDecoction,
      ...(!isDecoction
        ? { [`plans[${index}].bagCount`]: '', [`plans[${index}].volumeMl`]: '' }
        : {})
    });
  },

  onPlanOptionChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    const type = e.currentTarget.dataset.type;
    const optionIndex = Number(e.detail.value);
    const configs = {
      pickup: { options: PICKUP_METHOD_OPTIONS, value: 'pickupMethod', text: 'pickupMethodText', index: 'pickupMethodIndex', key: 'value' },
      priority: { options: PRIORITY_OPTIONS, value: 'priority', text: 'priorityText', index: 'priorityIndex', key: 'value' },
      schedule: { options: SCHEDULE_OPTIONS, value: 'scheduleType', text: 'scheduleText', index: 'scheduleIndex', key: 'value' },
      payment: { options: PAYMENT_OPTIONS, value: 'paymentStatus', text: 'paymentText', index: 'paymentIndex', key: 'value' },
      notify: { options: this.data.notifyTypes, value: 'notifyType', text: 'notifyTypeName', index: 'notifyTypeIndex', key: 'id' }
    };
    const config = configs[type];
    const item = config.options[optionIndex];
    this.setData({
      [`plans[${index}].${config.index}`]: optionIndex,
      [`plans[${index}].${config.value}`]: item[config.key],
      [`plans[${index}].${config.text}`]: item.label || item.name,
      ...(type === 'pickup' && ![1, 2].includes(item.value)
        ? { [`plans[${index}].expressAddress`]: '' }
        : {})
    });
  },

  onPlanDateChange(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ [`plans[${index}].processDate`]: e.detail.value });
  },

  addPlan() {
    const plans = [...this.data.plans];
    const batchNo = Math.max(0, ...plans.map((plan) => Number(plan.batchNo) || 0)) + 1;
    const previous = plans[plans.length - 1];
    plans.push(previous ? { ...previous, key: ++planKey, batchNo, processRemark: '', remark: '' } : emptyPlan(batchNo));
    this.setData({ plans });
  },

  copyPrevious(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index <= 0) return;
    const plans = [...this.data.plans];
    plans[index] = { ...plans[index - 1], key: plans[index].key, batchNo: plans[index].batchNo };
    this.setData({ plans });
  },

  removePlan(e) {
    if (this.data.plans.length <= 1) return;
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ plans: this.data.plans.filter((_, planIndex) => planIndex !== index) });
  },

  validate() {
    if (this.data.prescriptionMode === 'existing' && !this.data.prescriptionId) return '请选择处方';
    if (this.data.prescriptionMode === 'new') {
      const prescription = this.data.prescription;
      if (this.data.isSuperAdmin && !prescription.storeId) return '请选择所属门店';
      if (!String(prescription.customerName || '').trim()) return '请输入顾客姓名';
      if (!isValidPhone(prescription.phone)) return '请输入正确手机号';
      if (!prescription.doctorId) return '请选择医生';
      if (!prescription.sourceId) return '请选择处方来源';
    }
    const batchNos = new Set();
    for (const plan of this.data.plans) {
      const batchNo = Number(plan.batchNo);
      if (!Number.isInteger(batchNo) || batchNo <= 0) return '批次号必须为正整数';
      if (batchNos.has(batchNo)) return `第 ${batchNo} 批重复`;
      batchNos.add(batchNo);
      if (!plan.processTypeId) return `请选择第 ${batchNo} 批加工方式`;
      if (!Number.isInteger(Number(plan.totalDose)) || Number(plan.totalDose) <= 0) return `请填写第 ${batchNo} 批剂数`;
      if (plan.isDecoction && (!Number.isInteger(Number(plan.bagCount)) || Number(plan.bagCount) <= 0)) return `请填写第 ${batchNo} 批袋数`;
      if (plan.isDecoction && (!Number.isInteger(Number(plan.volumeMl)) || Number(plan.volumeMl) <= 0)) return `请填写第 ${batchNo} 批毫升数`;
      if (Number(plan.scheduleType) === 1 && !plan.processDate) return `请选择第 ${batchNo} 批加工日期`;
    }
    return '';
  },

  async submit() {
    const error = this.validate();
    if (error) return wx.showToast({ title: error, icon: 'none' });
    const prescription = this.data.prescription;
    const payload = {
      prescriptionMode: this.data.prescriptionMode,
      ...(this.data.prescriptionMode === 'existing'
        ? { prescriptionId: this.data.prescriptionId }
        : {
            prescription: {
              customerName: String(prescription.customerName).trim(),
              phone: String(prescription.phone || '').trim(),
              doctorId: prescription.doctorId,
              sourceId: prescription.sourceId,
              isExternal: prescription.isExternal,
              externalHospital: prescription.externalHospital,
              externalDoctor: prescription.externalDoctor,
              externalRemark: prescription.externalRemark,
              remark: prescription.remark,
              ...(this.data.isSuperAdmin ? { storeId: prescription.storeId } : {})
            }
          }),
      plans: this.data.plans.map((plan) => ({
        processTypeId: plan.processTypeId,
        batchNo: Number(plan.batchNo),
        totalDose: Number(plan.totalDose),
        bagCount: plan.isDecoction ? Number(plan.bagCount) : null,
        volumeMl: plan.isDecoction ? Number(plan.volumeMl) : null,
        pickupMethod: plan.pickupMethod,
        expressAddress: plan.expressAddress,
        priority: plan.priority,
        scheduleType: plan.scheduleType,
        processDate: Number(plan.scheduleType) === 1 ? plan.processDate : null,
        notifyType: plan.notifyType,
        paymentStatus: plan.paymentStatus,
        processRemark: plan.processRemark,
        remark: plan.remark
      }))
    };
    this.setData({ loading: true });
    try {
      await createProcessingPlanBatch(payload);
      wx.showToast({ title: `已创建${this.data.plans.length}个计划`, icon: 'success' });
      setTimeout(() => wx.navigateBack(), 600);
    } finally {
      this.setData({ loading: false });
    }
  }
});
