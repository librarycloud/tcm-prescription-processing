import {
  finishProcessingEquipmentUsage,
  getProcessingWorkflow,
  startProcessingEquipmentUsage,
  transitionProcessingPlan,
  uploadDispensingPhoto
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

function durationText(start, end) {
  if (!start) return '-';
  const endTime = end ? new Date(end).getTime() : Date.now();
  const minutes = Math.max(0, Math.floor((endTime - new Date(start).getTime()) / 60000));
  if (minutes < 60) return `${minutes}分钟`;
  return `${Math.floor(minutes / 60)}小时${minutes % 60}分钟`;
}

function scanEquipment() {
  return new Promise((resolve, reject) => {
    wx.scanCode({
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
    usageHistory: [],
    canUploadPhoto: false,
    canStartSoaking: false,
    canFinish: false
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
        startedAtText: formatDate(item.startedAt),
        endedAtText: item.endedAt ? formatDate(item.endedAt) : '进行中',
        durationText: durationText(item.startedAt, item.endedAt)
      }));
      const activeSoakings = usages.filter((item) => Number(item.stage) === 3 && !item.endedAt);
      const activeDecoctions = usages.filter(
        (item) => Number(item.stage) === 4 && !item.endedAt
      );
      const inProgress = Number(detail.status) === 1;
      const viewDetail = {
        ...detail,
        dispensingCompletedAtText: detail.dispensingCompletedAt
          ? formatDate(detail.dispensingCompletedAt)
          : ''
      };
      this.setData({
        detail: viewDetail,
        stageName: STAGE_NAMES[detail.currentStage] || (Number(detail.status) === 0 ? '待加工' : '-'),
        activeSoakings,
        activeDecoctions,
        usageHistory: usages,
        canUploadPhoto: inProgress && [1, 2].includes(Number(detail.currentStage)),
        canStartSoaking:
          inProgress && detail.isDecoction && [2, 3].includes(Number(detail.currentStage)),
        canFinish: inProgress && detail.canCompleteWorkflow
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
      sizeType: ['compressed'],
      success: async (res) => {
        const filePath = res.tempFiles?.[0]?.tempFilePath;
        if (!filePath) return;
        this.setData({ loading: true });
        try {
          await uploadDispensingPhoto(this.data.id, filePath);
          wx.showToast({ title: '调配已完成', icon: 'success' });
          await this.load();
        } finally {
          this.setData({ loading: false });
        }
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
      equipmentCode: code
    });
    wx.showToast({ title: `第${portionNo}份开始浸泡`, icon: 'success' });
    await this.load();
  },

  async startDecoction(e) {
    const code = await scanEquipment().catch(() => '');
    if (!code) return;
    const portionNo = Number(e.currentTarget.dataset.portion);
    await startProcessingEquipmentUsage(this.data.id, {
      stage: 4,
      portionNo,
      equipmentCode: code
    });
    wx.showToast({ title: `第${portionNo}份开始煎煮`, icon: 'success' });
    await this.load();
  },

  async finishWithPackaging(e) {
    const code = await scanEquipment().catch(() => '');
    if (!code) return;
    await finishProcessingEquipmentUsage(this.data.id, e.currentTarget.dataset.usage, {
      equipmentCode: code
    });
    wx.showToast({ title: '煎煮和打包已记录', icon: 'success' });
    await this.load();
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

  async finishPlan() {
    const createPackage = await chooseCompletionMode();
    if (createPackage === null) return;
    await transitionProcessingPlan(this.data.id, 2, { createPackage });
    wx.showToast({
      title: createPackage ? '已完成并生成包裹' : '加工已完成',
      icon: 'success'
    });
    setTimeout(() => wx.navigateBack(), 500);
  }
});
