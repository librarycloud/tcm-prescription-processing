import { safeScanCode } from '../../../utils/scanner';
import {
  deleteDispensingPhoto,
  finishProcessingEquipmentUsage,
  getProcessingWorkflow,
  startProcessingEquipmentUsage,
  startPackagingEquipmentUsage,
  transferFaultyProcessingEquipment,
  transitionProcessingPlan,
  uploadDispensingPhoto,
  voidProcessingEquipmentUsage
} from '../../../api/admin';
import { getBaseUrl } from '../../../utils/config';
import { getToken } from '../../../utils/auth';
import { formatDate } from '../../../utils/format';

const STAGE_NAMES = {
  1: '调配中',
  2: '调配完成',
  3: '浸泡中',
  4: '煎煮中',
  5: '打包中',
  6: '打包完成',
  7: '加工完成'
};

const USAGE_STAGE_NAMES = {
  3: '浸泡',
  4: '煎煮',
  5: '打包'
};

const PHOTO_MAX_SIZE = 5 * 1024 * 1024;

const USAGE_STATUS_NAMES = {
  1: '进行中',
  2: '已完成',
  3: '已作废'
};

const USAGE_SOURCE_NAMES = {
  1: '扫码记录',
  2: '人工补录',
  3: '故障接续'
};

const EXCEPTION_TYPE_NAMES = {
  1: '误扫撤销',
  2: '设备故障换机',
  3: '人工补录'
};

function newRequestId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

function promptReason(title, placeholder) {
  return new Promise((resolve) => {
    wx.showModal({
      title,
      editable: true,
      placeholderText: placeholder,
      confirmText: '确认',
      success: (res) => resolve(res.confirm ? String(res.content || '').trim() : ''),
      fail: () => resolve('')
    });
  });
}

function localFileSize(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileInfo({
      filePath,
      success: (res) => resolve(Number(res.size) || 0),
      fail: reject
    });
  });
}

function compressPhoto(filePath, options) {
  return new Promise((resolve, reject) => {
    wx.compressImage({
      src: filePath,
      quality: options.quality,
      compressedWidth: options.width,
      success: (res) => resolve(res.tempFilePath),
      fail: reject
    });
  });
}

async function preparePhotoForUpload(file) {
  const originalPath = file?.tempFilePath;
  if (!originalPath) return '';
  const originalSize = Number(file.size) || (await localFileSize(originalPath));
  if (originalSize <= PHOTO_MAX_SIZE) return originalPath;

  const strategies = [
    { quality: 90, width: 3000 },
    { quality: 82, width: 2400 },
    { quality: 75, width: 1920 }
  ];
  try {
    for (const strategy of strategies) {
      const compressedPath = await compressPhoto(originalPath, strategy);
      if ((await localFileSize(compressedPath)) <= PHOTO_MAX_SIZE) return compressedPath;
    }
  } catch (error) {
    error.photoPreparationFailed = true;
    throw error;
  }

  const error = new Error('图片压缩后仍超过 5MB');
  error.photoPreparationFailed = true;
  throw error;
}

function durationText(start, end) {
  if (!start) return '-';
  const endTime = end ? new Date(end).getTime() : Date.now();
  const minutes = Math.max(0, Math.floor((endTime - new Date(start).getTime()) / 60000));
  if (minutes < 60) return `${minutes}分钟`;
  return `${Math.floor(minutes / 60)}小时${minutes % 60}分钟`;
}

function scanEquipment() {
  return new Promise((resolve, reject) => {
    safeScanCode({
      scanType: ['qrCode', 'barCode'],
      success: (res) => resolve(res.result),
      fail: reject
    });
  });
}

function chooseCompletionMode() {
  return new Promise((resolve) => {
    wx.showActionSheet({
      itemList: ['完成并生成包裹', '仅完成加工'],
      success: (res) => resolve(res.tapIndex === 0),
      fail: () => resolve(null)
    });
  });
}

Page({
  data: {
    id: null,
    loading: false,
    detail: null,
    stageName: '-',
    activeSoakings: [],
    activeDecoctions: [],
    activePackagings: [],
    usageHistory: [],
    showProcessingSteps: false,
    processingFinished: false,
    packagingCompleted: false,
    canDeletePhotos: false,
    canUploadPhoto: false,
    canCancel: false,
    canStartSoaking: false,
    canFinish: false,
    finishing: false
  },

  onLoad(options) {
    this.setData({ id: Number(options.id) });
  },

  onShow() {
    this.load();
  },

  async load() {
    if (!this.data.id) return;
    this.setData({ loading: true });
    try {
      const detail = await getProcessingWorkflow(this.data.id);
      const usages = (detail.equipmentUsages || []).map((item) => ({
        ...item,
        stageName: USAGE_STAGE_NAMES[Number(item.stage)] || '-',
        operatorName: item.operator?.nickname || item.operator?.name || item.operator?.phone || '-',
        startedAtText: formatDate(item.startedAt),
        endedAtText: item.endedAt ? formatDate(item.endedAt) : '进行中',
        durationText: durationText(item.startedAt, item.endedAt),
        statusName: USAGE_STATUS_NAMES[Number(item.status)] || '-',
        sourceName: USAGE_SOURCE_NAMES[Number(item.source)] || '-'
      }));
      const activeSoakings = usages.filter(
        (item) => Number(item.stage) === 3 && Number(item.status) === 1
      );
      const activeDecoctions = usages.filter(
        (item) => Number(item.stage) === 4 && Number(item.status) === 1
      );
      const activePackagings = usages.filter(
        (item) => Number(item.stage) === 5 && Number(item.status) === 1
      );
      const inProgress = Number(detail.status) === 1;
      const processingFinished = [2, 3, 4].includes(Number(detail.status));
      const viewDetail = {
        ...detail,
        completionBlockers: detail.completionBlockers || [],
        workflowExceptions: (detail.workflowExceptions || []).map((item) => ({
          ...item,
          typeName: EXCEPTION_TYPE_NAMES[Number(item.type)] || '异常处理',
          createdAtText: formatDate(item.createdAt),
          operatorName: item.creator?.nickname || item.creator?.name || item.creator?.phone || '-'
        })),
        dispensingCompletedAtText: detail.dispensingCompletedAt
          ? formatDate(detail.dispensingCompletedAt)
          : ''
      };
      this.setData({
        detail: viewDetail,
        stageName:
          STAGE_NAMES[detail.currentStage] || (Number(detail.status) === 0 ? '待加工' : '-'),
        activeSoakings,
        activeDecoctions,
        activePackagings,
        usageHistory: usages,
        showProcessingSteps: [1, 2, 3, 4].includes(Number(detail.status)),
        processingFinished,
        packagingCompleted: processingFinished || [6, 7].includes(Number(detail.currentStage)),
        canDeletePhotos: inProgress && [1, 2].includes(Number(detail.currentStage)),
        canUploadPhoto: inProgress && [1, 2].includes(Number(detail.currentStage)),
        canCancel: inProgress && Number(detail.currentStage) === 1 && !detail.dispensingCompletedAt,
        canStartSoaking:
          inProgress && detail.isDecoction && [2, 3].includes(Number(detail.currentStage)),
        canFinish: inProgress && (detail.canCompleteWorkflow || detail.canFinalizeWorkflow)
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  async startPlan() {
    await transitionProcessingPlan(this.data.id, 1);
    wx.showToast({ title: '已开始调配', icon: 'success' });
    await this.load();
  },

  takeDispensingPhoto() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera', 'album'],
      sizeType: ['original'],
      camera: 'back',
      success: async (res) => {
        const selectedFile = res.tempFiles?.[0];
        const filePath = selectedFile?.tempFilePath;
        if (!filePath) return;
        this.setData({ loading: true });
        try {
          const uploadPath = await preparePhotoForUpload(selectedFile);
          await uploadDispensingPhoto(this.data.id, uploadPath);
          wx.showToast({ title: '调配已完成', icon: 'success' });
          await this.load();
        } catch (error) {
          if (error.photoPreparationFailed) {
            wx.showToast({
              title: error.message || '图片压缩失败',
              icon: 'none'
            });
          }
        } finally {
          this.setData({ loading: false });
        }
      }
    });
  },

  cancelPlan() {
    wx.showModal({
      title: '取消加工',
      content: '确认取消当前加工计划吗？',
      confirmColor: '#d54941',
      success: async (result) => {
        if (!result.confirm) return;
        await transitionProcessingPlan(this.data.id, 5);
        wx.showToast({ title: '加工已取消', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 500);
      }
    });
  },

  async startSoaking() {
    const code = await scanEquipment().catch(() => '');
    if (!code) return;
    const usedPortions = (this.data.detail.equipmentUsages || [])
      .filter((item) => Number(item.stage) === 3)
      .map((item) => Number(item.portionNo));
    const portionNo = usedPortions.length ? Math.max(...usedPortions) + 1 : 1;
    await startProcessingEquipmentUsage(this.data.id, {
      stage: 3,
      portionNo,
      equipmentCode: code,
      requestId: newRequestId()
    });
    wx.showToast({ title: `第${portionNo}组开始浸泡`, icon: 'success' });
    await this.load();
  },

  async startDecoction(e) {
    const code = await scanEquipment().catch(() => '');
    if (!code) return;
    const portionNo = Number(e.currentTarget.dataset.portion);
    await startProcessingEquipmentUsage(this.data.id, {
      stage: 4,
      portionNo,
      equipmentCode: code,
      requestId: newRequestId()
    });
    wx.showToast({ title: `第${portionNo}组开始煎煮`, icon: 'success' });
    await this.load();
  },

  async startPackaging(e) {
    const code = await scanEquipment().catch(() => '');
    if (!code) return;
    await startPackagingEquipmentUsage(this.data.id, e.currentTarget.dataset.usage, {
      equipmentCode: code,
      requestId: newRequestId()
    });
    wx.showToast({ title: '已开始打包', icon: 'success' });
    await this.load();
  },

  async handleUsageException(e) {
    const usageId = Number(e.currentTarget.dataset.usage);
    if (!usageId) return;
    const usage = (this.data.detail.equipmentUsages || []).find(
      (item) => Number(item.id) === usageId
    );
    if (!usage || Number(usage.status) !== 1) {
      return wx.showToast({ title: '该记录已结束', icon: 'none' });
    }
    wx.showActionSheet({
      itemList: ['撤销误扫', '设备故障并换机'],
      success: async (res) => {
        if (res.tapIndex === 0) {
          const reason = await promptReason('撤销误扫', '请填写撤销原因');
          if (!reason) return;
          await voidProcessingEquipmentUsage(this.data.id, usageId, { reason });
          wx.showToast({ title: '误扫已撤销', icon: 'success' });
          await this.load();
          return;
        }
        const reason = await promptReason('设备故障', '请填写故障原因');
        if (!reason) return;
        const equipmentCode = await scanEquipment().catch(() => '');
        if (!equipmentCode) return;
        await transferFaultyProcessingEquipment(this.data.id, usageId, {
          reason,
          equipmentCode,
          requestId: newRequestId()
        });
        wx.showToast({ title: '已更换设备', icon: 'success' });
        await this.load();
      }
    });
  },

  viewPhoto(e) {
    const photoId = e.currentTarget.dataset.id;
    wx.downloadFile({
      url: `${getBaseUrl()}/admin/processing-plans/${this.data.id}/photos/${photoId}`,
      header: { Authorization: `Bearer ${getToken()}` },
      success: (res) => {
        if (res.statusCode === 200) wx.previewImage({ urls: [res.tempFilePath] });
        else wx.showToast({ title: '照片加载失败', icon: 'none' });
      },
      fail: () => wx.showToast({ title: '照片加载失败', icon: 'none' })
    });
  },

  deletePhoto(e) {
    const photoId = Number(e.currentTarget.dataset.id);
    if (!photoId) return;
    wx.showModal({
      title: '删除照片',
      content: '确认删除这张调配照片？',
      confirmColor: '#d54941',
      success: async (result) => {
        if (!result.confirm) return;
        await deleteDispensingPhoto(this.data.id, photoId);
        wx.showToast({ title: '照片已删除', icon: 'success' });
        await this.load();
      }
    });
  },

  async finishPlan() {
    if (this.data.finishing) return;
    const createPackage = await chooseCompletionMode();
    if (createPackage === null) return;
    this.setData({ finishing: true });
    try {
      for (const usage of this.data.activePackagings) {
        await finishProcessingEquipmentUsage(this.data.id, usage.id, {
          requestId: newRequestId()
        });
      }
      await transitionProcessingPlan(this.data.id, 2, { createPackage });
      wx.showToast({
        title: createPackage ? '已完成并生成包裹' : '加工已完成',
        icon: 'success'
      });
      await this.load();
    } finally {
      this.setData({ finishing: false });
    }
  }
});
