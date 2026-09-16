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
        <!-- 微信内打开的提示蒙层 -->
        <div v-if="isWechat" class="wechat-overlay" @click="isWechat = false">
          <div class="wechat-guide">
            <el-icon class="arrow-icon"><TopRight /></el-icon>
            <p>点击右上角 <strong>...</strong></p>
            <p>选择 <strong>在浏览器中打开</strong></p>
            <p class="guide-sub">以允许自动跳转到药房助手 App</p>
          </div>
        </div>
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
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { Connection, TopRight } from '@element-plus/icons-vue';

const route = useRoute();
const isWechat = ref(false);

const serverUrl = computed(() => route.query.server || '');

const deepLink = computed(() => {
  if (!serverUrl.value) return '';
  return `tcmadmin://config?server=${encodeURIComponent(serverUrl.value)}`;
});

function checkWechat() {
  const ua = navigator.userAgent.toLowerCase();
  return ua.includes('micromessenger');
}

function openApp() {
  if (checkWechat()) {
    isWechat.value = true;
    return;
  }
  if (deepLink.value) {
    window.location.href = deepLink.value;
  }
}

onMounted(() => {
  if (checkWechat()) {
    isWechat.value = true;
  } else if (deepLink.value) {
    // 非微信环境，尝试自动拉起
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
.wechat-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.75);
  z-index: 9999;
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
  padding-right: 20px;
}

.wechat-guide {
  color: white;
  text-align: right;
  font-size: 18px;
  line-height: 1.8;
}

.arrow-icon {
  font-size: 48px;
  margin-bottom: 10px;
  animation: bounce 1s infinite alternate;
}

.guide-sub {
  font-size: 14px;
  color: #ccc;
  margin-top: 8px;
}

@keyframes bounce {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(-10px, 10px);
  }
}
</style>
