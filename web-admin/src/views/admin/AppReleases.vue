<template>
  <div class="page app-releases-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Android 版本与增量更新发布</h1>
        <p class="page-subtitle">管理 Android 客户端全量 APK 与增量（差分）更新补丁，支持一键从 GitHub 同步或解耦迁移至大带宽下载服务器。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <!-- 状态提示横幅 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :span="12">
        <el-alert
          :title="matrix?.bsdiffAvailable ? '差分引擎已就绪：服务器已安装 bsdiff，同步时将自动生成增量补丁' : '差分引擎未激活：服务器未安装 bsdiff（客户端将仅能全量更新）。安装命令：apt-get install -y bsdiff'"
          :type="matrix?.bsdiffAvailable ? 'success' : 'warning'"
          :closable="false"
          show-icon
        />
      </el-col>
      <el-col :span="12">
        <el-alert
          :title="matrix?.appDownloadBaseUrl ? `大带宽加速已启用：下载节点指向 ${matrix.appDownloadBaseUrl}` : '当前下载节点：由主业务服务器托管分发（未来可随时迁移至大带宽独立服务器）'"
          :type="matrix?.appDownloadBaseUrl ? 'success' : 'info'"
          :closable="false"
          show-icon
        />
      </el-col>
    </el-row>

    <!-- 最新版本卡片 -->
    <el-card v-loading="loading" shadow="never" class="release-card mb-4">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="header-title">当前最新客户端版本</span>
            <el-tag v-if="version" type="success" effect="plain" class="ml-2">已发布</el-tag>
          </div>
          <el-button type="primary" :icon="Download" :loading="syncing" @click="syncVersion">
            从 GitHub 同步最新版本
          </el-button>
        </div>
      </template>

      <el-descriptions v-if="version" :column="2" border>
        <el-descriptions-item label="版本名称">{{ version.versionName }}</el-descriptions-item>
        <el-descriptions-item label="版本号 (versionCode)">{{ version.versionCode }}</el-descriptions-item>
        <el-descriptions-item label="最低兼容版本号">{{ version.minVersionCode }}</el-descriptions-item>
        <el-descriptions-item label="强制更新">
          <el-tag :type="version.forceUpdate ? 'danger' : 'info'">
            {{ version.forceUpdate ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ version.publishedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="全量 APK 大小">{{ formatSize(version.size) }}</el-descriptions-item>
        <el-descriptions-item label="全量下载地址" :span="2">
          <el-link :href="downloadUrl" target="_blank" type="primary">{{ downloadUrl }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item label="SHA-256" :span="2">
          <span class="hash-text">{{ version.sha256 || '未配置' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无版本信息" />

      <div v-if="version?.releaseNotes?.length" class="release-notes">
        <div class="section-label">更新说明</div>
        <ul>
          <li v-for="(note, index) in version.releaseNotes" :key="`${index}-${note}`">{{ note }}</li>
        </ul>
      </div>
    </el-card>

    <!-- 增量补丁管理与流量节约看板 -->
    <el-card shadow="never" class="release-card mb-4">
      <template #header>
        <div class="card-header">
          <div>
            <span class="header-title">增量更新补丁矩阵 (Differential Patches)</span>
            <span class="header-subtitle ml-2">客户端根据当前安装版本智能匹配补丁，平均节省 90% 以上下载带宽</span>
          </div>
        </div>
      </template>

      <el-table :data="matrix?.patches || []" stripe border style="width: 100%">
        <el-table-column label="起始旧版本" width="160">
          <template #default="{ row }">
            <el-tag type="info">versionCode {{ row.fromVersionCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标新版本" width="160">
          <template #default="{ row }">
            <el-tag type="success">versionCode {{ row.targetVersionCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="补丁包大小" width="140">
          <template #default="{ row }">
            <strong>{{ formatSize(row.patchSize) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="节约流量比例" width="160">
          <template #default="{ row }">
            <el-tag type="danger" effect="dark" v-if="row.savedPercentage > 0">
              节省 {{ row.savedPercentage }}% ({{ formatSize(row.savedBytes) }})
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="补丁 SHA-256" min-width="200">
          <template #default="{ row }">
            <span class="hash-text">{{ row.patchSha256 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="copyPatchUrl(row.resolvedPatchUrl || row.patchUrl)">
              复制链接
            </el-button>
            <el-button
              link
              type="warning"
              size="small"
              :loading="generatingPatch === `${row.fromVersionCode}-${row.targetVersionCode}`"
              @click="triggerPatch(row.fromVersionCode, row.targetVersionCode)"
            >
              重新生成
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-patches">
            <p>暂无差分补丁。当有 2 个及以上历史版本且服务器安装了 bsdiff 时，同步版本会自动生成补丁。</p>
            <div v-if="historyVersionOptions.length > 0 && matrix?.bsdiffAvailable" class="mt-2">
              <el-button size="small" type="primary" plain @click="manualGenerateDialog = true">
                手动为历史版本生成差分补丁
              </el-button>
            </div>
          </div>
        </template>
      </el-table>
    </el-card>

    <!-- 历史版本记录与未来迁移指引 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="release-guide">
          <template #header><span>发布与增量工作流</span></template>
          <ol>
            <li>在 Android 代码中递增 <code>versionCode</code> 与 <code>versionName</code>。</li>
            <li>推送 <code>v*</code> 标签，GitHub Actions 会构建并在 Release 中上传 APK。</li>
            <li>在当前页面点击“从 GitHub 同步最新版本”。系统自动拉取新版并运行 <code>bsdiff</code> 生成历史差分包。</li>
            <li>Android 客户端启动或进入【关于】页，自动匹配下载几 MB 的补丁并无缝合并安装。</li>
          </ol>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="release-guide">
          <template #header><span>未来迁移至大带宽独立服务器指引 (0代码改动)</span></template>
          <ol>
            <li><strong>文件迁移</strong>：将后端 <code>backend/data/releases</code> 和 <code>patches</code> 目录复制到大带宽服务器的 Web 目录（如 Nginx）。</li>
            <li><strong>配置生效</strong>：在后端 <code>.env</code> 中配置 <code>APP_DOWNLOAD_BASE_URL="https://download.yourdomain.com"</code>。</li>
            <li><strong>完成割接</strong>：所有客户端后续下载 APK 与 Patch 将直接走大带宽服务器，主服务器彻底释放带宽压力！</li>
          </ol>
        </el-card>
      </el-col>
    </el-row>

    <!-- 手动生成差分弹窗 -->
    <el-dialog v-model="manualGenerateDialog" title="手动生成增量补丁" width="480px">
      <el-form :model="patchForm" label-width="120px">
        <el-form-item label="起始旧版本">
          <el-select v-model="patchForm.fromVersionCode" placeholder="请选择旧版本">
            <el-option
              v-for="item in historyVersionOptions"
              :key="item.versionCode"
              :label="`v${item.versionName} (versionCode: ${item.versionCode})`"
              :value="item.versionCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标新版本">
          <el-tag type="success">v{{ version?.versionName }} (versionCode: {{ version?.versionCode }})</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualGenerateDialog = false">取消</el-button>
        <el-button type="primary" :loading="generatingManual" @click="handleManualGenerate">立即生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Download, Refresh } from '@element-plus/icons-vue';
import { generateAppPatch, getAndroidAppVersion, getAppPatchMatrix, syncAndroidAppVersion } from '@/api/appVersion';

const loading = ref(false);
const syncing = ref(false);
const version = ref(null);
const matrix = ref(null);
const generatingPatch = ref('');
const manualGenerateDialog = ref(false);
const generatingManual = ref(false);

const patchForm = reactive({
  fromVersionCode: null
});

const downloadUrl = computed(() => {
  if (!version.value?.apkUrl) return '-';
  if (/^https?:\/\//i.test(version.value.apkUrl)) return version.value.apkUrl;
  const base = String(import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
  return `${base}${version.value.apkUrl.startsWith('/') ? '' : '/'}${version.value.apkUrl}`;
});

const historyVersionOptions = computed(() => {
  if (!matrix.value?.history || !version.value) return [];
  return matrix.value.history.filter(h => Number(h.versionCode) < Number(version.value.versionCode));
});

function formatSize(value) {
  const size = Number(value || 0);
  if (!size) return '-';
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

async function loadData() {
  loading.value = true;
  try {
    const [vData, mData] = await Promise.all([
      getAndroidAppVersion().catch(() => null),
      getAppPatchMatrix().catch(() => null)
    ]);
    version.value = vData;
    matrix.value = mData;
  } finally {
    loading.value = false;
  }
}

async function syncVersion() {
  try {
    await ElMessageBox.confirm(
      '将从 GitHub 最新 Release 下载 APK，并在服务器自动为历史版本生成差分补丁，确认继续吗？',
      '同步 Android 版本',
      { type: 'warning', confirmButtonText: '确认同步', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  syncing.value = true;
  try {
    const result = await syncAndroidAppVersion();
    await loadData();
    const patchMsg = result.patchesGenerated?.length ? `，已自动生成 ${result.patchesGenerated.length} 个增量补丁` : '';
    ElMessage.success(`已同步 Android ${result.versionName}（版本号 ${result.versionCode}）${patchMsg}`);
  } finally {
    syncing.value = false;
  }
}

async function triggerPatch(fromCode, targetCode) {
  const key = `${fromCode}-${targetCode}`;
  generatingPatch.value = key;
  try {
    await generateAppPatch(fromCode, targetCode);
    ElMessage.success(`差分补丁已生成 (v${fromCode} -> v${targetCode})`);
    await loadData();
  } finally {
    generatingPatch.value = '';
  }
}

async function handleManualGenerate() {
  if (!patchForm.fromVersionCode) {
    ElMessage.warning('请选择起始旧版本');
    return;
  }
  generatingManual.value = true;
  try {
    await generateAppPatch(patchForm.fromVersionCode, version.value.versionCode);
    ElMessage.success('差分补丁生成成功');
    manualGenerateDialog.value = false;
    await loadData();
  } finally {
    generatingManual.value = false;
  }
}

function copyPatchUrl(url) {
  const fullUrl = /^https?:\/\//i.test(url)
    ? url
    : `${window.location.origin}${url.startsWith('/') ? '' : '/'}${url}`;
  navigator.clipboard.writeText(fullUrl).then(() => {
    ElMessage.success('补丁下载链接已复制到剪贴板');
  });
}

onMounted(loadData);
</script>

<style scoped>
.mb-4 { margin-bottom: 16px; }
.ml-2 { margin-left: 8px; }
.mt-2 { margin-top: 8px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.header-title { font-weight: 600; font-size: 15px; }
.header-subtitle { color: var(--app-muted); font-size: 13px; }
.hash-text { word-break: break-all; font-family: monospace; font-size: 12px; }
.release-notes { margin-top: 20px; }
.section-label { margin-bottom: 8px; font-weight: 600; color: var(--app-text); }
.release-notes ul, .release-guide ol { margin: 0; padding-left: 20px; color: var(--app-muted); line-height: 1.9; }
code { padding: 2px 5px; border-radius: 4px; background: var(--el-fill-color-light); }
.empty-patches { padding: 16px 0; color: var(--app-muted); text-align: center; }
</style>
