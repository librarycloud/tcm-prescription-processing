<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">客户端展示设置</h1>
        <p class="page-subtitle">
          管理右上角弹窗展示的微信小程序码、Android App 下载源与版本更新说明
        </p>
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
              <el-input
                v-model.trim="form.wechat.appId"
                placeholder="留空则使用系统环境变量 WX_APPID"
              />
            </el-form-item>
            <el-form-item label="小程序 AppSecret" class="flex-1">
              <el-input
                v-model="form.wechat.appSecret"
                type="password"
                show-password
                :placeholder="
                  form.wechat.hasSecret ? '已配置密钥，留空不修改' : '留空则使用环境变量 WX_SECRET'
                "
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
                      <el-select v-model="genOptions.envVersion" size="small" style="width: 100%">
                        <el-option label="正式版 (release)" value="release" />
                        <el-option label="体验版 (trial)" value="trial" />
                        <el-option label="开发版 (develop)" value="develop" />
                      </el-select>
                    </div>
                    <div class="params-item" style="margin-top: 8px">
                      <span class="params-label">落地页路径（选填）</span>
                      <el-input
                        v-model.trim="genOptions.page"
                        size="small"
                        placeholder="默认为主页"
                      />
                    </div>
                  </div>
                </el-popover>
              </div>
              <span class="generate-tip"
                >基于微信官方接口一键生成官方圆形菊花码；亦可手动上传自定义图片。</span
              >
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
                    <el-button size="small" type="primary" plain @click="triggerUpload"
                      >手动替换图片</el-button
                    >
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
                <div class="el-upload__text">手动上传：拖到此处，或<em>点击上传</em></div>
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
            <el-input
              v-model.trim="form.android.displayName"
              placeholder="例如：药房助手 Android 版"
            />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="App Release Hub 地址" class="flex-1">
              <el-input
                v-model.trim="form.android.releaseHubUrl"
                placeholder="如留空则使用系统环境变量配置"
              />
            </el-form-item>
            <el-form-item label="Hub 应用 ID (App ID)" class="w-180">
              <el-input v-model.trim="form.android.releaseHubAppId" placeholder="默认：tcm-admin" />
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
                <img
                  v-if="apkQrDataUrl"
                  :src="apkQrDataUrl"
                  class="apk-qr-img"
                  alt="APK下载二维码"
                />
                <div v-else class="qr-loading">生成中...</div>
                <span class="apk-qr-tip">手机扫码直接下载</span>
              </div>
              <div class="apk-info-box">
                <div class="probe-badge-row">
                  <span class="probe-version"
                    >版本：v{{ hubProbe?.versionName || '-' }} ({{
                      hubProbe?.versionCode || '-'
                    }})</span
                  >
                  <span v-if="hubProbe?.size" class="probe-size"
                    >大小：{{ formatSize(hubProbe.size) }}</span
                  >
                  <span v-if="hubProbe?.publishedAt" class="probe-date"
                    >发布：{{ hubProbe.publishedAt }}</span
                  >
                </div>
                <div class="probe-link">
                  <span class="link-label">有效下载链接：</span>
                  <el-link
                    :href="effectiveDownloadUrl"
                    target="_blank"
                    type="primary"
                    :underline="false"
                  >
                    {{ effectiveDownloadUrl }}
                  </el-link>
                </div>
                <div style="margin-top: 8px">
                  <el-button
                    size="small"
                    type="primary"
                    :icon="Download"
                    @click="downloadApk(effectiveDownloadUrl)"
                  >
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
            <span>{{
              hubProbe?.message || '暂无可用下载链接，请确认 Hub 地址或填写自定义下载地址'
            }}</span>
          </div>
        </div>
      </el-card>

      <!-- 卡片: iOS App TestFlight -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-title-row">
            <div class="card-title">
              <el-icon><Cellphone /></el-icon>
              <span>iOS App (TestFlight)</span>
            </div>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="客户端显示名称">
            <el-input v-model.trim="form.ios.displayName" placeholder="例如：药房助手 iOS 版" />
          </el-form-item>
          <el-form-item label="TestFlight 公开链接">
            <el-input
              v-model.trim="form.ios.testflightUrl"
              placeholder="例如：https://testflight.apple.com/join/xxxx"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 卡片 3: App 后端地址与一键导入配置 (Deep Link & 二维码) -->
      <el-card shadow="never" class="config-card full-width">
        <template #header>
          <div class="card-title-row">
            <div class="card-title">
              <el-icon><Connection /></el-icon>
              <span>App 后端地址与一键导入配置 (Deep Link & 二维码)</span>
            </div>
            <el-tag :type="form.serverUrl ? 'success' : 'info'" size="small">
              {{ form.serverUrl ? '已设置自定义后端' : '默认使用当前域名' }}
            </el-tag>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="后端服务器 API 根地址 (Server Base URL)">
            <div class="server-input-row">
              <el-input
                v-model.trim="form.serverUrl"
                placeholder="例如：https://api.tcm.yourdomain.com 或 http://192.168.1.100:3000 (留空则默认使用当前后台访问域名)"
                clearable
              >
                <template #prefix>
                  <el-icon><Link /></el-icon>
                </template>
              </el-input>
              <el-button type="primary" plain :icon="Aim" @click="fillCurrentOrigin">
                填入当前网页地址
              </el-button>
            </div>
            <div class="server-hint-text">
              <span>当前生效基准地址：</span>
              <el-tag size="small" type="primary" effect="plain">{{
                effectiveServerUrl || '未检测到'
              }}</el-tag>
              <span class="server-subhint"
                >修改后，下方 Deep Link 专属链接与配置二维码将即时联动更新。</span
              >
            </div>
          </el-form-item>
        </el-form>

        <div class="deeplink-section-grid">
          <!-- 左侧：2. 专属链接一键导入（Deep Link） -->
          <div class="deeplink-col">
            <div class="section-subtitle-row">
              <h4 class="section-subtitle">2. 专属链接一键导入（Deep Link）</h4>
            </div>
            <p class="section-desc">
              可将配置链接通过微信、钉钉、企微、短信或备忘录直接发给员工，点击链接即可<strong
                >自动唤起 App、完成配置并持久化保存</strong
              >：
            </p>

            <div class="links-table-wrapper">
              <table class="deeplink-table">
                <thead>
                  <tr>
                    <th style="width: 140px">链接格式</th>
                    <th>实时生成链接</th>
                    <th style="width: 130px">适用场景</th>
                    <th style="width: 80px; text-align: center">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><strong>标准格式（推荐）</strong></td>
                    <td>
                      <code class="code-badge">{{ standardDeepLink || '请先配置后端地址' }}</code>
                    </td>
                    <td>生产线上域名</td>
                    <td style="text-align: center">
                      <el-button
                        size="small"
                        type="primary"
                        link
                        :icon="CopyDocument"
                        @click="copyToClipboard(standardDeepLink, '标准格式链接')"
                      >
                        复制
                      </el-button>
                    </td>
                  </tr>
                  <tr>
                    <td><strong>局域网真机</strong></td>
                    <td>
                      <code class="code-badge">{{ lanExampleDeepLink }}</code>
                    </td>
                    <td>药店内部 Wi-Fi 联调</td>
                    <td style="text-align: center">
                      <el-button
                        size="small"
                        type="primary"
                        link
                        :icon="CopyDocument"
                        @click="copyToClipboard(lanExampleDeepLink, '局域网链接')"
                      >
                        复制
                      </el-button>
                    </td>
                  </tr>
                  <tr>
                    <td><strong>超简短格式</strong></td>
                    <td>
                      <code class="code-badge">{{ shortDeepLink || '请先配置后端地址' }}</code>
                    </td>
                    <td>便于短信下发</td>
                    <td style="text-align: center">
                      <el-button
                        size="small"
                        type="primary"
                        link
                        :icon="CopyDocument"
                        @click="copyToClipboard(shortDeepLink, '超简短链接')"
                      >
                        复制
                      </el-button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="deeplink-tip-box">
              💡 参数名兼容 <code>server</code>、<code>url</code>、<code>baseURL</code> 与
              <code>api</code>（不区分大小写）；亦支持简写格式如
              <code>tcmadmin://192.168.1.100:3000</code>。
            </div>
          </div>

          <!-- 右侧：3. 二维码扫码导入（系统相机直接支持） -->
          <div class="deeplink-col qr-col">
            <div class="section-subtitle-row">
              <h4 class="section-subtitle">3. 二维码扫码导入（系统相机直接支持）</h4>
            </div>
            <p class="section-desc">将上述导入链接生成为标准二维码（支持系统相机直接唤起）：</p>

            <div class="server-qr-card">
              <div class="qr-scheme-selector">
                <el-radio-group v-model="selectedQrScheme" size="small">
                  <el-radio-button value="standard">标准格式 (tcmadmin://)</el-radio-button>
                  <el-radio-button value="short">短链格式 (tcm://)</el-radio-button>
                </el-radio-group>
              </div>

              <div class="server-qr-box">
                <img
                  v-if="serverQrDataUrl"
                  :src="serverQrDataUrl"
                  class="server-qr-img"
                  alt="服务器配置二维码"
                />
                <div v-else class="qr-loading">生成二维码中...</div>
                <span class="qr-subtext">iPhone / Android 系统相机扫码</span>
              </div>

              <div class="qr-action-btns">
                <el-button size="small" type="primary" :icon="Download" @click="downloadServerQr">
                  下载配置二维码
                </el-button>
                <el-button
                  size="small"
                  :icon="CopyDocument"
                  @click="copyToClipboard(currentQrDeepLink, '配置链接')"
                >
                  复制链接
                </el-button>
              </div>

              <div class="camera-steps-box">
                <div class="step-line">
                  <span class="step-num">1</span> 员工打开 iPhone 或 Android
                  <strong>系统相机</strong> 对准该二维码。
                </div>
                <div class="step-line">
                  <span class="step-num">2</span> 画面中会自动出现
                  <strong>“在「药房助手」中打开”</strong> 的黄色提示胶囊。
                </div>
                <div class="step-line">
                  <span class="step-num">3</span> 点击即可启动 App
                  并自动导入切换服务器，无需任何手工输入！
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 卡片 4: 公告与温馨提示 -->
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
import QRCode from 'qrcode';
import {
  Aim,
  Bell,
  Cellphone,
  ChatDotRound,
  Connection,
  CopyDocument,
  Download,
  Link,
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
  ios: {
    displayName: '药房助手 iOS 版',
    testflightUrl: ''
  },
  serverUrl: '',
  announcement: ''
});

const hubProbe = ref(null);
const qrcodePreviewUrl = ref('');
const apkQrDataUrl = ref('');

// App 服务器配置 Deep Link 与二维码相关状态
const selectedQrScheme = ref('standard');
const serverQrDataUrl = ref('');

const effectiveServerUrl = computed(() => {
  return (
    form.value.serverUrl?.trim() || (typeof window !== 'undefined' ? window.location.origin : '')
  );
});

const standardDeepLink = computed(() => {
  const s = effectiveServerUrl.value;
  return s ? `tcmadmin://config?server=${s}` : '';
});

const shortDeepLink = computed(() => {
  const s = effectiveServerUrl.value;
  return s ? `tcm://config?server=${s}` : '';
});

const lanExampleDeepLink = computed(() => {
  const s = effectiveServerUrl.value;
  return s ? `tcmadmin://config?server=${s}` : 'tcmadmin://config?server=http://192.168.1.100:3000';
});

const currentQrDeepLink = computed(() => {
  return selectedQrScheme.value === 'short' ? shortDeepLink.value : standardDeepLink.value;
});

watch(
  currentQrDeepLink,
  async (newLink) => {
    if (newLink) {
      try {
        serverQrDataUrl.value = await QRCode.toDataURL(newLink, {
          width: 170,
          margin: 1,
          color: { dark: '#000000', light: '#ffffff' }
        });
      } catch {
        serverQrDataUrl.value = '';
      }
    } else {
      serverQrDataUrl.value = '';
    }
  },
  { immediate: true }
);

function fillCurrentOrigin() {
  if (typeof window !== 'undefined' && window.location.origin) {
    form.value.serverUrl = window.location.origin;
    ElMessage.info(`已填入当前访问地址: ${window.location.origin}`);
  }
}

async function copyToClipboard(text, label = '链接') {
  if (!text) return;
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    }
    ElMessage.success(`${label}已复制到剪贴板`);
  } catch {
    ElMessage.error('复制失败，请手动选择并复制');
  }
}

function downloadServerQr() {
  if (!serverQrDataUrl.value) return;
  const link = document.createElement('a');
  link.href = serverQrDataUrl.value;
  link.download = 'tcm-app-server-config.png';
  link.click();
}

const effectiveDownloadUrl = computed(() => {
  return form.value.android.customDownloadUrl || hubProbe.value?.downloadUrl || '';
});

watch(
  effectiveDownloadUrl,
  async (newUrl) => {
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
  },
  { immediate: true }
);

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
        ios: { ...form.value.ios, ...(res.config.ios || {}) },
        serverUrl: res.config.serverUrl || '',
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
        ios: { ...form.value.ios, ...(res.config.ios || {}) },
        serverUrl: res.config.serverUrl || '',
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

.server-input-row {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.server-hint-text {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.server-subhint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.deeplink-section-grid {
  display: grid;
  grid-template-columns: 1.18fr 0.82fr;
  gap: 20px;
  margin-top: 12px;
}

.deeplink-col {
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
}

.section-subtitle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.section-subtitle {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-desc {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 0 0 12px;
  line-height: 1.5;
}

.links-table-wrapper {
  overflow-x: auto;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.deeplink-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.deeplink-table th,
.deeplink-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.deeplink-table th {
  background: var(--el-fill-color);
  font-weight: 600;
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.deeplink-table tr:last-child td {
  border-bottom: none;
}

.code-badge {
  display: inline-block;
  max-width: 250px;
  padding: 3px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
}

.deeplink-tip-box {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 6px;
  font-size: 12px;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.server-qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 16px;
}

.qr-scheme-selector {
  margin-bottom: 12px;
}

.server-qr-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 170px;
  height: 170px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
  background: #fff;
  margin-bottom: 10px;
}

.server-qr-img {
  width: 136px;
  height: 136px;
  object-fit: contain;
}

.qr-subtext {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.qr-action-btns {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.camera-steps-box {
  width: 100%;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  line-height: 1.4;
}

.step-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 10px;
  font-weight: bold;
  flex-shrink: 0;
  margin-top: 1px;
}

@media (max-width: 960px) {
  .cards-grid {
    grid-template-columns: 1fr;
  }
  .deeplink-section-grid {
    grid-template-columns: 1fr;
  }
}
</style>
