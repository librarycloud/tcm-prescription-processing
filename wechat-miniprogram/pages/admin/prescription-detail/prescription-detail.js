import {
  deletePrescriptionAttachment,
  deleteProcessingPlan,
  getPrescriptionDetail
} from '../../../api/admin';
import { formatDate, formatPickupCode } from '../../../utils/format';

const PRESCRIPTION_STATUS = {
  0: { text: '进行中', theme: 'primary' },
  1: { text: '已完成', theme: 'success' },
  2: { text: '已取消', theme: 'default' }
};

const PLAN_STATUS = {
  0: { text: '待加工', theme: 'default' },
  1: { text: '加工中', theme: 'primary' },
  2: { text: '加工完成', theme: 'success' },
  3: { text: '待领取', theme: 'warning' },
  4: { text: '已领取', theme: 'success' },
  5: { text: '已取消', theme: 'default' }
};

function formatFileSize(value) {
  const size = Number(value);
  if (!Number.isFinite(size) || size <= 0) return '0 B';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

Page({
  data: {
    id: null,
    detail: null,
    plans: [],
    attachmentDeleting: false
  },

  onLoad(options) {
    this.setData({ id: options.id });
  },

  onShow() {
    if (this.data.id) this.load();
  },

  async load() {
    const item = await getPrescriptionDetail(this.data.id);
    const prescriptionStatus =
      PRESCRIPTION_STATUS[Number(item.status)] || PRESCRIPTION_STATUS[0];
    this.setData({
      detail: {
        ...item,
        statusText: prescriptionStatus.text,
        statusTheme: prescriptionStatus.theme,
        createdAtText: formatDate(item.createdAt),
        doctorName: item.doctor ? item.doctor.name : '-',
        sourceName: item.source ? item.source.name : '-',
        storeName: item.store ? item.store.name : '-',
        creatorName: item.creator
          ? item.creator.nickname || item.creator.phone
          : '-',
        attachment: item.attachment
          ? {
              ...item.attachment,
              fileSizeText: formatFileSize(item.attachment.fileSize)
            }
          : null,
        canEdit: Number(item.status) !== 1,
        canAddPlan: Number(item.status) === 0
      },
      plans: (item.plans || []).map((plan) => {
        const status = PLAN_STATUS[plan.status] || {
          text: plan.status,
          theme: 'default'
        };
        return {
          ...plan,
          pickupCode: formatPickupCode(plan.pickupCode),
          package: plan.package
            ? { ...plan.package, pickupCode: formatPickupCode(plan.package.pickupCode) }
            : plan.package,
          statusText: status.text,
          statusTheme: status.theme,
          processTypeName: plan.processType ? plan.processType.name : '-',
          isDecoction: plan.processType?.code === 'DECOCTION',
          scheduleText:
            Number(plan.scheduleType) === 2
              ? '等待顾客通知'
              : String(plan.processDate || '').slice(0, 10),
          finishDateText: formatDate(plan.finishDate),
          canViewWorkflow: [1, 2, 3, 4].includes(Number(plan.status)),
          workflowLabel: Number(plan.status) === 1 ? '工序操作' : '工序详情',
          canEdit: [0, 1].includes(Number(plan.status)),
          canDelete: Number(plan.status) === 0
        };
      })
    });
  },

  goProcessing() {
    wx.redirectTo({
      url: '/pages/admin/processing-workbench/processing-workbench'
    });
  },

  editPrescription() {
    wx.navigateTo({
      url: `/pages/admin/prescription-form/prescription-form?id=${this.data.id}`
    });
  },

  createPlan() {
    wx.setStorageSync('processingPlanPreset', {
      prescriptionId: Number(this.data.id),
      prescription: this.data.detail
    });
    wx.navigateTo({
      url: `/pages/admin/processing-plan-batch/processing-plan-batch?prescriptionId=${this.data.id}`
    });
  },

  editPlan(e) {
    const plan = this.data.plans.find(
      (item) => Number(item.id) === Number(e.currentTarget.dataset.id)
    );
    if (!plan) return;
    wx.setStorageSync('editingProcessingPlan', {
      ...plan,
      prescription: this.data.detail
    });
    wx.navigateTo({
      url: `/pages/admin/processing-plan-form/processing-plan-form?id=${plan.id}`
    });
  },

  goWorkflow(e) {
    wx.navigateTo({
      url: `/pages/admin/processing-operation/processing-operation?id=${e.currentTarget.dataset.id}`
    });
  },

  removePlan(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '删除加工计划',
      content: '确认删除该加工批次？历史操作记录仍会保留。',
      confirmColor: '#d54941',
      success: async (result) => {
        if (!result.confirm) return;
        await deleteProcessingPlan(id);
        wx.showToast({ title: '已删除', icon: 'success' });
        await this.load();
      }
    });
  },

  removeAttachment() {
    if (!this.data.detail?.attachment || this.data.attachmentDeleting) return;
    wx.showModal({
      title: '删除处方原件',
      content: '确认删除该处方原件？删除后无法恢复。',
      confirmColor: '#d54941',
      success: async (result) => {
        if (!result.confirm) return;
        this.setData({ attachmentDeleting: true });
        try {
          await deletePrescriptionAttachment(this.data.id);
          wx.showToast({ title: '已删除', icon: 'success' });
          await this.load();
        } finally {
          this.setData({ attachmentDeleting: false });
        }
      }
    });
  },

  goPackage(e) {
    wx.navigateTo({
      url: `/pages/admin/package-detail/package-detail?id=${e.currentTarget.dataset.id}`
    });
  }
});
