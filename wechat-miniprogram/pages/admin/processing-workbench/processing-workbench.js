import {
  delayProcessingPlan,
  deleteProcessingPlan,
  generateProcessingPlanPackage,
  getPackages,
  getProcessingPlans,
  getStats,
  getStores,
  receiveProcessingNotice,
  findProcessingPlanByScan,
  transitionProcessingPlan
} from '../../../api/admin';
import { formatDate, formatPickupCode, pickupMethodText, statusText, statusTheme } from '../../../utils/format';
import { onAdminTabChange } from '../../../utils/admin-tabbar';
import { getUser } from '../../../utils/auth';

const PLAN_STATUS = {
  0: { text: '待加工', theme: 'default' },
  1: { text: '加工中', theme: 'primary' },
  2: { text: '加工完成', theme: 'success' },
  3: { text: '待领取', theme: 'warning' },
  4: { text: '已领取', theme: 'success' },
  5: { text: '已取消', theme: 'default' }
};

function dateText(value = new Date(), offset = 0) {
  const date = new Date(value);
  date.setDate(date.getDate() + offset);
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function confirmAction(title, content) {
  return new Promise((resolve) => {
    wx.showModal({
      title,
      content,
      confirmColor: '#0052d9',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
}

function chooseAction(itemList) {
  return new Promise((resolve) => {
    wx.showActionSheet({
      itemList,
      success: (result) => resolve(result.tapIndex),
      fail: () => resolve(-1)
    });
  });
}

function planStage(plan, view) {
  if (
    Number(plan.status) === 0 &&
    Number(plan.scheduleType) === 1 &&
    plan.processDate &&
    dateText(plan.processDate) < dateText()
  ) {
    return { text: '逾期未开工', theme: 'warning' };
  }
  if (
    view === 'today-all' &&
    plan.finishDate &&
    dateText(plan.finishDate) === dateText()
  ) {
    return { text: '已完成', theme: 'success' };
  }
  return PLAN_STATUS[plan.status] || { text: plan.status, theme: 'default' };
}

Page({
  data: {
    activeTab: 'processing',
    mode: 'plans',
    activeView: 'today-all',
    loading: false,
    keyword: '',
    isSuperAdmin: false,
    stores: [],
    storeIndex: 0,
    storeId: '',
    storeName: '全部门店',
    page: 1,
    pageSize: 10,
    pages: 1,
    list: [],
    statCards: []
  },

  onTabChange: onAdminTabChange,

  async onLoad(options = {}) {
    const user = getUser();
    const isSuperAdmin = Number(user.role) === 0;
    const allowedViews = [
      'today-all',
      'today-waiting',
      'today-finished',
      'overdue',
      'processing',
      'notice',
      'tomorrow',
      'all'
    ];
    const activeView = allowedViews.includes(options.view) ? options.view : this.data.activeView;
    this.setData({ isSuperAdmin, activeView });
    if (isSuperAdmin) {
      const data = await getStores({ page: 1, pageSize: 100 });
      this.setData({
        stores: [{ id: '', name: '全部门店' }, ...(data.list || [])]
      });
    }
  },

  async onShow() {
    await this.reloadAll();
  },

  async onPullDownRefresh() {
    try {
      await this.reloadAll();
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  async reloadAll() {
    await Promise.all([this.loadStats(), this.load()]);
  },

  async loadStats() {
    const stats = await getStats({ storeId: this.data.storeId });
    this.setData({
      statCards: [
        {
          key: 'today-all',
          view: 'today-all',
          label: '今日全部',
          value:
            (stats.waitingCount || 0) +
            (stats.processingCount || 0) +
            (stats.todayFinished || 0)
        },
        {
          key: 'today-waiting',
          view: 'today-waiting',
          label: '今日待加工',
          value: stats.waitingCount || 0
        },
        {
          key: 'overdue',
          view: 'overdue',
          label: '逾期未开工',
          value: stats.overdueCount || 0
        },
        {
          key: 'processing',
          view: 'processing',
          label: '加工中',
          value: stats.processingCount || 0
        },
        {
          key: 'notice',
          view: 'notice',
          label: '等待顾客',
          value: stats.waitingNoticeCount || 0
        },
        {
          key: 'tomorrow',
          view: 'tomorrow',
          label: '明日加工',
          value: stats.tomorrowWaitingCount || 0
        },
        {
          key: 'all',
          view: 'all',
          label: '全部',
          value: stats.processingPlanTotalCount || 0
        }
      ]
    });
  },

  async load() {
    this.setData({ loading: true });
    try {
      if (this.data.mode === 'pickup') {
        await this.loadPickupTasks();
      } else {
        await this.loadPlans();
      }
    } finally {
      this.setData({ loading: false });
    }
  },

  async loadPlans() {
    const data = await getProcessingPlans({
      view: this.data.activeView,
      keyword: this.data.keyword,
      storeId: this.data.storeId,
      page: this.data.page,
      pageSize: this.data.pageSize
    });
    this.setData({
      list: (data.list || []).map((item) => {
        const stage = planStage(item, this.data.activeView);
        const canStart = Number(item.status) === 0 && Number(item.scheduleType) === 1;
        const canFinish = false;
        const canOperate = [1, 2, 3, 4].includes(Number(item.status));
        const canGeneratePackage = Number(item.status) === 2;
        const canReceiveNotice = Number(item.status) === 0 && Number(item.scheduleType) === 2;
        const canDelay = Number(item.status) === 0;
        const canEdit = [0, 1].includes(Number(item.status));
        const canDelete = Number(item.status) === 0;
        const actionCount =
          2 +
          [canStart, canFinish, canOperate, canGeneratePackage, canReceiveNotice, canDelay, canEdit, canDelete].filter(Boolean)
            .length;
        return {
          ...item,
          pickupCode: formatPickupCode(item.pickupCode),
          package: item.package
            ? { ...item.package, pickupCode: formatPickupCode(item.package.pickupCode) }
            : item.package,
          stageText: stage.text,
          stageTheme: stage.theme,
          customerName: item.prescription
            ? item.prescription.customerName
            : '-',
          customerPhone: item.prescription ? item.prescription.phone : '-',
          doctorName: item.prescription?.doctor
            ? item.prescription.doctor.name
            : '-',
          processTypeName: item.processType ? item.processType.name : '-',
          isDecoction: item.processType?.code === 'DECOCTION',
          pickupMethodLabel: pickupMethodText(item.pickupMethod),
          storeName: item.store ? item.store.name : '',
          scheduleText:
            Number(item.scheduleType) === 2
              ? '等待顾客通知'
              : String(item.processDate || '').slice(0, 10),
          startDateText: formatDate(item.startDate),
          finishDateText: formatDate(item.finishDate),
          isUrgent: Number(item.priority) === 1,
          canStart,
          canFinish,
          canOperate,
          operationLabel: Number(item.status) === 1 ? '工序操作' : '工序详情',
          canGeneratePackage,
          canReceiveNotice,
          canDelay,
          canEdit,
          canDelete,
          actionCount,
          fewActions: actionCount <= 2,
          singleAction: actionCount === 1
        };
      }),
      pages: data.pagination?.pages || 1
    });
  },

  async loadPickupTasks() {
    const data = await getPackages({
      source: 'processing',
      dateScope: 'pickup-workbench',
      keyword: this.data.keyword,
      storeId: this.data.storeId,
      page: this.data.page,
      pageSize: this.data.pageSize
    });
    this.setData({
      list: (data.list || []).map((item) => {
        const overdue =
          Number(item.status) === 0 && dateText(item.createdAt) < dateText();
        return {
          ...item,
          pickupCode: formatPickupCode(item.pickupCode),
          processTypeName: item.processingPlan?.processType?.name || '-',
          processingPlanId: item.processingPlan?.id || null,
          totalDose: item.processingPlan?.totalDose || '-',
          storeName: item.store ? item.store.name : '',
          finishDateText: formatDate(item.processingPlan?.finishDate),
          pickedAtText: formatDate(item.pickedAt),
          scopeText: overdue ? '逾期未取' : '今日生成',
          scopeTheme: overdue ? 'danger' : 'primary',
          statusText: statusText(item.status),
          statusTheme: statusTheme(item.status),
          canVerify: Number(item.status) === 0,
          fewActions: true,
          singleAction: Number(item.status) !== 0 && !item.processingPlan?.id
        };
      }),
      pages: data.pagination?.pages || 1
    });
  },

  switchMode(e) {
    const mode = e.currentTarget.dataset.mode;
    if (mode === this.data.mode) return;
    this.setData({ mode, keyword: '', page: 1 });
    this.load();
  },

  switchView(e) {
    const activeView = e.currentTarget.dataset.view;
    if (activeView === this.data.activeView) return;
    this.setData({ activeView, page: 1 });
    this.load();
  },

  onKeywordChange(e) {
    this.setData({ keyword: e.detail.value });
  },

  search() {
    this.setData({ page: 1 });
    this.load();
  },

  onStoreChange(e) {
    const storeIndex = Number(e.detail.value);
    const store = this.data.stores[storeIndex];
    this.setData({
      storeIndex,
      storeId: store.id,
      storeName: store.name,
      page: 1
    });
    this.reloadAll();
  },

  prevPage() {
    if (this.data.page <= 1) return;
    this.setData({ page: this.data.page - 1 });
    this.load();
  },

  nextPage() {
    if (this.data.page >= this.data.pages) return;
    this.setData({ page: this.data.page + 1 });
    this.load();
  },

  findPlan(id) {
    return this.data.list.find((item) => Number(item.id) === Number(id));
  },

  goCreatePlan() {
    wx.navigateTo({
      url: '/pages/admin/processing-plan-batch/processing-plan-batch'
    });
  },

  scanPlan() {
    wx.scanCode({
      scanType: ['qrCode', 'barCode'],
      success: async (res) => {
        const plan = await findProcessingPlanByScan(res.result);
        wx.navigateTo({
          url: `/pages/admin/processing-operation/processing-operation?id=${plan.id}`
        });
      }
    });
  },

  openOperation(e) {
    wx.navigateTo({
      url: `/pages/admin/processing-operation/processing-operation?id=${e.currentTarget.dataset.id}`
    });
  },

  showPlanDetail(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    const lines = [
      `顾客：${plan.customerName} ${plan.customerPhone || ''}`,
      `加工：${plan.processTypeName}，第 ${plan.batchNo} 批，${plan.totalDose} 剂`,
      ...(plan.isDecoction ? [`袋数：${plan.bagCount} 袋`, `毫升数：${plan.volumeMl} ml`] : []),
      `计划开工：${plan.scheduleText}`,
      `状态：${plan.stageText}`,
      `提醒：${plan.notifyTypeDictionary?.name || '不提醒'}`,
      `收费：${Number(plan.paymentStatus) === 1 ? '已收费' : '未收费'}`,
      `取货方式：${plan.pickupMethodLabel}`,
      `实际开工：${plan.startDateText}`,
      `完成时间：${plan.finishDateText}`,
      `备注：${plan.processRemark || plan.remark || '-'}`
    ];
    wx.showModal({
      title: '加工计划详情',
      content: lines.join('\n'),
      showCancel: false,
      confirmText: '关闭'
    });
  },

  goPrescriptionDetail(e) {
    wx.navigateTo({
      url: `/pages/admin/prescription-detail/prescription-detail?id=${e.currentTarget.dataset.id}`
    });
  },

  editPlan(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    wx.setStorageSync('editingProcessingPlan', plan);
    wx.navigateTo({
      url: `/pages/admin/processing-plan-form/processing-plan-form?id=${plan.id}`
    });
  },

  removePlan(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    wx.showModal({
      title: '删除加工计划',
      content: `确认删除${plan.customerName}的${plan.processTypeName}加工计划？`,
      confirmColor: '#d54941',
      success: async (result) => {
        if (!result.confirm) return;
        await deleteProcessingPlan(plan.id);
        wx.showToast({ title: '已删除', icon: 'success' });
        await this.reloadAll();
      }
    });
  },

  async startPlan(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    const confirmed = await confirmAction(
      '开始加工',
      `确认开始加工${plan.customerName}的${plan.processTypeName} ${plan.totalDose}剂吗？`
    );
    if (!confirmed) return;
    await transitionProcessingPlan(plan.id, 1);
    wx.showToast({ title: '已开始加工', icon: 'success' });
    wx.navigateTo({
      url: `/pages/admin/processing-operation/processing-operation?id=${plan.id}`
    });
  },

  async finishPlan(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    const index = await chooseAction(['生成包裹', '不生成包裹']);
    if (index < 0) return;
    const createPackage = index === 0;
    await transitionProcessingPlan(plan.id, 2, { createPackage });
    wx.showToast({
      title: createPackage ? '已完成并生成包裹' : '加工已完成',
      icon: 'success'
    });
    if (!createPackage) {
      this.setData({ activeView: 'today-all', page: 1 });
    }
    await this.reloadAll();
  },

  async generatePlanPackage(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    const confirmed = await confirmAction(
      '生成包裹',
      `确认为${plan.customerName}的${plan.processTypeName}生成待领取包裹吗？`
    );
    if (!confirmed) return;
    await generateProcessingPlanPackage(plan.id);
    wx.showToast({ title: '包裹已生成', icon: 'success' });
    await this.reloadAll();
  },

  async receiveNotice(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    const index = await chooseAction(['安排今天加工', '安排明天加工']);
    if (index < 0) return;
    await receiveProcessingNotice(plan.id, {
      processDate: dateText(new Date(), index)
    });
    wx.showToast({ title: '已加入日程', icon: 'success' });
    await this.reloadAll();
  },

  async delayPlan(e) {
    const plan = this.findPlan(e.currentTarget.dataset.id);
    if (!plan) return;
    const index = await chooseAction([
      '延期到明天',
      '延期到后天',
      '改为等待顾客'
    ]);
    if (index < 0) return;
    const data =
      index === 2
        ? { scheduleType: 2, processDate: null }
        : {
            scheduleType: 1,
            processDate: dateText(new Date(), index + 1)
          };
    await delayProcessingPlan(plan.id, data);
    wx.showToast({ title: '延期已保存', icon: 'success' });
    await this.reloadAll();
  },

  goPackageDetail(e) {
    wx.navigateTo({
      url: `/pages/admin/package-detail/package-detail?id=${e.currentTarget.dataset.id}`
    });
  },

  goVerify(e) {
    wx.navigateTo({
      url: `/pages/admin/verify/verify?pickupCode=${encodeURIComponent(e.currentTarget.dataset.code)}`
    });
  }
});
