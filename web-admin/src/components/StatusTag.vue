<template>
  <el-tag :type="statusTagType(status)" effect="light" round>
    {{ statusText(status) }}
  </el-tag>
</template>

<script setup>
import { statusTagType, statusText } from '@/utils/status';

defineProps({
  status: {
    type: [Number, String],
    default: 0
  }
});
</script>
