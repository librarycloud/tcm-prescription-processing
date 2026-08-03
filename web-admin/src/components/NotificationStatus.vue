<template>
  <el-button
    link
    :type="buttonType"
    :icon="Bell"
    :loading="Number(status) === 3"
    :disabled="disabled"
    @click="$emit('click')"
  >
    {{ text }}
  </el-button>
</template>

<script setup>
import { computed } from 'vue';
import { Bell } from '@element-plus/icons-vue';

const props = defineProps({
  status: { type: [Number, String], default: 0 },
  count: { type: [Number, String], default: 0 },
  disabled: Boolean
});

defineEmits(['click']);

const buttonType = computed(() => {
  const types = { 0: 'info', 1: 'success', 2: 'danger', 3: 'primary' };
  return types[Number(props.status)] || 'info';
});

const text = computed(() => {
  const count = Number(props.count) || 0;
  if (Number(props.status) === 3) return '发送中';
  if (Number(props.status) === 2) return count ? `失败 · 已发${count}次` : '发送失败';
  if (count > 0) return `已通知${count}次`;
  return '未通知';
});
</script>
