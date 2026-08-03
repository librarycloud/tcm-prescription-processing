<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">首页</h1>
        <p class="page-subtitle">查看今日加工、待领取与包裹流转情况</p>
        <el-tag v-if="userStore.isStoreAdmin" class="store-tag" effect="plain">
          当前门店：{{ stats.store?.name || userStore.user?.store?.name || '-' }}
        </el-tag>
      </div>
      <div class="page-actions">
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="selectedStoreId"
          clearable
          placeholder="全部门店"
          @change="handleStoreChange"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <el-button :icon="Calendar" @click="router.push('/admin/processing-plans?mode=calendar')">
          日历工作台
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-action" @click="openProcessing('today-waiting')">
        <StatisticCard label="今日待加工" :value="stats.waitingCount" icon="List" type="primary" />
      </div>
      <div class="stat-action" @click="openProcessing('processing')">
        <StatisticCard
          label="加工中"
          :value="stats.processingCount"
          icon="Loading"
          type="primary"
        />
      </div>
      <div class="stat-action" @click="openProcessing('today-finished')">
        <StatisticCard
          label="今日完成"
          :value="stats.todayFinished"
          icon="CircleCheck"
          type="success"
        />
      </div>
      <div class="stat-action" @click="openProcessing('notice')">
        <StatisticCard label="等待通知" :value="stats.waitingNoticeCount" icon="Bell" type="info" />
      </div>
      <div class="stat-action" @click="openProcessing('urgent')">
        <StatisticCard label="加急" :value="stats.urgentCount" icon="Warning" type="warning" />
      </div>
      <div class="stat-action" @click="openPackages({ status: 0 })">
        <StatisticCard
          label="待领取"
          :value="stats.pendingCount"
          icon="ShoppingBag"
          type="success"
        />
      </div>
      <div class="stat-action" @click="openPackages({ status: 1, dateScope: 'today-picked' })">
        <StatisticCard
          label="今日领取"
          :value="stats.todayPicked"
          icon="CircleCheck"
          type="success"
        />
      </div>
    </div>

    <div class="section-heading">
      <h2>门店调拨</h2>
      <el-button link type="primary" @click="router.push('/admin/store-transfers')"
        >查看全部</el-button
      >
    </div>
    <div class="transfer-stat-grid">
      <div class="stat-action" @click="openTransfers({ status: TRANSFER_STATUS.BORROWING })">
        <StatisticCard label="借出中" :value="transferStats.borrowing" icon="Sort" type="primary" />
      </div>
      <div class="stat-action" @click="openTransfers({ status: TRANSFER_STATUS.PART_RETURNED })">
        <StatisticCard
          label="部分归还"
          :value="transferStats.partReturned"
          icon="Refresh"
          type="warning"
        />
      </div>
      <div class="stat-action" @click="openTransfers({ pending: '1' })">
        <StatisticCard label="待调平" :value="transferStats.pending" icon="Clock" type="info" />
      </div>
      <div class="stat-action" @click="openTransfers({ overdue: '1' })">
        <StatisticCard
          label="逾期调拨"
          :value="transferStats.overdue"
          icon="Warning"
          type="danger"
        />
      </div>
    </div>

    <el-card class="next-task" shadow="never">
      <template #header>
        <div class="next-task-header">
          <span>当前第一项待加工任务</span>
          <el-button link type="primary" @click="router.push('/admin/processing-plans')"
            >进入工作台</el-button
          >
        </div>
      </template>
      <div v-if="stats.firstTask" class="next-task-content">
        <span class="queue-number">{{ queueText(stats.firstTask.queueOrder) }}</span>
        <el-tag v-if="stats.firstTask.priority === PRIORITY.URGENT" type="danger" effect="dark">
          【加急】
        </el-tag>
        <strong>{{ stats.firstTask.prescription?.customerName || '-' }}</strong>
        <span class="task-detail">
          {{ stats.firstTask.processType?.name || '-' }} {{ stats.firstTask.totalDose }}剂
        </span>
      </div>
      <EmptyView v-else description="当前没有待加工任务" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="package-list-header">
          <span>今日包裹列表</span>
          <el-button link type="primary" @click="router.push('/admin/packages')"
            >包裹管理</el-button
          >
        </div>
      </template>
      <el-table v-loading="packageLoading" :data="packageList" border table-layout="auto">
        <template #empty><EmptyView description="暂无今日或逾期包裹" /></template>
        <el-table-column label="分类" align="center">
          <template #default="{ row }">
            <el-tag :type="packageCategoryType(row)" effect="plain">
              {{ packageCategory(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="录入时间" align="center">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店" align="center">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="pickupCode" label="取货码" align="center" />
        <el-table-column prop="receiverName" label="收件人" align="center" />
        <el-table-column prop="receiverPhone" label="手机号" align="center">
          <template #default="{ row }">{{ row.receiverPhone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="物品名称" align="center" />
        <el-table-column prop="pickedAt" label="取货时间" align="center">
          <template #default="{ row }">{{ formatDate(row.pickedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPackageDrawer('detail', row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :page="packagePagination.page"
        :page-size="packagePagination.pageSize"
        :total="packagePagination.total"
        @update:page="changePackagePage"
        @update:page-size="changePackagePageSize"
      />
    </el-card>
    <el-drawer v-model="packageDrawerVisible" size="min(720px, 96vw)" destroy-on-close>
      <template #header>
        <div class="drawer-header">
          <span>{{ packageDrawerTitle }}</span>
          <el-button
            v-if="packageDrawerMode === 'detail' && packageDrawerId"
            type="primary"
            size="small"
            :icon="Printer"
            @click="printPackageLabel"
          >
            打印标签
          </el-button>
        </div>
      </template>
      <PackageDetail
        v-if="packageDrawerMode === 'detail' && packageDrawerId"
        :key="`detail-${packageDrawerId}`"
        :id="packageDrawerId"
        ref="packageDetailRef"
        embedded
        @edit="openPackageDrawer('edit', $event)"
        @verify="openPackageDrawer('verify', $event)"
      />
      <PackageEdit
        v-else-if="packageDrawerMode === 'edit' && packageDrawerId"
        :key="`edit-${packageDrawerId}`"
        :id="packageDrawerId"
        embedded
        @saved="handlePackageDrawerSaved"
        @cancel="closePackageDrawer"
      />
      <Verify
        v-else-if="packageDrawerMode === 'verify'"
        :key="`verify-${packageDrawerCode || packageDrawerId || 'new'}`"
        :initial-pickup-code="packageDrawerCode"
        embedded
        @success="handlePackageDrawerVerified"
        @cancel="closePackageDrawer"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Calendar, Printer, Refresh } from '@element-plus/icons-vue';
import StatisticCard from '@/components/StatisticCard.vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import PackageDetail from './PackageDetail.vue';
import PackageEdit from './PackageEdit.vue';
import Verify from './Verify.vue';
import { getAdminPackages, getStats } from '@/api/package';
import { PRIORITY } from '@/constants/processing';
import { TRANSFER_STATUS } from '@/constants/storeTransfer';
import { formatDate } from '@/utils/date';
import { formatPickupCode, PACKAGE_STATUS } from '@/utils/status';
import { useUserStore } from '@/stores/user';
import { getStores } from '@/api/store';
import { getStoreTransferStats } from '@/api/storeTransfer';

const loading = ref(false);
const packageLoading = ref(false);
const packageDrawerVisible = ref(false);
const packageDrawerMode = ref('detail');
const packageDrawerId = ref(null);
const packageDrawerCode = ref('');
const packageDetailRef = ref(null);
const router = useRouter();
const userStore = useUserStore();
const selectedStoreId = ref('');
const stores = ref([]);
const packageList = ref([]);
const packagePagination = reactive({ page: 1, pageSize: 10, total: 0 });

const stats = reactive({
  pendingCount: 0,
  todayAdded: 0,
  todayPicked: 0,
  waitingCount: 0,
  processingCount: 0,
  waitingNoticeCount: 0,
  todayFinished: 0,
  urgentCount: 0,
  store: null,
  firstTask: null
});
const transferStats = reactive({ borrowing: 0, partReturned: 0, pending: 0, overdue: 0 });

function queueText(value) {
  return String(value || 1).padStart(3, '0');
}

function isToday(value) {
  const date = new Date(value);
  const today = new Date();
  return (
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()
  );
}

function packageCategory(row) {
  if (Number(row.status) === PACKAGE_STATUS.PICKED) return '今日已取';
  return isToday(row.createdAt) ? '今日未取' : '逾期未取';
}

function packageCategoryType(row) {
  if (Number(row.status) === PACKAGE_STATUS.PICKED) return 'success';
  return isToday(row.createdAt) ? 'warning' : 'danger';
}

async function loadPackages() {
  packageLoading.value = true;
  try {
    const data = await getAdminPackages({
      storeId: selectedStoreId.value || undefined,
      dateScope: 'dashboard',
      page: packagePagination.page,
      pageSize: packagePagination.pageSize,
      sortBy: 'createdAt',
      sortOrder: 'desc'
    });
    packageList.value = (data?.list || []).map((item) => ({
      ...item,
      pickupCode: formatPickupCode(item.pickupCode)
    }));
    packagePagination.total = data?.pagination?.total || 0;
  } finally {
    packageLoading.value = false;
  }
}

async function loadData() {
  loading.value = true;
  try {
    const [statData, transferStatData] = await Promise.all([
      getStats({ storeId: selectedStoreId.value || undefined }),
      getStoreTransferStats({ storeId: selectedStoreId.value || undefined }),
      loadPackages()
    ]);
    Object.assign(stats, statData || {});
    Object.assign(transferStats, transferStatData || {});
  } finally {
    loading.value = false;
  }
}

function openTransfers(query = {}) {
  router.push({ path: '/admin/store-transfers', query: withSelectedStore(query) });
}

function withSelectedStore(query = {}) {
  return {
    ...query,
    ...(userStore.isSuperAdmin && selectedStoreId.value ? { storeId: selectedStoreId.value } : {})
  };
}

function openProcessing(view) {
  router.push({ path: '/admin/processing-plans', query: withSelectedStore({ view }) });
}

function openPackages(query = {}) {
  router.push({ path: '/admin/packages', query: withSelectedStore(query) });
}

const packageDrawerTitle = computed(() => {
  if (packageDrawerMode.value === 'verify') return '包裹核销';
  if (packageDrawerMode.value === 'edit') return '编辑包裹';
  return '包裹详情';
});

function openPackageDrawer(mode, row = null) {
  packageDrawerMode.value = mode;
  packageDrawerId.value = row?.id || null;
  packageDrawerCode.value = row?.pickupCode || '';
  packageDrawerVisible.value = true;
}

function closePackageDrawer() {
  packageDrawerVisible.value = false;
}

function printPackageLabel() {
  packageDetailRef.value?.openPrint();
}

function handlePackageDrawerSaved() {
  closePackageDrawer();
  loadPackages();
}

function handlePackageDrawerVerified() {
  closePackageDrawer();
  loadPackages();
}

function handleStoreChange() {
  packagePagination.page = 1;
  loadData();
}

function changePackagePage(page) {
  packagePagination.page = page;
  loadPackages();
}

function changePackagePageSize(pageSize) {
  packagePagination.page = 1;
  packagePagination.pageSize = pageSize;
  loadPackages();
}

onMounted(async () => {
  if (userStore.isSuperAdmin) {
    const data = await getStores({ page: 1, pageSize: 100 });
    stores.value = data?.list || [];
  }
  await loadData();
});
</script>

<style scoped>
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24px 0 12px;
}

.section-heading h2 {
  margin: 0;
  font-size: 18px;
}

.transfer-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.stat-action {
  cursor: pointer;
}

.stat-action:hover :deep(.el-card) {
  border-color: var(--el-color-primary-light-5);
}

.page-actions {
  display: flex;
  gap: 10px;
}

.page-actions :deep(.el-select) {
  width: 180px;
}

.store-tag {
  margin-top: 8px;
}

.next-task {
  margin-bottom: 16px;
}

.next-task-header,
.next-task-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.next-task-header {
  justify-content: space-between;
}

.queue-number {
  color: #2563eb;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.task-detail {
  color: var(--app-muted);
}

.package-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .transfer-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .transfer-stat-grid {
    grid-template-columns: 1fr;
  }

  .page-actions,
  .next-task-content {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
