<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">操作日志</h1>
        <p class="page-subtitle">查询关键业务操作与审计记录</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="search-form" @submit.prevent="handleSearch">
        <el-select v-model="filters.storeId" clearable filterable placeholder="全部门店" @change="handleSearch">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
        <el-input-number
          v-model="filters.actorId"
          :min="1"
          :controls="false"
          placeholder="操作人 ID"
          style="width: 100%"
          @change="handleSearch"
        />
        <el-input v-model.trim="filters.module" clearable placeholder="模块，例如 prescription" />
        <el-input v-model.trim="filters.action" clearable placeholder="动作，例如 create" />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="handleSearch"
        />
        <div class="search-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty><EmptyView description="暂无操作日志" /></template>
        <el-table-column prop="createdAt" label="操作时间" align="center">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="门店" show-overflow-tooltip>
          <template #default="{ row }">{{ row.store?.name || '全局' }}</template>
        </el-table-column>
        <el-table-column label="操作人" show-overflow-tooltip>
          <template #default="{ row }">{{ row.actorName || '-' }}<span v-if="row.actorId">（{{ row.actorId }}）</span></template>
        </el-table-column>
        <el-table-column prop="module" label="模块" align="center" />
        <el-table-column prop="action" label="动作" align="center" />
        <el-table-column label="业务对象" show-overflow-tooltip>
          <template #default="{ row }">{{ row.targetLabel || '-' }}</template>
        </el-table-column>
        <el-table-column
          prop="description"
          label="详细内容"
          class-name="log-description"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="log-description-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" align="center">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { Refresh, Search } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { getOperationLogs } from '@/api/operationLog';
import { getStores } from '@/api/store';
import { formatDate } from '@/utils/date';

const loading = ref(false);
const list = ref([]);
const stores = ref([]);
const dateRange = ref([]);
const filters = reactive({ storeId: '', actorId: undefined, module: '', action: '' });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });

async function loadStores() {
  const data = await getStores({ page: 1, pageSize: 100, status: 1 });
  stores.value = data?.list || [];
}

async function loadData() {
  loading.value = true;
  try {
    const data = await getOperationLogs({
      ...filters,
      startDate: dateRange.value?.[0] || '',
      endDate: dateRange.value?.[1] || '',
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  if (pagination.page !== 1) pagination.page = 1;
  else loadData();
}

function handleReset() {
  Object.assign(filters, { storeId: '', actorId: undefined, module: '', action: '' });
  dateRange.value = [];
  handleSearch();
}

watch(() => [pagination.page, pagination.pageSize], loadData);
onMounted(() => Promise.all([loadStores(), loadData()]));
</script>

<style scoped>
.search-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(150px, 1fr)) minmax(260px, 1.4fr) auto;
  gap: 12px;
  align-items: center;
}

.search-actions {
  display: flex;
  gap: 8px;
}

:deep(.log-description .cell) {
  line-height: normal;
  text-align: left;
}

.log-description-text {
  display: block;
  max-width: min(360px, 32vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1280px) {
  .search-form { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 720px) {
  .search-form { grid-template-columns: 1fr; }
  .search-actions { justify-content: flex-end; }
}
</style>
