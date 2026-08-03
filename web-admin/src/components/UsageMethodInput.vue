<template>
  <div class="usage-method-input">
    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="5"
      resize="vertical"
      maxlength="200"
      show-word-limit
      :placeholder="DEFAULT_USAGE_METHOD"
      :disabled="disabled"
      @update:model-value="$emit('update:modelValue', $event)"
    />
    <div class="usage-presets">
      <el-select
        v-model="frequency"
        filterable
        allow-create
        default-first-option
        placeholder="每日次数"
        :disabled="disabled"
      >
        <el-option label="一天一次" value="一天一次" />
        <el-option label="一天两次" value="一天两次" />
        <el-option label="一天三次" value="一天三次" />
        <el-option label="一天四次" value="一天四次" />
      </el-select>
      <el-select
        v-model="periods"
        multiple
        collapse-tags
        placeholder="服用时段"
        :disabled="disabled"
      >
        <el-option label="早" value="早" />
        <el-option label="中" value="中" />
        <el-option label="晚" value="晚" />
      </el-select>
      <el-select v-model="meal" clearable placeholder="餐前/餐后" :disabled="disabled">
        <el-option label="餐前" value="餐前" />
        <el-option label="餐后" value="餐后" />
      </el-select>
      <el-select v-model="temperature" clearable placeholder="服用温度" :disabled="disabled">
        <el-option label="分服" value="分服" />
        <el-option label="温服" value="温服" />
        <el-option label="热服" value="热服" />
      </el-select>
      <el-button :disabled="disabled" @click="applyPreset">应用组合</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { applyUsagePreset, DEFAULT_USAGE_METHOD } from '@/utils/usageMethod';

const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue']);
const frequency = ref('');
const periods = ref([]);
const meal = ref('');
const temperature = ref('');

watch(
  () => props.modelValue,
  (value) => {
    const text = String(value || '');
    const firstLine = text.split(/\r?\n/)[0].trim();
    frequency.value = /^一天.+次$/.test(firstLine) ? firstLine : '';
    periods.value = ['早', '中', '晚'].filter((item) => text.includes(item));
    meal.value = ['餐前', '餐后'].find((item) => text.includes(item)) || '';
    temperature.value = ['分服', '温服', '热服'].find((item) => text.includes(item)) || '';
  },
  { immediate: true }
);

function applyPreset() {
  const periodText = periods.value.join('');
  const schedule = [periodText, meal.value, temperature.value].filter(Boolean).join('');
  emit(
    'update:modelValue',
    applyUsagePreset(props.modelValue, { frequency: frequency.value, schedule })
  );
}
</script>

<style scoped>
.usage-method-input {
  width: 100%;
}
.usage-presets {
  display: grid;
  grid-template-columns: 1fr 1.3fr 1fr 1fr auto;
  gap: 8px;
  margin-top: 8px;
}
@media (max-width: 720px) {
  .usage-presets {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
