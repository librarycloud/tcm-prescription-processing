<template>
  <el-card class="search-card" shadow="never">
    <el-form
      class="search-form"
      :class="{ 'with-store': showStore }"
      :model="model"
      label-width="72px"
      @submit.prevent
    >
      <el-form-item class="keyword-filter" label="关键词">
        <el-input
          :model-value="model.keyword"
          clearable
          placeholder="取货码 / 手机号 / 姓名 / 物品名称"
          @update:model-value="updateField('keyword', $event)"
          @keyup.enter="emit('search')"
        />
      </el-form-item>
      <el-form-item v-if="showStore" label="门店">
        <el-select
          :model-value="model.storeId"
          clearable
          filterable
          placeholder="全部门店"
          @update:model-value="updateField('storeId', $event)"
        >
          <el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          :model-value="model.status"
          :disabled="model.dateScope === 'overdue'"
          clearable
          placeholder="全部"
          @update:model-value="updateField('status', $event)"
        >
          <el-option label="待取" :value="0" />
          <el-option label="已取" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-select
          :model-value="model.dateScope"
          placeholder="全部时间"
          @update:model-value="updateField('dateScope', $event)"
        >
          <el-option label="全部时间" value="" />
          <el-option label="仅今天" value="today" />
          <el-option label="逾期未取" value="overdue" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
        <el-select :model-value="model.sortBy" @update:model-value="updateField('sortBy', $event)">
          <el-option label="录入时间" value="createdAt" />
          <el-option label="取货时间" value="pickedAt" />
        </el-select>
      </el-form-item>
      <el-form-item label="顺序">
        <el-select
          :model-value="model.sortOrder"
          @update:model-value="updateField('sortOrder', $event)"
        >
          <el-option label="倒序" value="desc" />
          <el-option label="正序" value="asc" />
        </el-select>
      </el-form-item>
      <div class="search-actions">
        <el-button type="primary" :icon="Search" @click="emit('search')">查询</el-button>
        <el-button :icon="Refresh" @click="emit('reset')">重置</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup>
import { Refresh, Search } from '@element-plus/icons-vue';

const props = defineProps({
  model: {
    type: Object,
    required: true
  },
  showStore: Boolean,
  stores: { type: Array, default: () => [] }
});

const emit = defineEmits(['update:model', 'search', 'reset']);

function updateField(field, value) {
  emit('update:model', {
    ...props.model,
    [field]: value,
    ...(field === 'dateScope' && value === 'overdue' ? { status: '' } : {})
  });
}
</script>

<style scoped>
.search-card {
  border: 1px solid var(--app-border);
}

.search-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr));
  gap: 12px;
  align-items: flex-start;
}

.keyword-filter {
  grid-column: span 2;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.search-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.search-form:not(.with-store) .search-actions {
  grid-column: span 2;
}

@media (max-width: 900px) {
  .search-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-actions,
  .search-form:not(.with-store) .search-actions {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .search-form {
    grid-template-columns: 1fr;
  }

  .keyword-filter {
    grid-column: auto;
  }
}
</style>
