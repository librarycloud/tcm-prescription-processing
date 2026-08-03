<template>
  <div class="not-found">
    <el-result title="404" sub-title="页面不存在或已被移动">
      <template #icon>
        <img src="@/assets/empty.svg" alt="404" class="not-found-image" />
      </template>
      <template #extra>
        <el-button type="primary" @click="router.replace(userStore.homePath)">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--app-bg);
}

.not-found-image {
  width: 180px;
  height: auto;
}
</style>
