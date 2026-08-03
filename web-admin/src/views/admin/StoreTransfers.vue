<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">门店调拨</h1>
        <p class="page-subtitle">记录门店之间的临时借调、分次归还与调平过程</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建调拨</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-action" @click="applyStatus(TRANSFER_STATUS.BORROWING)">
        <StatisticCard label="借出中" :value="stats.borrowing" icon="Sort" type="primary" />
      </div>
      <div class="stat-action" @click="applyStatus(TRANSFER_STATUS.PART_RETURNED)">
        <StatisticCard label="部分归还" :value="stats.partReturned" icon="Refresh" type="warning" />
      </div>
      <div class="stat-action" @click="applyPending">
        <StatisticCard label="待调平" :value="stats.pending" icon="Clock" type="info" />
      </div>
      <div class="stat-action" @click="applyOverdue">
        <StatisticCard label="已逾期" :value="stats.overdue" icon="Warning" type="danger" />
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="filters" inline @submit.prevent>
        <el-form-item label="搜索">
          <el-input
            v-model.trim="query.keyword"
            :prefix-icon="Search"
            clearable
            placeholder="单号、门店、物品、规格或批号"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="调拨日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userStore.isSuperAdmin" label="门店">
          <el-select v-model="query.storeId" clearable filterable placeholder="全部门店">
            <el-option
              v-for="store in stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty><EmptyView description="暂无门店调拨记录" /></template>
        <el-table-column prop="transferNo" label="调拨单号" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ row.transferNo }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="调出门店" align="center">
          <template #default="{ row }">{{ row.fromStore?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="调入门店" align="center">
          <template #default="{ row }">{{ row.toStore?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="调拨日期" align="center">
          <template #default="{ row }">{{ formatDateOnly(row.transferDate) }}</template>
        </el-table-column>
        <el-table-column label="预计归还" align="center">
          <template #default="{ row }">
            <span :class="{ 'overdue-text': row.overdue }">{{
              formatDateOnly(row.expectedReturnDate)
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调拨项目">
          <template #default="{ row }">
            <span>{{ itemSummary(row) }}</span>
            <div class="secondary-text">共 {{ row.items?.length || 0 }} 项</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center">
          <template #default="{ row }">
            <div class="status-tags">
              <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
              <el-tag
                v-if="row.outboundStatus !== TRANSFER_OUTBOUND_STATUS.CONFIRMED"
                type="warning"
                effect="dark"
                >待确认调出</el-tag
              >
              <el-tag v-if="row.overdue" type="danger" effect="dark">已逾期</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建人" align="center">
          <template #default="{ row }">{{ operatorName(row.creator) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
      />
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerMode === 'create' ? '新建调拨' : `调拨详情 · ${detail?.transferNo || ''}`"
      size="min(920px, 92vw)"
      destroy-on-close
      @closed="resetDrawer"
    >
      <template v-if="drawerMode === 'create'">
        <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
          <div class="form-grid">
            <el-form-item label="调出门店" prop="fromStoreId">
              <el-select v-model="createForm.fromStoreId" filterable placeholder="选择调出门店">
                <el-option
                  v-for="store in sourceStores"
                  :key="store.id"
                  :label="store.name"
                  :value="store.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="调入门店" prop="toStoreId">
              <el-select
                v-if="userStore.isSuperAdmin"
                v-model="createForm.toStoreId"
                filterable
                placeholder="选择调入门店"
              >
                <el-option
                  v-for="store in destinationStores"
                  :key="store.id"
                  :label="store.name"
                  :value="store.id"
                />
              </el-select>
              <el-input
                v-else
                :model-value="userStore.user?.store?.name || currentStoreName"
                disabled
              />
            </el-form-item>
            <el-form-item label="调拨日期" prop="transferDate">
              <el-date-picker
                v-model="createForm.transferDate"
                type="date"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
            <el-form-item label="预计归还日期" prop="expectedReturnDate">
              <el-date-picker
                v-model="createForm.expectedReturnDate"
                type="date"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </div>
          <el-form-item label="备注">
            <el-input
              v-model="createForm.remark"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
          <div class="section-heading">
            <span>调拨明细</span>
            <el-button type="primary" plain :icon="Plus" @click="addCreateItem">添加明细</el-button>
          </div>
          <div class="item-editor-list">
            <section v-for="(item, index) in createForm.items" :key="index" class="item-editor">
              <div class="item-editor-header">
                <span>明细 {{ index + 1 }}</span>
                <el-button
                  circle
                  text
                  type="danger"
                  :icon="Delete"
                  :disabled="createForm.items.length === 1"
                  aria-label="删除明细"
                  title="删除明细"
                  @click="removeCreateItem(index)"
                />
              </div>
              <div class="item-editor-grid">
                <label class="item-field item-field-name">
                  <span class="item-field-label required-label">名称</span>
                  <el-input
                    v-model.trim="item.itemName"
                    maxlength="120"
                    placeholder="请输入物品名称"
                  />
                </label>
                <label class="item-field item-field-specification">
                  <span class="item-field-label">规格</span>
                  <el-input
                    v-model.trim="item.specification"
                    maxlength="120"
                    placeholder="例如 10g/袋"
                  />
                </label>
                <label class="item-field item-field-batch">
                  <span class="item-field-label">批号</span>
                  <el-input v-model.trim="item.batchNo" maxlength="100" placeholder="选填" />
                </label>
                <label class="item-field item-field-quantity">
                  <span class="item-field-label required-label">数量</span>
                  <el-input-number
                    v-model="item.quantity"
                    :min="0.001"
                    :precision="3"
                    :step="1"
                    controls-position="right"
                  />
                </label>
                <label class="item-field item-field-unit">
                  <span class="item-field-label required-label">单位</span>
                  <el-input v-model.trim="item.unit" maxlength="20" placeholder="例如 g、盒" />
                </label>
                <label class="item-field item-field-remark">
                  <span class="item-field-label">备注</span>
                  <el-input v-model.trim="item.remark" maxlength="500" placeholder="选填" />
                </label>
              </div>
            </section>
          </div>
        </el-form>
      </template>

      <div v-else-if="detail" v-loading="detailLoading" class="detail-content">
        <div class="detail-title-row">
          <h3>基础信息</h3>
          <div class="status-tags">
            <el-tag :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
            <el-tag
              v-if="detail.outboundStatus !== TRANSFER_OUTBOUND_STATUS.CONFIRMED"
              type="warning"
              effect="dark"
              >待确认调出</el-tag
            >
            <el-tag v-if="detail.overdue" type="danger" effect="dark">已逾期</el-tag>
          </div>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="调拨单号">{{ detail.transferNo }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{
            operatorName(detail.creator)
          }}</el-descriptions-item>
          <el-descriptions-item label="调出门店">{{
            detail.fromStore?.name || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="调入门店">{{
            detail.toStore?.name || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="调出确认">
            {{
              detail.outboundStatus === TRANSFER_OUTBOUND_STATUS.CONFIRMED
                ? '已确认调出'
                : '待确认调出'
            }}
          </el-descriptions-item>
          <el-descriptions-item label="确认人">
            {{ operatorName(detail.outboundConfirmer) }}
          </el-descriptions-item>
          <el-descriptions-item label="确认时间">
            {{ detail.outboundConfirmedAt ? formatDate(detail.outboundConfirmedAt) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="调拨日期">{{
            formatDateOnly(detail.transferDate)
          }}</el-descriptions-item>
          <el-descriptions-item label="预计归还日期">
            <span :class="{ 'overdue-text': detail.overdue }">{{
              formatDateOnly(detail.expectedReturnDate)
            }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            detail.remark || '-'
          }}</el-descriptions-item>
          <el-descriptions-item
            v-if="detail.status === TRANSFER_STATUS.CANCELLED"
            label="取消原因"
            :span="2"
          >
            {{ detail.cancelReason || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <h3>调拨明细</h3>
        <el-table :data="detail.items" border table-layout="auto">
          <el-table-column prop="itemName" label="名称" />
          <el-table-column prop="specification" label="规格">
            <template #default="{ row }">{{ row.specification || '-' }}</template>
          </el-table-column>
          <el-table-column prop="batchNo" label="批号">
            <template #default="{ row }">{{ row.batchNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" align="center" />
          <el-table-column label="借调" align="right">
            <template #default="{ row }">{{ quantityText(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="已确认" align="right">
            <template #default="{ row }">{{ quantityText(row.returnedQuantity) }}</template>
          </el-table-column>
          <el-table-column label="待确认" align="right">
            <template #default="{ row }">{{ quantityText(row.pendingReturnQuantity) }}</template>
          </el-table-column>
          <el-table-column label="剩余" align="right">
            <template #default="{ row }"
              ><strong>{{ quantityText(row.remainingQuantity) }}</strong></template
            >
          </el-table-column>
          <el-table-column label="操作" align="center">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="!detail.permissions?.canSubmitReturn || row.availableReturnQuantity <= 0"
                @click="openReturn(row)"
                >申请归还</el-button
              >
            </template>
          </el-table-column>
        </el-table>

        <h3>归还记录</h3>
        <el-table :data="detail.returnRecords" border table-layout="auto">
          <template #empty><EmptyView description="暂无归还记录" /></template>
          <el-table-column prop="itemName" label="物品" />
          <el-table-column label="数量" align="right">
            <template #default="{ row }">{{ quantityText(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="状态" align="center">
            <template #default="{ row }">
              <el-tag :type="returnStatusType(row.status)">{{
                returnStatusText(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="归还日期" align="center">
            <template #default="{ row }">{{ formatDateOnly(row.returnDate) }}</template>
          </el-table-column>
          <el-table-column label="发起信息" align="center">
            <template #default="{ row }">
              <div class="table-meta">
                <span>{{ operatorName(row.operator) }}</span>
                <span>{{ formatDate(row.createdAt) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="确认信息" align="center">
            <template #default="{ row }">
              <div v-if="row.confirmedAt" class="table-meta">
                <span>{{ operatorName(row.confirmer) }}</span>
                <span>{{ formatDate(row.confirmedAt) }}</span>
              </div>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="
                  row.status !== TRANSFER_RETURN_STATUS.PENDING ||
                  !detail.permissions?.canConfirmReturn
                "
                @click="confirmReturn(row)"
                >确认收货</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button
            v-if="drawerMode === 'detail' && detail?.permissions?.canCancel"
            type="danger"
            plain
            @click="cancelTransfer"
            >取消调拨</el-button
          >
          <span class="footer-spacer" />
          <template v-if="drawerMode === 'create'">
            <el-button @click="drawerVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveCreate">提交调拨申请</el-button>
          </template>
          <template v-else>
            <el-button
              v-if="detail?.permissions?.canConfirmOutbound"
              type="primary"
              :loading="saving"
              @click="confirmOutbound"
              >确认调出</el-button
            >
            <el-button
              :disabled="!detail?.permissions?.canSubmitReturn"
              type="primary"
              plain
              @click="openReturn()"
              >发起归还</el-button
            >
            <el-button :disabled="!detail?.permissions?.canUpdate" @click="openExpectedDate"
              >修改预计归还日期</el-button
            >
            <el-button @click="drawerVisible = false">关闭</el-button>
          </template>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="returnVisible" title="发起归还" width="680px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="归还日期" required>
          <el-date-picker v-model="returnForm.returnDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-table :data="returnForm.items" border table-layout="auto">
          <el-table-column prop="itemName" label="物品" />
          <el-table-column label="可申请" align="right">
            <template #default="{ row }"
              >{{ quantityText(row.availableReturnQuantity) }} {{ row.unit }}</template
            >
          </el-table-column>
          <el-table-column label="本次申请">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0"
                :max="row.availableReturnQuantity"
                :precision="3"
                :step="1"
                controls-position="right"
              />
            </template>
          </el-table-column>
        </el-table>
        <el-form-item class="return-remark" label="备注">
          <el-input
            v-model="returnForm.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReturn">提交归还申请</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="expectedDateVisible"
      title="修改预计归还日期"
      width="440px"
      destroy-on-close
    >
      <el-form label-width="120px">
        <el-form-item label="预计归还日期" required>
          <el-date-picker v-model="expectedDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="expectedDateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveExpectedDate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import { Delete, Plus, Search } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import StatisticCard from '@/components/StatisticCard.vue';
import { useUserStore } from '@/stores/user';
import { formatDate, formatDateOnly } from '@/utils/date';
import {
  TRANSFER_OUTBOUND_STATUS,
  TRANSFER_RETURN_STATUS,
  TRANSFER_STATUS
} from '@/constants/storeTransfer';
import {
  addStoreTransferReturns,
  cancelStoreTransfer,
  confirmStoreTransferOutbound,
  confirmStoreTransferReturn,
  createStoreTransfer,
  getStoreTransfer,
  getStoreTransfers,
  getStoreTransferStats,
  getTransferStores,
  updateExpectedReturnDate
} from '@/api/storeTransfer';

const statusOptions = Object.freeze([
  { label: '借出中', value: TRANSFER_STATUS.BORROWING },
  { label: '部分归还', value: TRANSFER_STATUS.PART_RETURNED },
  { label: '已调平', value: TRANSFER_STATUS.RETURNED },
  { label: '已取消', value: TRANSFER_STATUS.CANCELLED }
]);

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const detailLoading = ref(false);
const saving = ref(false);
const drawerVisible = ref(false);
const returnVisible = ref(false);
const expectedDateVisible = ref(false);
const drawerMode = ref('detail');
const detail = ref(null);
const stores = ref([]);
const list = ref([]);
const dateRange = ref([]);
const expectedDate = ref('');
const createFormRef = ref(null);
const query = reactive({ keyword: '', status: '', storeId: '', pending: '', overdue: '' });
const pagination = reactive({ page: 1, pageSize: 10, total: 0 });
const stats = reactive({ borrowing: 0, partReturned: 0, pending: 0, overdue: 0 });
const createForm = reactive({});
const returnForm = reactive({ returnDate: '', remark: '', items: [] });
const createRules = {
  fromStoreId: [{ required: true, message: '请选择调出门店', trigger: 'change' }],
  toStoreId: [{ required: true, message: '请选择调入门店', trigger: 'change' }],
  transferDate: [{ required: true, message: '请选择调拨日期', trigger: 'change' }],
  expectedReturnDate: [{ required: true, message: '请选择预计归还日期', trigger: 'change' }]
};

const currentStoreName = computed(
  () => stores.value.find((item) => item.id === Number(userStore.user?.storeId))?.name || '-'
);
const destinationStores = computed(() => {
  const fromId = userStore.isSuperAdmin
    ? Number(createForm.fromStoreId)
    : Number(userStore.user?.storeId);
  return stores.value.filter((store) => store.id !== fromId);
});
const sourceStores = computed(() => {
  const toId = userStore.isSuperAdmin
    ? Number(createForm.toStoreId)
    : Number(userStore.user?.storeId);
  return stores.value.filter((store) => store.id !== toId);
});

function localDate(offsetDays = 0) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function resetCreateForm() {
  Object.assign(createForm, {
    fromStoreId: null,
    toStoreId: userStore.isSuperAdmin ? null : Number(userStore.user?.storeId),
    transferDate: localDate(),
    expectedReturnDate: localDate(7),
    remark: '',
    items: [{ itemName: '', specification: '', batchNo: '', quantity: 1, unit: '', remark: '' }]
  });
}

function operatorName(operator) {
  return operator?.nickname || operator?.name || operator?.phone || '-';
}

function statusText(status) {
  return statusOptions.find((item) => item.value === status)?.label || status || '-';
}

function statusType(status) {
  return (
    {
      [TRANSFER_STATUS.BORROWING]: 'primary',
      [TRANSFER_STATUS.PART_RETURNED]: 'warning',
      [TRANSFER_STATUS.RETURNED]: 'success',
      [TRANSFER_STATUS.CANCELLED]: 'info'
    }[status] || 'info'
  );
}

function returnStatusText(status) {
  return status === TRANSFER_RETURN_STATUS.CONFIRMED ? '已确认' : '待确认';
}

function returnStatusType(status) {
  return status === TRANSFER_RETURN_STATUS.CONFIRMED ? 'success' : 'warning';
}

function quantityText(value) {
  const number = Number(value || 0);
  return Number.isInteger(number)
    ? String(number)
    : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

function itemSummary(row) {
  const names = (row.items || []).map((item) => item.itemName);
  return names.length > 2 ? `${names.slice(0, 2).join('、')} 等` : names.join('、') || '-';
}

async function loadList() {
  loading.value = true;
  try {
    const data = await getStoreTransfers({
      ...query,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

async function loadStats() {
  Object.assign(
    stats,
    (await getStoreTransferStats({ storeId: query.storeId || undefined })) || {}
  );
}

async function reload() {
  await Promise.all([loadList(), loadStats()]);
}

function search() {
  pagination.page = 1;
  if (query.status) {
    query.pending = '';
    query.overdue = '';
  }
  reload();
}

function resetSearch() {
  Object.assign(query, { keyword: '', status: '', storeId: '', pending: '', overdue: '' });
  dateRange.value = [];
  pagination.page = 1;
  router.replace({ query: {} });
  reload();
}

function applyStatus(status) {
  query.status = status;
  query.pending = '';
  query.overdue = '';
  pagination.page = 1;
  loadList();
}

function applyPending() {
  query.status = '';
  query.pending = '1';
  query.overdue = '';
  pagination.page = 1;
  loadList();
}

function applyOverdue() {
  query.status = '';
  query.pending = '';
  query.overdue = '1';
  pagination.page = 1;
  loadList();
}

function openCreate() {
  resetCreateForm();
  drawerMode.value = 'create';
  drawerVisible.value = true;
}

async function openDetail(row) {
  drawerMode.value = 'detail';
  drawerVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = await getStoreTransfer(row.id);
  } finally {
    detailLoading.value = false;
  }
}

function resetDrawer() {
  detail.value = null;
  createFormRef.value = null;
}

function addCreateItem() {
  createForm.items.push({
    itemName: '',
    specification: '',
    batchNo: '',
    quantity: 1,
    unit: '',
    remark: ''
  });
}

function removeCreateItem(index) {
  createForm.items.splice(index, 1);
}

async function saveCreate() {
  await createFormRef.value?.validate();
  if (createForm.expectedReturnDate < createForm.transferDate) {
    ElMessage.warning('预计归还日期不能早于调拨日期');
    return;
  }
  if (createForm.items.some((item) => !item.itemName || !item.unit || Number(item.quantity) <= 0)) {
    ElMessage.warning('请完整填写每项明细的名称、数量和单位');
    return;
  }
  saving.value = true;
  try {
    await createStoreTransfer(createForm);
    ElMessage.success('调拨申请已提交，等待调出门店确认');
    drawerVisible.value = false;
    await reload();
  } finally {
    saving.value = false;
  }
}

async function confirmOutbound() {
  if (!detail.value?.permissions?.canConfirmOutbound) return;
  saving.value = true;
  try {
    detail.value = await confirmStoreTransferOutbound(detail.value.id);
    ElMessage.success('已确认调出，可由调入门店发起归还');
    await reload();
  } finally {
    saving.value = false;
  }
}

function openReturn(selectedItem) {
  if (!detail.value?.permissions?.canSubmitReturn) return;
  const available = detail.value.items.filter((item) => item.availableReturnQuantity > 0);
  Object.assign(returnForm, {
    returnDate: localDate(),
    remark: '',
    items: available.map((item) => ({
      transferItemId: item.id,
      itemName: item.itemName,
      unit: item.unit,
      availableReturnQuantity: item.availableReturnQuantity,
      quantity: selectedItem?.id === item.id ? item.availableReturnQuantity : 0
    }))
  });
  returnVisible.value = true;
}

async function saveReturn() {
  if (!returnForm.returnDate) return ElMessage.warning('请选择归还日期');
  const items = returnForm.items
    .filter((item) => Number(item.quantity) > 0)
    .map((item) => ({ transferItemId: item.transferItemId, quantity: item.quantity }));
  if (!items.length) return ElMessage.warning('请至少填写一项归还数量');
  saving.value = true;
  try {
    detail.value = await addStoreTransferReturns(detail.value.id, {
      returnDate: returnForm.returnDate,
      remark: returnForm.remark,
      items
    });
    returnVisible.value = false;
    ElMessage.success('归还申请已提交，等待调出门店确认收货');
    await reload();
  } finally {
    saving.value = false;
  }
}

async function confirmReturn(row) {
  if (!detail.value?.permissions?.canConfirmReturn || row.status !== TRANSFER_RETURN_STATUS.PENDING)
    return;
  saving.value = true;
  try {
    detail.value = await confirmStoreTransferReturn(detail.value.id, row.id);
    ElMessage.success(
      detail.value.status === TRANSFER_STATUS.RETURNED ? '已确认收货，全部物品已调平' : '已确认收货'
    );
    await reload();
  } finally {
    saving.value = false;
  }
}

function openExpectedDate() {
  expectedDate.value = formatDateOnly(detail.value.expectedReturnDate, '');
  expectedDateVisible.value = true;
}

async function saveExpectedDate() {
  if (!expectedDate.value) return ElMessage.warning('请选择预计归还日期');
  saving.value = true;
  try {
    detail.value = await updateExpectedReturnDate(detail.value.id, {
      expectedReturnDate: expectedDate.value
    });
    expectedDateVisible.value = false;
    ElMessage.success('预计归还日期已更新');
    await reload();
  } finally {
    saving.value = false;
  }
}

async function cancelTransfer() {
  const result = await ElMessageBox.prompt(
    '请输入取消原因',
    `取消调拨 ${detail.value.transferNo}`,
    {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPattern: /\S+/,
      inputErrorMessage: '请输入取消原因',
      type: 'warning'
    }
  );
  detail.value = await cancelStoreTransfer(detail.value.id, { reason: result.value });
  ElMessage.success('调拨已取消');
  await reload();
}

watch(() => [pagination.page, pagination.pageSize], loadList);
watch(
  () => createForm.fromStoreId,
  () => {
    if (createForm.toStoreId === createForm.fromStoreId) createForm.toStoreId = null;
  }
);
watch(
  () => createForm.toStoreId,
  () => {
    if (createForm.toStoreId === createForm.fromStoreId) createForm.fromStoreId = null;
  }
);

onMounted(async () => {
  stores.value = (await getTransferStores()) || [];
  const routeStatus = Number(route.query.status);
  if (route.query.status !== undefined && Object.values(TRANSFER_STATUS).includes(routeStatus)) {
    query.status = routeStatus;
  }
  if (route.query.pending === '1') query.pending = '1';
  if (route.query.overdue === '1') query.overdue = '1';
  if (userStore.isSuperAdmin && route.query.storeId) query.storeId = Number(route.query.storeId);
  resetCreateForm();
  await reload();
});
</script>

<style scoped>
.stat-grid {
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

.filters :deep(.el-input) {
  width: 260px;
}

.filters :deep(.el-select) {
  width: 170px;
}

.status-tags {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

.overdue-text {
  color: var(--el-color-danger);
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor) {
  width: 100%;
}

.section-heading,
.detail-title-row,
.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-heading {
  margin: 22px 0 12px;
  font-size: 16px;
  font-weight: 600;
}

.item-editor-list {
  display: grid;
  gap: 12px;
}

.item-editor {
  padding: 14px 16px 16px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
}

.item-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 10px;
  font-weight: 600;
}

.item-editor-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px 16px;
}

.item-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
}

.item-field-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 20px;
}

.required-label::before {
  margin-right: 4px;
  color: var(--el-color-danger);
  content: '*';
}

.item-field-name {
  grid-column: span 5;
}

.item-field-specification {
  grid-column: span 4;
}

.item-field-batch,
.item-field-quantity,
.item-field-unit {
  grid-column: span 3;
}

.item-field-remark {
  grid-column: span 6;
}

.item-field :deep(.el-input-number) {
  width: 100%;
}

.detail-content h3 {
  margin: 24px 0 12px;
  font-size: 16px;
}

.table-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 18px;
}

.table-meta span:last-child {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.detail-title-row h3 {
  margin-top: 0;
}

.footer-spacer {
  flex: 1;
}

.return-remark {
  margin-top: 18px;
}

@media (max-width: 900px) {
  .stat-grid,
  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .item-editor-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .item-field-name,
  .item-field-specification,
  .item-field-batch,
  .item-field-quantity,
  .item-field-unit {
    grid-column: span 1;
  }

  .item-field-remark {
    grid-column: span 2;
  }
}

@media (max-width: 640px) {
  .stat-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .item-editor-grid {
    grid-template-columns: 1fr;
  }

  .item-field-name,
  .item-field-specification,
  .item-field-batch,
  .item-field-quantity,
  .item-field-unit,
  .item-field-remark {
    grid-column: span 1;
  }

  .drawer-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .footer-spacer {
    display: none;
  }
}
</style>
