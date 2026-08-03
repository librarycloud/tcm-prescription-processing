<template>
  <el-card class="qr-card" shadow="never">
    <template #header>
      <div class="qr-header">
        <span>{{ title }}</span>
        <el-tag v-if="content" type="primary" effect="plain">{{ content }}</el-tag>
      </div>
    </template>

    <div class="qr-body">
      <img v-if="dataUrl" :src="dataUrl" :alt="content" class="qr-image" />
      <el-empty v-else description="暂无二维码" :image-size="90" />
    </div>
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue';
import { createQRCodeDataUrl } from '@/utils/qrcode';

const props = defineProps({
  title: {
    type: String,
    default: '取货二维码'
  },
  content: {
    type: String,
    default: ''
  }
});

const dataUrl = ref('');

watch(
  () => props.content,
  async (value) => {
    dataUrl.value = await createQRCodeDataUrl(value);
  },
  { immediate: true }
);
</script>

<style scoped>
.qr-card {
  width: 100%;
}

.qr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.qr-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 250px;
}

.qr-image {
  width: 220px;
  height: 220px;
}
</style>
