<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">客户端展示设置</h1>
        <p class="page-subtitle">管理右上角弹窗展示的微信小程序码、Android App 下载源与版本更新说明</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadSettings">刷新</el-button>
    </div>

    <div class="cards-grid">
      <!-- 卡片 1: 微信小程序 -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-title-row">
            <div class="card-title">
              <el-icon><ChatDotRound /></el-icon>
              <span>微信小程序</span>
            </div>
            <el-tag :type="form.wechat.hasQrcode ? 'success' : 'info'" size="small">
              {{ form.wechat.hasQrcode ? '已上传二维码' : '未上传二维码' }}
            </el-tag>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="小程序名称">
            <el-input v-model.trim="form.wechat.appName" placeholder="例如：药房助手" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="小程序 AppID" class="flex-1">
              <el-input v-model.trim="form.wechat.appId" placeholder="留空则使用系统环境变量 WX_APPID" />
            </el-form-item>
            <el-form-item label="小程序 AppSecret" class="flex-1">
              <el-input
                v-model="form.wechat.appSecret"
                type="password"
                show-password
                :placeholder="form.wechat.hasSecret ? '已配置密钥，留空不修改' : '留空则使用环境变量 WX_SECRET'"
              />
            </el-form-item>
          </div>

          <el-form-item label="微信小程序码生成与上传">
            <div class="wechat-generate-banner">
              <div class="generate-btn-group">
                <el-button
                  type="success"
                  :icon="MagicStick"
                  :loading="generating"
                  @click="handleGenerateWechatCode"
                >
                  一键从微信官方生成小程序码
                </el-button>
                <el-popover placement="bottom" :width="280" trigger="click">
                  <template #reference>
                    <el-button text type="primary" size="small">
                      <el-icon><Setting /></el-icon>
                      <span>生成参数</span>
                    </el-button>
                  </template>
                  <div class="gen-params-form">
                    <div class="params-title">微信小程序码参数设置</div>
                    <div class="params-item">
                      <span class="params-label">版本环境</span>
                      <el-select v-model="genOptions.envVersion" size="small" style="width: 100%;">
                        <el-option label="正式版 (release)" value="release" />
                        <el-option label="体验版 (trial)" value="trial" />
                        <el-option label="开发版 (develop)" value="develop" />
                      </el-select>
                    </div>
                    <div class="params-item" style="margin-top: 8px;">
                      <span class="params-label">落地页路径（选填）</span>
                      <el-input v-model.trim="genOptions.page" size="small" placeholder="默认为主页" />
                    </div>
                  </div>
                </el-popover>
              </div>
              <span class="generate-tip">基于微信官方接口一键生成官方圆形菊花码；亦可手动上传自定义图片。</span>
            </div>

            <div class="qrcode-upload-area">
              <div v-if="qrcodePreviewUrl" class="qrcode-preview-box">
                <el-image
                  :src="qrcodePreviewUrl"
                  class="qrcode-img"
                  fit="contain"
                  :preview-src-list="[qrcodePreviewUrl]"
                />
                <div class="qrcode-meta">
                  <div class="qrcode-badge-row">
                    <el-tag type="success" size="small">当前已生效</el-tag>
                    <span class="qrcode-tip">可在右上角弹窗即刻体验扫码</span>
                  </div>
                  <div class="qrcode-btns">
                    <el-button size="small" type="primary" plain @click="triggerUpload">手动替换图片</el-button>
                  </div>
                </div>
              </div>
              <el-upload
                ref="uploadRef"
                class="qrcode-uploader"
                :show-file-list="false"
                :auto-upload="false"
                :on-change="handleFileSelected"
                accept="image/*"
                drag
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="el-upload__text">
                  手动上传：拖到此处，或<em>点击上传</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">支持 PNG / JPG / WebP 格式，最大 5MB</div>
                </template>
              </el-upload>
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 卡片 2: Android App 与 App Release Hub -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-title-row">
            <div class="card-title">
              <el-icon><Cellphone /></el-icon>
              <span>Android App (App Release Hub)</span>
            </div>
            <el-tag :type="hubProbe?.connected ? 'success' : 'warning'" size="small">
              {{ hubProbe?.connected ? 'Hub 连接正常' : 'Hub 未连接/需排查' }}
            </el-tag>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="客户端显示名称">
            <el-input v-model.trim="form.android.displayName" placeholder="例如：药房助手 Android 版" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="App Release Hub 地址" class="flex-1">
              <el-input
                v-model.trim="form.android.releaseHubUrl"
                placeholder="如留空则使用系统环境变量配置"
              />
            </el-form-item>
            <el-form-item label="Hub 应用 ID (App ID)" class="w-180">
              <el-input
                v-model.trim="form.android.releaseHubAppId"
                placeholder="默认：tcm-admin"
              />
            </el-form-item>
          </div>
          <el-form-item label="自定义下载地址（可选）">
            <el-input
              v-model.trim="form.android.customDownloadUrl"
              placeholder="留空则自动使用 Hub 最新 APK 下载链接；填写后将覆盖自动链接"
            />
          </el-form-item>
        </el-form>

        <!-- Hub 探测结果与 APK 下载二维码面板 -->
        <div class="hub-probe-panel">
          <div class="probe-title">Android 下载二维码与 Hub 状态</div>
          <div v-if="effectiveDownloadUrl" class="probe-content">
            <div class="apk-download-showcase">
              <div class="apk-qr-box">
                <img v-if="apkQrDataUrl" :src="apkQrDataUrl" class="apk-qr-img" alt="APK下载二维码" />
                <div v-else class="qr-loading">生成中...</div>
                <span class="apk-qr-tip">手机扫码直接下载</span>
              </div>
              <div class="apk-info-box">
                <div class="probe-badge-row">
                  <span class="probe-version">版本：v{{ hubProbe?.versionName || '-' }} ({{ hubProbe?.versionCode || '-' }})</span>
                  <span v-if="hubProbe?.size" class="probe-size">大小：{{ formatSize(hubProbe.size) }}</span>
                  <span v-if="hubProbe?.publishedAt" class="probe-date">发布：{{ hubProbe.publishedAt }}</span>
                </div>
                <div class="probe-link">
                  <span class="link-label">有效下载链接：</span>
                  <el-link :href="effectiveDownloadUrl" target="_blank" type="primary" :underline="false">
                    {{ effectiveDownloadUrl }}
                  </el-link>
                </div>
                <div style="margin-top: 8px;">
                  <el-button size="small" type="primary" :icon="Download" @click="downloadApk(effectiveDownloadUrl)">
                    测试下载 APK
                  </el-button>
                </div>
                <div v-if="hubProbe?.releaseNotes?.length" class="probe-notes">
                  <div class="notes-header">版本更新说明：</div>
                  <ul class="notes-list">
                    <li v-for="(note, idx) in hubProbe.releaseNotes" :key="idx">{{ note }}</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="probe-empty">
            <el-icon><Warning /></el-icon>
            <span>{{ hubProbe?.message || '暂无可用下载链接，请确认 Hub 地址或填写自定义下载地址' }}</span>
          </div>
        </div>
      </el-card>

      <!-- 卡片 3: 公告与温馨提示 -->
      <el-card shadow="never" class="config-card full-width">
        <template #header>
          <div class="card-title">
            <el-icon><Bell /></el-icon>
            <span>弹窗公告与使用提示（选填）</span>
          </div>
        </template>
        <el-form label-position="top">
          <el-form-item label="在右上角下载弹窗底部展示的公告内容">
            <el-input
              v-model="form.announcement"
              type="textarea"
              :rows="3"
              placeholder="例如：Android 手机首次安装请允许安装未知应用来源权限。如有疑问请联系管理员。"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <div class="form-actions">
      <el-button type="primary" :loading="saving" size="large" @click="saveSettings">
        保存配置
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import QRCode from 'qrcode';
import {
  Bell,
  Cellphone,
  ChatDotRound,
  Download,
  MagicStick,
  Refresh,
  Setting,
  UploadFilled,
  Warning
} from '@element-plus/icons-vue';
import {
  getClientDisplaySettings,
  updateClientDisplaySettings,
  uploadWechatQrcode,
  generateWechatQrcode
} from '@/api/clientDisplay';

const loading = ref(false);
const saving = ref(false);
const generating = ref(false);
const uploadRef = ref(null);

const genOptions = ref({
  envVersion: 'release',
  page: ''
});

const form = ref({
  wechat: {
    appName: '药房助手',
    appId: '',
    appSecret: '',
    hasSecret: false,
    hasQrcode: false
  },
  android: {
    releaseHubUrl: '',
    releaseHubAppId: '',
    customDownloadUrl: '',
    displayName: '药房助手 Android 版'
  },
  announcement: ''
});

const hubProbe = ref(null);
const qrcodePreviewUrl = ref('');
const apkQrDataUrl = ref('');

const effectiveDownloadUrl = computed(() => {
  return form.value.android.customDownloadUrl || hubProbe.value?.downloadUrl || '';
});

watch(effectiveDownloadUrl, async (newUrl) => {
  if (newUrl) {
    try {
      apkQrDataUrl.value = await QRCode.toDataURL(newUrl, {
        width: 140,
        margin: 1,
        color: { dark: '#000000', light: '#ffffff' }
      });
    } catch {
      apkQrDataUrl.value = '';
    }
  } else {
    apkQrDataUrl.value = '';
  }
}, { immediate: true });

function downloadApk(url) {
  if (!url) return;
  window.open(url, '_blank');
}

function formatSize(bytes) {
  if (!bytes) return '未知';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

async function loadSettings() {
  loading.value = true;
  try {
    const res = await getClientDisplaySettings();
    if (res?.config) {
      form.value = {
        wechat: { ...form.value.wechat, ...(res.config.wechat || {}) },
        android: { ...form.value.android, ...(res.config.android || {}) },
        announcement: res.config.announcement || ''
      };
      if (res.config.wechat?.qrcodeUrl) {
        qrcodePreviewUrl.value = res.config.wechat.qrcodeUrl;
      } else {
        qrcodePreviewUrl.value = '';
      }
    }
    hubProbe.value = res?.hubProbe || null;
  } catch {
    // 错误已由 request 拦截器处理
  } finally {
    loading.value = false;
  }
}

async function saveSettings() {
  saving.value = true;
  try {
    const res = await updateClientDisplaySettings(form.value);
    ElMessage.success('配置已保存成功');
    if (res?.config) {
      form.value = {
        wechat: { ...form.value.wechat, ...(res.config.wechat || {}) },
        android: { ...form.value.android, ...(res.config.android || {}) },
        announcement: res.config.announcement || ''
      };
      if (res.config.wechat?.qrcodeUrl) {
        qrcodePreviewUrl.value = res.config.wechat.qrcodeUrl;
      }
    }
    hubProbe.value = res?.hubProbe || null;
  } catch {
    // request interceptor handles it
  } finally {
    saving.value = false;
  }
}

async function handleGenerateWechatCode() {
  generating.value = true;
  try {
    const res = await generateWechatQrcode({
      appId: form.value.wechat.appId,
      appSecret: form.value.wechat.appSecret,
      envVersion: genOptions.value.envVersion,
      page: genOptions.value.page
    });
    ElMessage.success('已成功从微信官方生成小程序码！');
    form.value.wechat.hasQrcode = true;
    if (res?.qrcodeUrl) {
      qrcodePreviewUrl.value = res.qrcodeUrl;
    }
  } catch {
    // request interceptor handles it
  } finally {
    generating.value = false;
  }
}

function triggerUpload() {
  uploadRef.value?.$el.querySelector('input')?.click();
}

async function handleFileSelected(uploadFile) {
  const rawFile = uploadFile.raw;
  if (!rawFile) return;

  if (rawFile.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB');
    return;
  }

  try {
    const uploadRes = await uploadWechatQrcode(rawFile);
    ElMessage.success('小程序码上传成功');
    form.value.wechat.hasQrcode = true;
    if (uploadRes?.qrcodeUrl) {
      qrcodePreviewUrl.value = uploadRes.qrcodeUrl;
    }
  } catch {
    // handled by interceptor
  }
}

onMounted(() => {
  loadSettings();
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.config-card {
  border-radius: 8px;
}

.config-card.full-width {
  grid-column: 1 / -1;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.flex-1 {
  flex: 1;
}

.w-180 {
  width: 180px;
}

.wechat-generate-banner {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 12px;
  border-left: 3px solid var(--el-color-success);
}

.generate-btn-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.generate-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.gen-params-form {
  padding: 4px;
}

.params-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.params-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.params-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.qrcode-badge-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qrcode-btns {
  margin-top: 6px;
}

.qrcode-upload-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.qrcode-preview-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.qrcode-img {
  width: 120px;
  height: 120px;
  border-radius: 4px;
  background: #fff;
  padding: 4px;
  border: 1px solid var(--el-border-color-lighter);
}

.qrcode-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.qrcode-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.qrcode-uploader :deep(.el-upload-dragger) {
  padding: 20px;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-text-color-placeholder);
}

.hub-probe-panel {
  margin-top: 10px;
  padding: 14px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  border: 1px dashed var(--el-border-color);
}

.probe-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.apk-download-showcase {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-top: 6px;
}

.apk-qr-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
  width: 140px;
  flex-shrink: 0;
}

.apk-qr-img {
  width: 124px;
  height: 124px;
  object-fit: contain;
}

.apk-qr-tip {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  white-space: nowrap;
}

.apk-info-box {
  flex: 1;
  min-width: 0;
}

.probe-badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
}

.probe-version {
  font-weight: 600;
  color: var(--el-color-primary);
}

.probe-size,
.probe-date {
  color: var(--el-text-color-secondary);
}

.probe-link {
  margin-top: 8px;
  font-size: 12px;
  word-break: break-all;
}

.link-label {
  color: var(--el-text-color-secondary);
}

.probe-notes {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.notes-header {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
}

.notes-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.notes-list li {
  margin-bottom: 2px;
}

.probe-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-color-warning);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
}

@media (max-width: 960px) {
  .cards-grid {
    grid-template-columns: 1fr;
  }
}
</style>
