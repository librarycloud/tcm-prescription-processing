<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">包裹管理</h1>
        <p class="page-subtitle">管理取货码、二维码、通知与核销记录</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
        <el-button type="success" :icon="CircleCheck" @click="openPackageDrawer('verify')">
          包裹核销
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openPackageDrawer('add')">
          新增包裹
        </el-button>
      </div>
    </div>

    <SearchBar
      v-model:model="query"
      :show-store="userStore.isSuperAdmin"
      :stores="stores"
      @search="handleSearch"
      @reset="handleReset"
    />

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        border
        table-layout="auto" @sort-change="handleSortChange">
        <template #empty>
          <EmptyView description="暂无包裹" />
        </template>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <div class="table-actions package-actions">
              <el-button link type="primary" @click="openPackageDrawer('detail', row)">
                详情
              </el-button>
              <el-button
                link
                type="primary"
                :disabled="isPicked(row.status)"
                @click="openPackageDrawer('edit', row)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="success"
                :disabled="isPicked(row.status)"
                @click="openPackageDrawer('verify', row)"
              >
                核销
              </el-button>
              <el-tooltip
                :disabled="!row.processingPlanId"
                content="加工计划生成的包裹不能单独删除"
                placement="top"
              >
                <span>
                  <el-button
                    link
                    type="danger"
                    :disabled="Boolean(row.processingPlanId)"
                    @click="openDelete(row)"
                  >
                    删除
                  </el-button>
                </span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="userStore.isSuperAdmin" label="所属门店" align="center">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="pickupCode" label="取货码" align="center" />
        <el-table-column prop="itemName" label="物品名称" align="center">
          <template #default="{ row }">
            <span class="item-name-text" :title="row.itemName || ''">
              {{ truncateItemName(row.itemName) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="receiverName" label="收件人" align="center" />
        <el-table-column prop="receiverPhone" label="手机号" align="center">
          <template #default="{ row }">{{ maskPhone(row.receiverPhone) }}</template>
        </el-table-column>
        <el-table-column prop="pickupMethod" label="取货方式" align="center">
          <template #default="{ row }">
            <el-tag :type="pickupMethodTagType(row.pickupMethod)" effect="plain">
              {{ pickupMethodText(row.pickupMethod) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="notificationStatus" label="通知状态" align="center">
          <template #default="{ row }">
            <NotificationStatus
              :status="row.notificationStatus"
              :count="row.notificationCount"
              :disabled="isPicked(row.status)"
              @click="openNotification(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="pickedAt" label="取货时间" align="center" sortable="custom">
          <template #default="{ row }">{{ formatDate(row.pickedAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="录入时间" align="center" sortable="custom">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
      />
    </el-card>

    <ConfirmDialog
      v-model="deleteDialogVisible"
      title="确认删除包裹"
      :content="`确认删除取货码 ${selectedPackage?.pickupCode || '-'} 的包裹“${selectedPackage?.itemName || '-'}”吗？删除后无法恢复。`"
      confirm-type="danger"
      :loading="deleteLoading"
      @confirm="handleDelete"
    />
    <NotificationDialog
      v-model="notificationDialogVisible"
      :package-info="selectedNotificationPackage"
      @sent="loadData"
    />
    <el-drawer
      v-model="packageDrawerVisible"
      size="min(720px, 96vw)"
      destroy-on-close
    >
      <template #header>
        <div class="drawer-header">
          <span>{{ packageDrawerTitle }}</span>
          <el-button
            v-if="packageDrawerMode === 'detail' && selectedPackageId"
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
        v-if="packageDrawerMode === 'detail'"
        :id="selectedPackageId"
        :key="`detail-${selectedPackageId}`"
        ref="packageDetailRef"
        embedded
        @edit="openPackageDrawer('edit', $event)"
        @verify="openPackageDrawer('verify', $event)"
      />
      <PackageEdit
        v-else-if="packageDrawerMode === 'edit'"
        :id="selectedPackageId"
        :key="`edit-${selectedPackageId}`"
        embedded
        @saved="handlePackageDrawerSaved"
        @cancel="closePackageDrawer"
      />
      <PackageAdd
        v-else-if="packageDrawerMode === 'add'"
        embedded
        @saved="handlePackageDrawerSaved"
        @cancel="closePackageDrawer"
      />
      <Verify
        v-else-if="packageDrawerMode === 'verify'"
        :key="`verify-${selectedPackageCode || 'empty'}`"
        :initial-pickup-code="selectedPackageCode"
        embedded
        @success="handlePackageDrawerVerified"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { CircleCheck, Plus, Printer, Refresh } from '@element-plus/icons-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import EmptyView from '@/components/EmptyView.vue';
import NotificationDialog from '@/components/NotificationDialog.vue';
import NotificationStatus from '@/components/NotificationStatus.vue';
import Pagination from '@/components/Pagination.vue';
import SearchBar from '@/components/SearchBar.vue';
import StatusTag from '@/components/StatusTag.vue';
import PackageAdd from './PackageAdd.vue';
import PackageDetail from './PackageDetail.vue';
import PackageEdit from './PackageEdit.vue';
import Verify from './Verify.vue';
import { deletePackage, getAdminPackages } from '@/api/package';
import { formatDate } from '@/utils/date';
import { maskPhone } from '@/utils/phone';
import { formatPickupCode, isPicked, PACKAGE_STATUS, pickupMethodTagType, pickupMethodText } from '@/utils/status';
import { getStores } from '@/api/store';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const userStore = useUserStore();
const loading = ref(false);
const deleteLoading = ref(false);
const deleteDialogVisible = ref(false);
const notificationDialogVisible = ref(false);
const selectedPackage = ref(null);
const selectedNotificationPackage = ref(null);
const packageDrawerVisible = ref(false);
const packageDrawerMode = ref('detail');
const selectedPackageId = ref(null);
const selectedPackageCode = ref('');
const packageDetailRef = ref(null);
const list = ref([]);
const stores = ref([]);

const query = ref({
  keyword: '',
  storeId: '',
  status: '',
  dateScope: '',
  sortBy: 'createdAt',
  sortOrder: 'desc'
});

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
});

async function loadData() {
  loading.value = true;
  try {
    const data = await getAdminPackages({
      ...query.value,
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

function handleReset() {
  query.value = {
    keyword: '',
    storeId: '',
    status: '',
    dateScope: '',
    sortBy: 'createdAt',
    sortOrder: 'desc'
  };
  handleSearch();
}

function handleSortChange({ prop, order }) {
  if (!prop) return;
  query.value.sortBy = prop;
  query.value.sortOrder = order === 'ascending' ? 'asc' : 'desc';
  handleSearch();
}

function truncateItemName(value) {
  const text = String(value || '-');
  return text.length > 5 ? `${text.slice(0, 5)}...` : text;
}

const packageDrawerTitle = computed(() => {
  const titles = { detail: '包裹详情', edit: '编辑包裹', add: '新增包裹', verify: '包裹核销' };
  return titles[packageDrawerMode.value] || '包裹';
});

function openPackageDrawer(mode, row = null) {
  packageDrawerMode.value = mode;
  selectedPackageId.value = row?.id || null;
  selectedPackageCode.value = row?.pickupCode || '';
  packageDrawerVisible.value = true;
}

function closePackageDrawer() {
  packageDrawerVisible.value = false;
}

function printPackageLabel() {
  packageDetailRef.value?.openPrint();
}

async function handlePackageDrawerSaved() {
  closePackageDrawer();
  await loadData();
}

async function handlePackageDrawerVerified() {
  closePackageDrawer();
  await loadData();
}

function openDelete(row) {
  selectedPackage.value = row;
  deleteDialogVisible.value = true;
}

function openNotification(row) {
  selectedNotificationPackage.value = row;
  notificationDialogVisible.value = true;
}

async function handleDelete() {
  if (!selectedPackage.value) return;
  deleteLoading.value = true;
  try {
    await deletePackage(selectedPackage.value.id);
    ElMessage.success('包裹已删除');
    deleteDialogVisible.value = false;
    if (list.value.length === 1 && pagination.page > 1) pagination.page -= 1;
    else await loadData();
  } finally {
    deleteLoading.value = false;
  }
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
  const routeStatus = Number(route.query.status);
  if ([PACKAGE_STATUS.READY_PICKUP, PACKAGE_STATUS.PICKED].includes(routeStatus))
    query.value.status = routeStatus;
  if (
    ['today', 'today-picked', 'overdue', 'dashboard', 'pickup-workbench'].includes(
      route.query.dateScope
    )
  )
    query.value.dateScope = route.query.dateScope;
  if (userStore.isSuperAdmin && route.query.storeId)
    query.value.storeId = Number(route.query.storeId);
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

.page-actions {
  display: flex;
  gap: 10px;
}

.item-name-text {
  display: inline-block;
  max-width: 7em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.table-actions {
  justify-content: center;
}

.package-actions {
  gap: 2px;
  width: auto;
  padding: 0;
  white-space: nowrap;
}

.package-actions > span {
  display: inline-flex;
  align-items: center;
}

.package-actions :deep(.el-button) {
  margin-left: 0;
  padding-right: 3px;
  padding-left: 3px;
}

@media (max-width: 640px) {
  .page-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
