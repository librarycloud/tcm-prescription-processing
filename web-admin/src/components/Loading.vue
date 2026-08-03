<template>
  <div class="loading">
    <el-icon class="loading-icon"><LoadingIcon /></el-icon>
    <span>{{ text }}</span>
  </div>
</template>

<script setup>
import { Loading as LoadingIcon } from '@element-plus/icons-vue';

defineProps({
  text: {
    type: String,
    default: '加载中'
  }
});
</script>

<style scoped>
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 240px;
  color: var(--app-muted);
}

.loading-icon {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
