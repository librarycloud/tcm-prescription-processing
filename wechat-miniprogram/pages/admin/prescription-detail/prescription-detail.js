import {
  deletePrescriptionAttachment,
  deleteProcessingPlan,
  getPrescriptionDetail,
  uploadPrescriptionAttachment
} from '../../../api/admin';
import { getToken, getUser } from '../../../utils/auth';
import { getBaseUrl } from '../../../utils/config';
import { formatDate, formatPickupCode } from '../../../utils/format';
import {
  choosePrescriptionAttachment,
  formatAttachmentSize
} from '../../../utils/prescription-attachment';

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

Page({
  data: {
    id: null,
    detail: null,
    plans: [],
    isStoreStaff: false,
    attachmentDeleting: false,
    attachmentPreparing: false,
    attachmentUploading: false,
    attachmentPreviewing: false
  },

  onLoad(options) {
    this.setData({ id: options.id, isStoreStaff: Number(getUser()?.role) === 3 });
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
              fileSizeText: formatAttachmentSize(item.attachment.fileSize)
            }
          : null,
        canEdit: !this.data.isStoreStaff && Number(item.status) !== 1,
        canAddPlan: !this.data.isStoreStaff && Number(item.status) === 0
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
          canEdit: !this.data.isStoreStaff && [0, 1].includes(Number(plan.status)),
          canDelete: !this.data.isStoreStaff && Number(plan.status) === 0
        };
      })
    });
  },

  goProcessing() {
    wx.navigateTo({
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

  async selectAndUploadAttachment() {
    if (this.data.attachmentPreparing || this.data.attachmentUploading) return;
    this.setData({ attachmentPreparing: true });
    let attachment = null;
    try {
      attachment = await choosePrescriptionAttachment();
    } catch (error) {
      wx.showToast({ title: error.message || '文件处理失败', icon: 'none' });
    } finally {
      this.setData({ attachmentPreparing: false });
    }
    if (!attachment) return;

    this.setData({ attachmentUploading: true });
    try {
      await uploadPrescriptionAttachment(this.data.id, attachment);
      wx.showToast({
        title: attachment.compressed ? '已压缩并上传' : '上传成功',
        icon: 'success'
      });
      await this.load();
    } finally {
      this.setData({ attachmentUploading: false });
    }
  },

  previewAttachment() {
    const attachment = this.data.detail?.attachment;
    if (!attachment || this.data.attachmentPreviewing) return;
    this.setData({ attachmentPreviewing: true });
    wx.downloadFile({
      url: `${getBaseUrl()}/admin/prescriptions/${this.data.id}/attachment`,
      header: { Authorization: `Bearer ${getToken()}` },
      success: (res) => {
        if (res.statusCode !== 200) {
          wx.showToast({ title: '处方原件加载失败', icon: 'none' });
          return;
        }
        if (String(attachment.mimeType || '').startsWith('image/')) {
          wx.previewImage({ urls: [res.tempFilePath] });
          return;
        }
        wx.openDocument({
          filePath: res.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          fail: () => wx.showToast({ title: 'PDF 打开失败', icon: 'none' })
        });
      },
      fail: () => wx.showToast({ title: '处方原件加载失败', icon: 'none' }),
      complete: () => this.setData({ attachmentPreviewing: false })
    });
  },

  goPackage(e) {
    wx.navigateTo({
      url: `/pages/admin/package-detail/package-detail?id=${e.currentTarget.dataset.id}`
    });
  }
});
