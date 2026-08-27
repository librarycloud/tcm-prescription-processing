<template>
  <div class="page app-releases-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Android 版本发布</h1>
        <p class="page-subtitle">从 GitHub Release 手动同步最新 APK 到业务服务器，供 Android 客户端检查更新。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadVersion">刷新</el-button>
    </div>

    <el-alert
      title="仅全局管理员可执行版本同步"
      type="info"
      :closable="false"
      show-icon
      class="release-alert"
    />

    <el-card v-loading="loading" shadow="never" class="release-card">
      <template #header>
        <div class="card-header">
          <span>当前客户端版本</span>
          <el-tag v-if="version" type="success" effect="plain">已同步</el-tag>
        </div>
      </template>

      <el-descriptions v-if="version" :column="2" border>
        <el-descriptions-item label="版本名称">{{ version.versionName }}</el-descriptions-item>
        <el-descriptions-item label="版本号">{{ version.versionCode }}</el-descriptions-item>
        <el-descriptions-item label="最低版本号">{{ version.minVersionCode }}</el-descriptions-item>
        <el-descriptions-item label="强制更新">
          <el-tag :type="version.forceUpdate ? 'danger' : 'info'">
            {{ version.forceUpdate ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ version.publishedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="APK大小">{{ formatSize(version.size) }}</el-descriptions-item>
        <el-descriptions-item label="下载地址" :span="2">
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

      <div class="release-actions">
        <el-button type="primary" :icon="Download" :loading="syncing" @click="syncVersion">
          从 GitHub 同步最新版本
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="release-guide">
      <template #header><span>发布流程</span></template>
      <ol>
        <li>修改 Android 的 <code>versionCode</code> 和 <code>versionName</code>。</li>
        <li>推送对应的 <code>v*</code> 标签，GitHub Actions 会构建并发布 APK。</li>
        <li>回到本页面点击“从 GitHub 同步最新版本”。</li>
        <li>Android 客户端从后端获取版本信息并下载 APK。</li>
      </ol>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Download, Refresh } from '@element-plus/icons-vue';
import { getAndroidAppVersion, syncAndroidAppVersion } from '@/api/appVersion';

const loading = ref(false);
const syncing = ref(false);
const version = ref(null);

const downloadUrl = computed(() => {
  if (!version.value?.apkUrl) return '-';
  if (/^https?:\/\//i.test(version.value.apkUrl)) return version.value.apkUrl;
  const base = String(import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
  return `${base}${version.value.apkUrl.startsWith('/') ? '' : '/'}${version.value.apkUrl}`;
});

function formatSize(value) {
  const size = Number(value || 0);
  if (!size) return '-';
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

async function loadVersion() {
  loading.value = true;
  try {
    version.value = await getAndroidAppVersion();
  } finally {
    loading.value = false;
  }
}

async function syncVersion() {
  try {
    await ElMessageBox.confirm(
      '将从 GitHub 最新 Release 下载 APK 并替换服务器当前版本，确认继续吗？',
      '同步 Android 版本',
      { type: 'warning', confirmButtonText: '确认同步', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  syncing.value = true;
  try {
    const result = await syncAndroidAppVersion();
    await loadVersion();
    ElMessage.success(`已同步 Android ${result.versionName}（版本号 ${result.versionCode}）`);
  } finally {
    syncing.value = false;
  }
}

onMounted(loadVersion);
</script>

<style scoped>
.release-alert { margin-bottom: 16px; }
.release-card, .release-guide { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.hash-text { word-break: break-all; font-family: monospace; font-size: 12px; }
.release-notes { margin-top: 20px; }
.section-label { margin-bottom: 8px; font-weight: 600; color: var(--app-text); }
.release-notes ul, .release-guide ol { margin: 0; padding-left: 20px; color: var(--app-muted); line-height: 1.9; }
.release-actions { display: flex; justify-content: flex-end; margin-top: 20px; }
code { padding: 2px 5px; border-radius: 4px; background: var(--el-fill-color-light); }
</style>
