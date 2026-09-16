<template>
  <div class="app-config-page">
    <el-card class="config-card" shadow="hover">
      <template #header>
        <div class="header">
          <el-icon class="icon"><Connection /></el-icon>
          <h2>导入应用配置</h2>
        </div>
      </template>

      <div class="content">
        <p class="desc">您正在尝试为药房助手 App 导入专属服务器配置：</p>
        <div class="server-url">
          {{ serverUrl }}
        </div>
        <p class="hint">请点击下方按钮，允许在药房助手 App 中打开以完成导入。</p>
        <div class="actions">
          <el-button type="primary" size="large" class="action-btn" @click="openApp">
            点击打开 App
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { Connection } from '@element-plus/icons-vue';

const route = useRoute();

const serverUrl = computed(() => route.query.server || '');

const deepLink = computed(() => {
  if (!serverUrl.value) return '';
  return `tcmadmin://config?server=${encodeURIComponent(serverUrl.value)}`;
});

function openApp() {
  if (deepLink.value) {
    window.location.href = deepLink.value;
  }
}

onMounted(() => {
  if (deepLink.value) {
    // 尝试自动拉起
    setTimeout(() => {
      window.location.href = deepLink.value;
    }, 500);
  }
});
</script>

<style scoped>
.app-config-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f3f4f6;
  padding: 20px;
}

.config-card {
  width: 100%;
  max-width: 400px;
  border-radius: 12px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--el-color-primary);
}

.header h2 {
  margin: 0;
  font-size: 18px;
}

.icon {
  font-size: 24px;
}

.content {
  text-align: center;
}

.desc {
  font-size: 15px;
  color: var(--el-text-color-regular);
  margin-bottom: 16px;
}

.server-url {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 14px;
  color: var(--el-text-color-primary);
  margin-bottom: 20px;
  word-break: break-all;
}

.hint {
  font-size: 13px;
  color: var(--el-color-warning);
  margin-bottom: 24px;
}

.actions {
  display: flex;
  justify-content: center;
}

.action-btn {
  width: 100%;
  border-radius: 20px;
}
</style>
