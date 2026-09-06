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

    <!-- 增量补丁管理与版本分类看板 -->
    <el-card shadow="never" class="release-card mb-4">
      <template #header>
        <div class="card-header">
          <div>
            <span class="header-title">增量更新补丁矩阵 (按发布版本分类)</span>
            <span class="header-subtitle ml-2">展示各版本的增量分包详情。支持客户端首次请求时按需现场生成差分包，亦支持一键预热补齐。</span>
          </div>
        </div>
      </template>

      <div v-if="matrix?.versionGroups?.length" class="version-groups-container">
        <el-collapse v-model="activeCollapseNames">
          <el-collapse-item
            v-for="group in matrix.versionGroups"
            :key="group.versionCode"
            :name="String(group.versionCode)"
            class="version-collapse-item"
          >
            <template #title>
              <div class="collapse-title-row">
                <div class="title-left">
                  <el-tag :type="group.isLatest ? 'success' : 'info'" effect="dark" size="small">
                    {{ group.isLatest ? '当前最新版本' : '历史版本' }}
                  </el-tag>
                  <span class="version-name ml-2">v{{ group.versionName }}</span>
                  <span class="version-code ml-1">(versionCode: {{ group.versionCode }})</span>
                  <el-tag size="small" class="ml-2" type="info">全量包: {{ formatSize(group.size) }}</el-tag>

                  <el-tag
                    v-if="group.eligibleCount === 0"
                    size="small"
                    class="ml-2"
                    type="info"
                    effect="plain"
                  >
                    基线初始版本（无需差分包）
                  </el-tag>
                  <el-tag
                    v-else-if="group.coveredCount >= group.eligibleCount"
                    size="small"
                    class="ml-2"
                    type="success"
                    effect="plain"
                  >
                    已生成全部 {{ group.coveredCount }} 个历史差分包 (100% 覆盖)
                  </el-tag>
                  <el-tag
                    v-else
                    size="small"
                    class="ml-2"
                    type="warning"
                    effect="plain"
                  >
                    已生成 {{ group.coveredCount }}/{{ group.eligibleCount }} 个差分包 (缺 {{ group.missingCount }} 个)
                  </el-tag>
                </div>

                <div class="title-right" @click.stop>
                  <el-button
                    v-if="group.missingCount > 0 && matrix?.bsdiffAvailable"
                    type="primary"
                    size="small"
                    plain
                    :loading="generatingAll === group.versionCode"
                    @click.stop="handleGenerateAll(group.versionCode)"
                  >
                    一键补齐所有历史差分 (缺 {{ group.missingCount }} 个)
                  </el-button>
                  <el-button
                    v-if="group.eligibleCount > 0 && matrix?.bsdiffAvailable"
                    size="small"
                    type="default"
                    @click.stop="openManualDialog(group.versionCode)"
                  >
                    手动补丁
                  </el-button>
                </div>
              </div>
            </template>

            <!-- 目标版本的差分包列表 -->
            <div class="version-patches-content">
              <el-table
                v-if="group.patches?.length"
                :data="group.patches"
                stripe
                border
                size="small"
                style="width: 100%"
              >
                <el-table-column label="起始旧版本" min-width="160" align="center">
                  <template #default="{ row }">
                    <el-tag type="info">
                      v{{ row.fromVersionName }} (versionCode: {{ row.fromVersionCode }})
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="目标新版本" min-width="160" align="center">
                  <template #default>
                    <el-tag type="success">
                      v{{ group.versionName }} (versionCode: {{ group.versionCode }})
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="补丁包大小" min-width="120" align="center">
                  <template #default="{ row }">
                    <strong>{{ formatSize(row.patchSize) }}</strong>
                  </template>
                </el-table-column>
                <el-table-column label="节约流量比例" min-width="170" align="center">
                  <template #default="{ row }">
                    <el-tag type="danger" effect="dark" v-if="row.savedPercentage > 0">
                      节省 {{ row.savedPercentage }}% ({{ formatSize(row.savedBytes) }})
                    </el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="补丁 SHA-256" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="hash-text">{{ row.patchSha256 }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" min-width="150" align="center">
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
              </el-table>

              <div v-else class="empty-group-patches">
                <el-empty
                  :description="group.eligibleCount === 0 ? '此版本为基线初始版本，无需增量差分补丁' : '暂无指向此版本的预生成差分包。客户端发起更新请求时将按需现场生成并持久化，或可点击上方按钮一键预热生成。'"
                  :image-size="70"
                />
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <el-empty v-else description="暂无版本与补丁数据" />
    </el-card>

    <!-- 历史版本记录与未来迁移指引 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="release-guide">
          <template #header><span>发布与增量工作流</span></template>
          <ol>
            <li>在 Android 代码中递增 <code>versionCode</code> 与 <code>versionName</code>。</li>
            <li>推送 <code>v*</code> 标签，GitHub Actions 构建并在 Release 中上传 APK。</li>
            <li>在当前页面点击“从 GitHub 同步最新版本”，系统自动拉取新版并预生成近版差分包。</li>
            <li><strong>跨多版本智能增量</strong>：若老用户跨了多个版本，客户端首次请求时服务端将<strong>现场动态生成直达补丁并持久化缓存</strong>，后续相同版本 0ms 秒回！</li>
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
        <el-form-item label="目标新版本">
          <el-tag type="success">{{ targetVersionLabel }}</el-tag>
        </el-form-item>
        <el-form-item label="起始旧版本">
          <el-select v-model="patchForm.fromVersionCode" placeholder="请选择旧版本" style="width: 100%">
            <el-option
              v-for="item in historyVersionOptions"
              :key="item.versionCode"
              :label="`v${item.versionName} (versionCode: ${item.versionCode})`"
              :value="item.versionCode"
            />
          </el-select>
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
import {
  generateAllAppPatches,
  generateAppPatch,
  getAndroidAppVersion,
  getAppPatchMatrix,
  syncAndroidAppVersion,
} from '@/api/appVersion';

const loading = ref(false);
const syncing = ref(false);
const version = ref(null);
const matrix = ref(null);
const activeCollapseNames = ref([]);
const generatingPatch = ref('');
const generatingAll = ref(null);
const manualGenerateDialog = ref(false);
const generatingManual = ref(false);

const patchForm = reactive({
  fromVersionCode: null,
  targetVersionCode: null,
});

const downloadUrl = computed(() => {
  if (!version.value?.apkUrl) return '-';
  if (/^https?:\/\//i.test(version.value.apkUrl)) return version.value.apkUrl;
  const base = String(import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
  return `${base}${version.value.apkUrl.startsWith('/') ? '' : '/'}${version.value.apkUrl}`;
});

const historyVersionOptions = computed(() => {
  if (!matrix.value?.history || !patchForm.targetVersionCode) return [];
  return matrix.value.history.filter(h => Number(h.versionCode) < Number(patchForm.targetVersionCode));
});

const targetVersionLabel = computed(() => {
  if (!matrix.value?.history || !patchForm.targetVersionCode) return '-';
  const item = matrix.value.history.find(h => Number(h.versionCode) === Number(patchForm.targetVersionCode));
  return item ? `v${item.versionName} (versionCode: ${item.versionCode})` : `versionCode: ${patchForm.targetVersionCode}`;
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
      getAppPatchMatrix().catch(() => null),
    ]);
    version.value = vData;
    matrix.value = mData;

    if (activeCollapseNames.value.length === 0 && mData?.versionGroups?.length > 0) {
      activeCollapseNames.value = [String(mData.versionGroups[0].versionCode)];
    }
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

function openManualDialog(targetVersionCode) {
  patchForm.targetVersionCode = targetVersionCode || version.value?.versionCode;
  patchForm.fromVersionCode = null;
  manualGenerateDialog.value = true;
}

async function handleGenerateAll(targetVersionCode) {
  generatingAll.value = targetVersionCode;
  try {
    const res = await generateAllAppPatches(targetVersionCode);
    ElMessage.success(`已为版本 v${targetVersionCode} 补齐 ${res.generatedCount} 个历史差分补丁`);
    await loadData();
  } finally {
    generatingAll.value = null;
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
    await generateAppPatch(patchForm.fromVersionCode, patchForm.targetVersionCode);
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
.ml-1 { margin-left: 4px; }
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

.collapse-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 16px;
}
.title-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.title-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.version-name {
  font-weight: 600;
  font-size: 14px;
}
.version-code {
  color: var(--app-muted);
  font-size: 13px;
}
.version-patches-content {
  padding: 8px 0;
}
.empty-group-patches {
  padding: 8px 0;
}

:deep(.el-collapse-item__header) {
  height: auto;
  min-height: 48px;
  line-height: normal;
  padding: 8px 0;
}

:deep(.el-table th.gutter),
:deep(.el-table col.gutter) {
  display: table-cell !important;
  width: 0 !important;
}
:deep(.el-table__header),
:deep(.el-table__body) {
  width: 100% !important;
}
:deep(.el-table td.el-table__cell),
:deep(.el-table th.el-table__cell) {
  vertical-align: middle;
}
</style>
