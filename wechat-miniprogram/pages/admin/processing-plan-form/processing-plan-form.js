import {
  createProcessingPlan,
  getDictionaries,
  getPrescriptions,
  updateProcessingPlan
} from '../../../api/admin';
import { getUser } from '../../../utils/auth';
import { PICKUP_METHOD_OPTIONS, pickupMethodText } from '../../../utils/format';

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

function todayText() {
  const date = new Date();
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function prescriptionLabel(item) {
  return `${item.prescriptionNo} · ${item.customerName} · ${item.phone || '无手机号'}`;
}

Page({
  data: {
    id: null,
    isEdit: false,
    loading: false,
    searching: false,
    searchKeyword: '',
    prescriptions: [],
    prescriptionIndex: 0,
    processTypes: [],
    processTypeIndex: 0,
    isDecoction: false,
    notifyTypes: [],
    notifyTypeIndex: 0,
    priorityOptions: PRIORITY_OPTIONS,
    priorityIndex: 0,
    scheduleOptions: SCHEDULE_OPTIONS,
    scheduleIndex: 0,
    paymentOptions: PAYMENT_OPTIONS,
    paymentIndex: 0,
    pickupMethodOptions: PICKUP_METHOD_OPTIONS,
    pickupMethodIndex: 0,
    form: {
      prescriptionId: null,
      prescriptionLabel: '',
      processTypeId: null,
      processTypeName: '',
      batchNo: 1,
      totalDose: 1,
      bagCount: '',
      volumeMl: '',
      priority: 0,
      priorityText: '普通',
      scheduleType: 1,
      scheduleText: '指定日期',
      processDate: todayText(),
      notifyType: null,
      notifyTypeName: '不提醒',
      paymentStatus: 1,
      paymentText: '已收费',
      pickupMethod: 0,
      pickupMethodText: '自提',
      expressAddress: '',
      processRemark: '',
      remark: ''
    }
  },

  async onLoad(options) {
    const editing = options.id
      ? wx.getStorageSync('editingProcessingPlan')
      : null;
    const preset = wx.getStorageSync('processingPlanPreset');
    wx.removeStorageSync('editingProcessingPlan');
    wx.removeStorageSync('processingPlanPreset');
    if (options.id && (!editing || Number(editing.id) !== Number(options.id))) {
      wx.showToast({ title: '缺少计划数据，请重新进入', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 800);
      return;
    }

    this.editingPlan = editing || null;
    this.preset = preset || null;
    this.setData({ id: options.id || null, isEdit: Boolean(options.id) });
    wx.setNavigationBarTitle({
      title: options.id ? '编辑加工计划' : '新建加工计划'
    });
    await this.loadOptions();
    if (editing) this.applyEditingPlan(editing);
    else if (preset?.prescriptionId) this.applyPrescriptionPreset(preset);
  },

  async loadOptions() {
    const [processTypes, notifyTypes] = await Promise.all([
      getDictionaries('ProcessType'),
      getDictionaries('NotifyType')
    ]);
    const defaultNotifyType = (notifyTypes || []).find((item) => item.code === 'NONE');
    this.setData({
      processTypes: processTypes || [],
      notifyTypes: notifyTypes || [],
      'form.notifyType': defaultNotifyType?.id ?? null,
      'form.notifyTypeName': defaultNotifyType?.name || '不提醒'
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
      let prescriptions = (data.list || []).map((item) => ({
        ...item,
        label: prescriptionLabel(item)
      }));
      const selected =
        this.editingPlan?.prescription || this.preset?.prescription;
      if (
        selected &&
        !prescriptions.some((item) => Number(item.id) === Number(selected.id))
      ) {
        prescriptions = [
          { ...selected, label: prescriptionLabel(selected) },
          ...prescriptions
        ];
      }
      this.setData({ prescriptions });
    } finally {
      this.setData({ searching: false });
    }
  },

  applyPrescriptionPreset(preset) {
    const index = Math.max(
      0,
      this.data.prescriptions.findIndex(
        (item) => Number(item.id) === Number(preset.prescriptionId)
      )
    );
    const item = this.data.prescriptions[index] || preset.prescription;
    const nextBatchNo =
      Math.max(
        0,
        ...(preset.prescription?.plans || []).map(
          (plan) => Number(plan.batchNo) || 0
        )
      ) + 1;
    this.setData({
      prescriptionIndex: index,
      'form.prescriptionId': Number(preset.prescriptionId),
      'form.prescriptionLabel': item
        ? prescriptionLabel(item)
        : `处方 #${preset.prescriptionId}`,
      'form.batchNo': nextBatchNo
    });
  },

  applyEditingPlan(plan) {
    const prescriptionIndex = Math.max(
      0,
      this.data.prescriptions.findIndex(
        (item) => Number(item.id) === Number(plan.prescriptionId)
      )
    );
    const processTypeIndex = Math.max(
      0,
      this.data.processTypes.findIndex(
        (item) => Number(item.id) === Number(plan.processTypeId)
      )
    );
    const notifyTypeIndex = Math.max(
      0,
      this.data.notifyTypes.findIndex((item) => item.id === Number(plan.notifyType))
    );
    const priorityIndex = Number(plan.priority) === 1 ? 1 : 0;
    const scheduleIndex = Number(plan.scheduleType) === 2 ? 1 : 0;
    const paymentIndex = Number(plan.paymentStatus) === 0 ? 1 : 0;
    const pickupMethodIndex = Math.max(
      0,
      PICKUP_METHOD_OPTIONS.findIndex((item) => item.value === Number(plan.pickupMethod))
    );
    this.setData({
      prescriptionIndex,
      processTypeIndex,
      isDecoction: plan.processType?.code === 'DECOCTION',
      notifyTypeIndex,
      priorityIndex,
      scheduleIndex,
      paymentIndex,
      pickupMethodIndex,
      form: {
        prescriptionId: Number(plan.prescriptionId),
        prescriptionLabel: prescriptionLabel(plan.prescription),
        processTypeId: Number(plan.processTypeId),
        processTypeName: plan.processType?.name || '',
        batchNo: Number(plan.batchNo),
        totalDose: Number(plan.totalDose),
        bagCount: plan.bagCount || '',
        volumeMl: plan.volumeMl || '',
        priority: Number(plan.priority) === 1 ? 1 : 0,
        priorityText: PRIORITY_OPTIONS[priorityIndex].label,
        scheduleType: plan.scheduleType,
        scheduleText: SCHEDULE_OPTIONS[scheduleIndex].label,
        processDate: plan.processDate
          ? String(plan.processDate).slice(0, 10)
          : todayText(),
        notifyType: Number(plan.notifyType) || null,
        notifyTypeName: plan.notifyType
          ? this.data.notifyTypes[notifyTypeIndex]?.name || plan.notifyType
          : '不提醒',
        paymentStatus: Number(plan.paymentStatus) === 0 ? 0 : 1,
        paymentText: PAYMENT_OPTIONS[paymentIndex].label,
        pickupMethod: plan.pickupMethod === null || plan.pickupMethod === undefined
          ? 0
          : Number(plan.pickupMethod),
        pickupMethodText: pickupMethodText(plan.pickupMethod, '自提'),
        expressAddress: plan.expressAddress || '',
        processRemark: plan.processRemark || '',
        remark: plan.remark || ''
      }
    });
  },

  onSearchKeywordChange(e) {
    this.setData({ searchKeyword: e.detail.value });
  },

  searchPrescriptions() {
    this.loadPrescriptions(this.data.searchKeyword);
  },

  onChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [`form.${field}`]: e.detail.value });
  },

  onPrescriptionChange(e) {
    const prescriptionIndex = Number(e.detail.value);
    const item = this.data.prescriptions[prescriptionIndex];
    this.setData({
      prescriptionIndex,
      'form.prescriptionId': item.id,
      'form.prescriptionLabel': item.label
    });
  },

  onProcessTypeChange(e) {
    const processTypeIndex = Number(e.detail.value);
    const item = this.data.processTypes[processTypeIndex];
    this.setData({
      processTypeIndex,
      isDecoction: item.code === 'DECOCTION',
      'form.processTypeId': item.id,
      'form.processTypeName': item.name
    });
  },

  onPriorityChange(e) {
    const priorityIndex = Number(e.detail.value);
    const item = PRIORITY_OPTIONS[priorityIndex];
    this.setData({
      priorityIndex,
      'form.priority': item.value,
      'form.priorityText': item.label
    });
  },

  onScheduleChange(e) {
    const scheduleIndex = Number(e.detail.value);
    const item = SCHEDULE_OPTIONS[scheduleIndex];
    this.setData({
      scheduleIndex,
      'form.scheduleType': item.value,
      'form.scheduleText': item.label
    });
  },

  onDateChange(e) {
    this.setData({ 'form.processDate': e.detail.value });
  },

  onNotifyTypeChange(e) {
    const notifyTypeIndex = Number(e.detail.value);
    const item = this.data.notifyTypes[notifyTypeIndex];
    this.setData({
      notifyTypeIndex,
      'form.notifyType': item.id,
      'form.notifyTypeName': item.name
    });
  },

  onPaymentChange(e) {
    const paymentIndex = Number(e.detail.value);
    const item = PAYMENT_OPTIONS[paymentIndex];
    this.setData({
      paymentIndex,
      'form.paymentStatus': item.value,
      'form.paymentText': item.label
    });
  },

  onPickupMethodChange(e) {
    const pickupMethodIndex = Number(e.detail.value);
    const item = PICKUP_METHOD_OPTIONS[pickupMethodIndex];
    this.setData({
      pickupMethodIndex,
      'form.pickupMethod': item.value,
      'form.pickupMethodText': item.label,
      ...(![1, 2].includes(item.value) ? { 'form.expressAddress': '' } : {})
    });
  },

  async submit() {
    const form = this.data.form;
    const batchNo = Number(form.batchNo);
    const totalDose = Number(form.totalDose);
    const bagCount = Number(form.bagCount);
    const volumeMl = Number(form.volumeMl);
    if (!form.prescriptionId)
      return wx.showToast({ title: '请选择处方', icon: 'none' });
    if (!form.processTypeId)
      return wx.showToast({ title: '请选择加工方式', icon: 'none' });
    if (!Number.isInteger(batchNo) || batchNo <= 0) {
      return wx.showToast({ title: '批次号必须为正整数', icon: 'none' });
    }
    if (!Number.isInteger(totalDose) || totalDose <= 0) {
      return wx.showToast({ title: '剂数必须为正整数', icon: 'none' });
    }
    if (this.data.isDecoction && (!Number.isInteger(bagCount) || bagCount <= 0)) {
      return wx.showToast({ title: '袋数必须为正整数', icon: 'none' });
    }
    if (this.data.isDecoction && (!Number.isInteger(volumeMl) || volumeMl <= 0)) {
      return wx.showToast({ title: '毫升数必须为正整数', icon: 'none' });
    }
    if (Number(form.scheduleType) === 1 && !form.processDate) {
      return wx.showToast({ title: '请选择计划开工日期', icon: 'none' });
    }
    if (!form.notifyType)
      return wx.showToast({ title: '请选择提醒方式', icon: 'none' });

    const payload = {
      prescriptionId: form.prescriptionId,
      processTypeId: form.processTypeId,
      batchNo,
      totalDose,
      bagCount: this.data.isDecoction ? bagCount : null,
      volumeMl: this.data.isDecoction ? volumeMl : null,
      priority: form.priority,
      scheduleType: form.scheduleType,
      processDate: Number(form.scheduleType) === 1 ? form.processDate : null,
      notifyType: form.notifyType,
      paymentStatus: form.paymentStatus,
      pickupMethod: form.pickupMethod,
      expressAddress: form.expressAddress,
      processRemark: form.processRemark,
      remark: form.remark
    };
    this.setData({ loading: true });
    try {
      if (this.data.isEdit) await updateProcessingPlan(this.data.id, payload);
      else await createProcessingPlan(payload);
      wx.showToast({
        title: this.data.isEdit ? '修改成功' : '新增成功',
        icon: 'success'
      });
      setTimeout(() => wx.navigateBack(), 500);
    } finally {
      this.setData({ loading: false });
    }
  }
});
