<template>
  <div class="page e6-pharmacy-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">E6药店商品库存</h1>
        <p class="page-subtitle">查询 E6 当前库存中存在的商品及批号库存</p>
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="search-form" @submit.prevent="search">
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="query.storeId"
          clearable
          placeholder="全部门店"
          @change="search"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <el-input
          v-model.trim="query.keyword"
          clearable
          placeholder="编号、商品名称或条形码"
          @keyup.enter="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="list"
        row-key="id"
        border
        table-layout="auto"
        @row-click="toggleRow"
      >
        <template #empty><EmptyView description="暂无 E6 药店库存" /></template>
        <el-table-column type="expand" width="1">
          <template #default="{ row }">
            <div class="batch-panel">
              <div class="batch-title">批号库存（{{ row.batchCount }}）</div>
              <el-table :data="row.inventories" border size="small" table-layout="auto">
                <el-table-column prop="batchNo" label="批号" min-width="140" />
                <el-table-column prop="locationName" label="货位" min-width="140" />
                <el-table-column label="生产日期" width="130">
                  <template #default="{ row: batch }">{{ dateText(batch.productionDate) }}</template>
                </el-table-column>
                <el-table-column label="入库日期" width="130">
                  <template #default="{ row: batch }">{{ dateText(batch.inboundDate) }}</template>
                </el-table-column>
                <el-table-column label="有效期至" width="130">
                  <template #default="{ row: batch }">{{ dateText(batch.expiryDate) }}</template>
                </el-table-column>
                <el-table-column label="库存数量" width="120" align="right">
                  <template #default="{ row: batch }">{{ quantityText(batch.quantity) }}</template>
                </el-table-column>
                <el-table-column label="库存金额" width="130" align="right">
                  <template #default="{ row: batch }">{{ moneyText(batch.amount) }}</template>
                </el-table-column>
                <el-table-column label="更新时间" min-width="170">
                  <template #default="{ row: batch }">{{ dateTimeText(batch.updatedAt) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店" min-width="120">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="productCode" label="商品编号" min-width="130" />
        <el-table-column label="商品名称" min-width="150">
          <template #default="{ row }">
            <el-tooltip v-if="isLongText(row.name)" :content="row.name" placement="top">
              <span>{{ shortText(row.name) }}</span>
            </el-tooltip>
            <span v-else>{{ row.name || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" min-width="80">
          <template #default="{ row }">{{ row.unit || '-' }}</template>
        </el-table-column>
        <el-table-column label="批号数" width="64" align="center">
          <template #default="{ row }">{{ row.batchCount }}</template>
        </el-table-column>
        <el-table-column label="总库存" width="78" align="right">
          <template #default="{ row }">{{ quantityText(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column prop="barcode" label="条形码" min-width="140">
          <template #default="{ row }">{{ row.barcode || '-' }}</template>
        </el-table-column>
        <el-table-column label="规格" width="98">
          <template #default="{ row }">
            <el-tooltip v-if="isLongText(row.specification)" :content="row.specification" placement="top">
              <span>{{ shortText(row.specification) }}</span>
            </el-tooltip>
            <span v-else>{{ row.specification || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dosageForm" label="剂型" width="98">
          <template #default="{ row }">{{ row.dosageForm || '-' }}</template>
        </el-table-column>
        <el-table-column label="生产厂商" min-width="150">
          <template #default="{ row }">
            <el-tooltip v-if="isLongText(row.manufacturer)" :content="row.manufacturer" placement="top">
              <span>{{ shortText(row.manufacturer) }}</span>
            </el-tooltip>
            <span v-else>{{ row.manufacturer || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="E6修改时间" min-width="170">
          <template #default="{ row }">{{ dateTimeText(row.e6ModifiedAt) }}</template>
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
import { useUserStore } from '@/stores/user';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { getProductStores } from '@/api/productDifference';
import { getE6PharmacyProducts } from '@/api/e6Pharmacy';

const userStore = useUserStore();
const tableRef = ref();
const loading = ref(false);
const list = ref([]);
const stores = ref([]);
const query = reactive({ keyword: '', storeId: undefined });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });

function dateText(value) {
  if (!value) return '-';
  return String(value).slice(0, 10);
}

function dateTimeText(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 19);
}

function quantityText(value) {
  const number = Number(value || 0);
  return Number.isInteger(number) ? String(number) : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

function moneyText(value) {
  return `¥ ${Number(value || 0).toFixed(2)}`;
}

function isLongText(value) {
  return String(value || '').length > 12;
}

function shortText(value) {
  return `${String(value).slice(0, 12)}…`;
}

async function loadStores() {
  if (!userStore.isSuperAdmin) return;
  stores.value = await getProductStores();
}

async function load() {
  loading.value = true;
  try {
    const data = await getE6PharmacyProducts({
      keyword: query.keyword || undefined,
      storeId: query.storeId || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data.list || [];
    Object.assign(pagination, data.pagination || {});
  } finally {
    loading.value = false;
  }
}

function search() {
  pagination.page = 1;
  load();
}

function resetSearch() {
  query.keyword = '';
  query.storeId = undefined;
  pagination.page = 1;
  load();
}

function toggleRow(row) {
  tableRef.value?.toggleRowExpansion(row);
}

watch(() => [pagination.page, pagination.pageSize], load);

onMounted(async () => {
  await loadStores();
  await load();
});
</script>

<style scoped>
.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.search-form :deep(.el-input) {
  width: 280px;
}

.search-form :deep(.el-select) {
  width: 150px;
}

.e6-pharmacy-page :deep(.el-table__expand-column) {
  width: 0 !important;
  padding: 0 !important;
}

.e6-pharmacy-page :deep(.el-table__expand-column .cell),
.e6-pharmacy-page :deep(.el-table__expand-icon) {
  display: none;
}

.e6-pharmacy-page :deep(.el-table .cell) {
  min-width: 0;
  padding-right: 4px;
  padding-left: 4px;
  white-space: nowrap;
}

.batch-panel {
  padding: 4px 18px 12px 52px;
}

.batch-title {
  font-weight: 600;
  margin-bottom: 10px;
}

@media (max-width: 768px) {
  .search-form :deep(.el-input),
  .search-form :deep(.el-select) {
    width: 100%;
  }

  .batch-panel {
    padding-left: 8px;
    padding-right: 8px;
  }
}
</style>
