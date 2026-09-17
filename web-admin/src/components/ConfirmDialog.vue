<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="420px"
    align-center
    @close="emit('update:modelValue', false)"
  >
    <div class="dialog-content">{{ content }}</div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :type="confirmType" :loading="loading" @click="emit('confirm')">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '确认操作'
  },
  content: {
    type: String,
    default: '确认继续当前操作吗？'
  },
  confirmType: {
    type: String,
    default: 'primary'
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:modelValue', 'confirm']);
</script>

<style scoped>
.dialog-content {
  color: var(--app-text);
  line-height: 1.7;
}
</style>
