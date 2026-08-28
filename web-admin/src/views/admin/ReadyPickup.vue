<template>
  <div :class="embedded ? 'pickup-workbench' : 'page'">
    <div v-if="!embedded" class="page-header">
      <div>
        <h1 class="page-title">待领取</h1>
        <p class="page-subtitle">查看加工完成并已生成取货码的领取任务</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <div v-else class="embedded-header">
      <div>
        <h2>领取任务</h2>
        <span>显示今日加工生成的全部包裹，以及今天以前仍未领取的包裹</span>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="filters" inline @submit.prevent>
        <el-form-item label="搜索">
          <el-input
            v-model.trim="query.keyword"
            :prefix-icon="Search"
            clearable
            placeholder="姓名或手机号"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item v-if="userStore.isSuperAdmin" label="门店">
          <el-select v-model="query.storeId" clearable placeholder="全部门店" @change="handleSearch">
            <el-option
              v-for="store in stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        class="ready-pickup-table"
        :data="list"
        row-key="id"
        border table-layout="auto">
        <template #empty>
          <EmptyView description="暂无加工领取任务" />
        </template>
        <el-table-column label="顾客" align="center">
          <template #default="{ row }">
            <div class="customer-name">{{ row.receiverName || '-' }}</div>
            <div class="secondary-text">{{ row.receiverPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="userStore.isSuperAdmin"
          label="所属门店"
          align="center"
        >
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="加工方式" align="center">
          <template #default="{ row }">{{ row.processingPlan?.processType?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="剂数" align="center">
          <template #default="{ row }">{{ row.processingPlan?.totalDose || '-' }}</template>
        </el-table-column>
        <el-table-column label="完成时间" align="center">
          <template #default="{ row }">{{ formatDate(row.processingPlan?.finishDate) }}</template>
        </el-table-column>
        <el-table-column label="取货码" align="center">
          <template #default="{ row }">
            <span class="pickup-code">{{ formatPickupCode(row.pickupCode) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="归类" align="center">
          <template #default="{ row }">
            <el-tag :type="isOverdue(row) ? 'danger' : 'primary'" effect="plain">
              {{ isOverdue(row) ? '逾期未取' : '今日生成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="领取状态" align="center">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="取货时间" align="center">
          <template #default="{ row }">{{ formatDate(row.pickedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-tooltip content="查看二维码" placement="top">
                <el-button
                  v-if="!embedded"
                  link
                  type="primary"
                  :icon="Grid"
                  aria-label="查看二维码"
                  @click="showQrCode(row)"
                />
              </el-tooltip>
              <el-button link type="primary" @click="openDetail(row)">
                详情
              </el-button>
              <el-button v-if="!isPicked(row.status)" link type="success" @click="openVerify(row)">
                核销
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
      />
    </el-card>

    <el-dialog v-model="qrVisible" title="领取二维码" width="380px">
      <QRCodeCard v-if="selectedPackage" :content="selectedPackage.pickupQrContent || selectedPackage.pickupCode" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Grid, Refresh, Search } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import QRCodeCard from '@/components/QRCodeCard.vue';
import StatusTag from '@/components/StatusTag.vue';
import { getAdminPackages } from '@/api/package';
import { getStores } from '@/api/store';
import { useUserStore } from '@/stores/user';
import { formatDate } from '@/utils/date';
import { formatPickupCode, isPicked } from '@/utils/status';

const router = useRouter();
const props = defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(['detail', 'verify']);
const userStore = useUserStore();
const loading = ref(false);
const qrVisible = ref(false);
const selectedPackage = ref(null);
const list = ref([]);
const stores = ref([]);
const query = reactive({ keyword: '', storeId: '' });
const pagination = reactive({ page: 1, pageSize: 10, total: 0 });

async function loadData() {
  loading.value = true;
  try {
    const data = await getAdminPackages({
      ...query,
      source: 'processing',
      dateScope: 'pickup-workbench',
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = (data?.list || []).map((item) => ({
      ...item,
      pickupCode: formatPickupCode(item.pickupCode)
    }));
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.page = 1;
  loadData();
}

function resetSearch() {
  query.keyword = '';
  query.storeId = '';
  handleSearch();
}

function showQrCode(row) {
  selectedPackage.value = row;
  qrVisible.value = true;
}

function openDetail(row) {
  if (props.embedded) emit('detail', row);
  else router.push(`/admin/packages/${row.id}`);
}

function openVerify(row) {
  if (props.embedded) emit('verify', row);
  else router.push({ path: '/admin/verify', query: { pickupCode: row.pickupCode } });
}

function isOverdue(row) {
  if (isPicked(row.status)) return false;
  const createdAt = new Date(row.createdAt);
  const today = new Date();
  return (
    createdAt.getFullYear() !== today.getFullYear() ||
    createdAt.getMonth() !== today.getMonth() ||
    createdAt.getDate() !== today.getDate()
  );
}

watch(
  () => [pagination.page, pagination.pageSize],
  () => loadData()
);

onMounted(async () => {
  if (userStore.isSuperAdmin) {
    const data = await getStores({ page: 1, pageSize: 100 });
    stores.value = data?.list || [];
  }
  await loadData();
});
</script>

<style scoped>
.ready-pickup-table {
  width: 100%;
  table-layout: auto;
}

.ready-pickup-table :deep(.el-table__header),
.ready-pickup-table :deep(.el-table__body),
.ready-pickup-table :deep(.el-table__footer) {
  table-layout: auto !important;
}

.filters {
  margin-bottom: -18px;
}

.pickup-workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.embedded-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.embedded-header h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

.embedded-header span {
  color: var(--app-muted);
  font-size: 13px;
}

.filters :deep(.el-input),
.filters :deep(.el-select) {
  width: 220px;
}

.customer-name {
  font-weight: 600;
}

.secondary-text {
  margin-top: 3px;
  color: var(--app-muted);
  font-size: 12px;
}

.pickup-code {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

@media (max-width: 768px) {
  .embedded-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .filters,
  .filters :deep(.el-form-item),
  .filters :deep(.el-input),
  .filters :deep(.el-select) {
    width: 100%;
  }
}
</style>
