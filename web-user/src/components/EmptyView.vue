<template>
  <div class="empty-view">
    <el-empty :image="emptyImage" :description="description" :image-size="imageSize">
      <slot />
    </el-empty>
  </div>
</template>

<script setup>
import emptyImage from '@/assets/empty.svg';

defineProps({
  description: {
    type: String,
    default: '暂无数据'
  },
  imageSize: {
    type: Number,
    default: 140
  }
});
</script>

<style scoped>
.empty-view {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 260px;
}
</style>
