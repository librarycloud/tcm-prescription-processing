<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">E6数据导入</h1>
        <p class="page-subtitle">核对E6订单并确认生成处方与加工计划</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="search-form" @submit.prevent="handleSearch">
        <el-input
          v-model.trim="query.keyword"
          clearable
          placeholder="订单号、顾客、电话或医师编码"
        />
        <el-date-picker
          v-model="query.orderDate"
          type="date"
          value-format="YYYY-MM-DD"
          clearable
          placeholder="订单时间"
          @change="handleSearch"
        />
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="query.storeId"
          clearable
          placeholder="全部门店"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="query.cashierName" clearable filterable placeholder="全部操作员">
          <el-option
            v-for="item in operatorOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleShowAll">显示全部</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="list"
        :fit="true"
        border
        class="e6-import-table"
        row-key="id"
        table-layout="auto"
      >
        <template #empty><EmptyView description="暂无E6导入数据" /></template>
        <el-table-column label="订单时间">
          <template #default="{ row }">{{ formatDateSeconds(row.sourceCreatedAt) }}</template>
        </el-table-column>
        <el-table-column prop="externalOrderNo" label="E6订单号" />
        <el-table-column v-if="userStore.isSuperAdmin" prop="store.name" label="门店" />
        <el-table-column prop="customerName" label="顾客" />
        <el-table-column label="电话"
          ><template #default="{ row }">{{ maskPhone(row.phone) }}</template></el-table-column
        >
        <el-table-column label="操作员" show-overflow-tooltip>
          <template #default="{ row }">{{ row.operatorMapping?.operatorName || row.cashierName || '-' }}</template>
        </el-table-column>
        <el-table-column label="系统医生">
          <template #default="{ row }">
            <span :class="{ 'mapping-missing': !row.prescription?.doctor && !row.doctorMapping }">{{
              row.prescription?.doctor?.name || row.doctorMapping?.doctor?.name || '未映射'
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总价"
          ><template #default="{ row }">¥{{ money(row.totalPrice) }}</template></el-table-column
        >
        <el-table-column prop="doseCount" label="剂数" />
        <el-table-column label="状态">
          <template #default="{ row }"
            ><el-tag :type="statusMeta(row.status).type" effect="plain">{{
              statusMeta(row.status).label
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="同步时间">
          <template #default="{ row }">{{ formatDate(row.lastSyncedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="row.prescriptionId"
                link
                type="primary"
                @click="editPrescription(row)"
                >编辑处方</el-button
              >
              <el-button
                v-if="canConfirm(row)"
                link
                type="success"
                @click="openConfirm(row)"
                >确认</el-button
              >
              <el-button v-if="canConfirm(row)" link type="warning" @click="handleRevalidate(row)"
                >重校验</el-button
              >
              <el-button v-if="canConfirm(row)" link type="danger" @click="handleReject(row)"
                >驳回</el-button
              >
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

    <el-dialog v-model="detailVisible" title="E6导入详情" width="760px" align-center>
      <div v-if="detail" class="detail-grid">
        <div class="detail-item">
          <div class="detail-label">E6订单号</div>
          <div class="detail-value">{{ detail.externalOrderNo }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">门店</div>
          <div class="detail-value">{{ detail.store?.name }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">顾客</div>
          <div class="detail-value">{{ detail.customerName }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">手机号</div>
          <div class="detail-value">{{ maskPhone(detail.phone) }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">操作员</div>
          <div class="detail-value">{{ detail.operatorMapping?.operatorName || detail.cashierName || '-' }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">E6医师编码</div>
          <div class="detail-value">{{ detail.e6DoctorCode }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">系统医生</div>
          <div class="detail-value">{{ detail.prescription?.doctor?.name || detail.doctorMapping?.doctor?.name || '未映射' }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">总价</div>
          <div class="detail-value">¥{{ money(detail.totalPrice) }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">剂数</div>
          <div class="detail-value">{{ detail.doseCount }}</div>
        </div>
        <div class="detail-item detail-wide">
          <div class="detail-label">备注</div>
          <div class="detail-value">{{ detail.remark || '-' }}</div>
        </div>
        <div v-if="detail.errorMessage" class="detail-item detail-wide">
          <div class="detail-label">错误信息</div>
          <div class="detail-value error-text">{{ detail.errorMessage }}</div>
        </div>
      </div>
      <div class="raw-section">
        <div class="detail-label">E6原始数据</div>
        <pre>{{ rawPayloadText }}</pre>
      </div>
    </el-dialog>

    <el-dialog v-model="confirmVisible" title="确认导入并生成加工计划" width="620px" align-center>
      <el-alert v-if="selected" :closable="false" type="info" show-icon>
        <template #title
          >{{ selected.externalOrderNo }} · {{ selected.customerName }} ·
          {{ selected.doseCount }}剂</template
        >
      </el-alert>
      <el-form
        ref="confirmFormRef"
        :model="confirmForm"
        :rules="confirmRules"
        label-width="100px"
        class="confirm-form"
      >
        <el-form-item label="顾客姓名">
          <el-input v-model.trim="confirmForm.customerName" maxlength="64" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model.trim="confirmForm.phone" maxlength="11" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="系统医生" prop="doctorId">
          <el-select v-model="confirmForm.doctorId" filterable placeholder="请选择医生" style="width: 100%">
            <el-option v-for="doctor in doctors" :key="doctor.id" :label="doctor.name" :value="doctor.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="剂数" prop="doseCount">
          <el-input-number v-model="confirmForm.doseCount" :min="1" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="加工方式" prop="processTypeId">
          <el-select
            v-model="confirmForm.processTypeId"
            placeholder="请选择加工方式"
            style="width: 100%"
          >
            <el-option
              v-for="item in processTypes"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="安排方式" prop="scheduleType">
          <el-segmented v-model="confirmForm.scheduleType" :options="scheduleOptions" />
        </el-form-item>
        <el-form-item v-if="confirmForm.scheduleType === 1" label="加工日期" prop="processDate">
          <el-date-picker
            v-model="confirmForm.processDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="取货方式" prop="pickupMethod">
          <el-segmented v-model="confirmForm.pickupMethod" :options="pickupOptions" />
        </el-form-item>
        <el-form-item v-if="[1, 2].includes(Number(confirmForm.pickupMethod))" label="地址">
          <el-input
            v-model.trim="confirmForm.expressAddress"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="选填"
          />
        </el-form-item>
        <template v-if="isDecoction">
          <el-form-item label="代煎袋数" prop="bagCount"
            ><el-input-number
              v-model="confirmForm.bagCount"
              :min="1"
              :max="9999"
              style="width: 100%"
          /></el-form-item>
          <el-form-item label="每袋毫升" prop="volumeMl"
            ><el-input-number
              v-model="confirmForm.volumeMl"
              :min="1"
              :max="9999"
              style="width: 100%"
          /></el-form-item>
          <el-form-item label="服用方法"
            ><el-input v-model.trim="confirmForm.usageMethod" maxlength="200"
          /></el-form-item>
        </template>
        <el-form-item label="加工备注"
          ><el-input
            v-model.trim="confirmForm.processRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirming" @click="submitConfirm">确认生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import {
  confirmE6Import,
  getE6Import,
  getE6Imports,
  getE6OperatorMappings,
  rejectE6Import,
  revalidateE6Import
} from '@/api/e6Integration';
import { getDictionaries, getDoctors } from '@/api/processing';
import { getStores } from '@/api/store';
import {
  E6_IMPORT_STATUS,
  E6_IMPORT_STATUS_OPTIONS,
  e6ImportStatusMeta
} from '@/constants/e6Integration';
import { formatDate, formatDateSeconds } from '@/utils/date';
import { maskPhone } from '@/utils/phone';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const router = useRouter();
const loading = ref(false);
const confirming = ref(false);
const detailVisible = ref(false);
const confirmVisible = ref(false);
const confirmFormRef = ref(null);
const list = ref([]);
const stores = ref([]);
const processTypes = ref([]);
const doctors = ref([]);
const operatorOptions = ref([]);
const detail = ref(null);
const selected = ref(null);
const statusOptions = E6_IMPORT_STATUS_OPTIONS.filter(
  (item) => item.value !== E6_IMPORT_STATUS.IMPORT_PROCESSING
);
const scheduleOptions = [
  { label: '指定日期', value: 1 },
  { label: '等待通知', value: 2 }
];
const pickupOptions = [
  { label: '自提', value: 0 },
  { label: '跑腿', value: 1 },
  { label: '快递', value: 2 }
];
const query = reactive({ keyword: '', orderDate: todayText(), storeId: '', status: '', cashierName: '' });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });
const confirmForm = reactive({
  customerName: '',
  phone: '',
  doctorId: null,
  doseCount: 1,
  processTypeId: '',
  scheduleType: 1,
  processDate: todayText(),
  pickupMethod: 0,
  expressAddress: '',
  bagCount: null,
  volumeMl: null,
  usageMethod: '',
  processRemark: ''
});
const confirmRules = {
  phone: [
    {
      validator: (_rule, value, callback) =>
        !value || /^1[3-9]\d{9}$/.test(value)
          ? callback()
          : callback(new Error('请输入正确的手机号')),
      trigger: 'blur'
    }
  ],
  doctorId: [{ required: true, message: '请选择医生', trigger: 'change' }],
  doseCount: [{ required: true, message: '请输入剂数', trigger: 'change' }],
  processTypeId: [{ required: true, message: '请选择加工方式', trigger: 'change' }],
  scheduleType: [{ required: true, message: '请选择安排方式', trigger: 'change' }],
  processDate: [
    {
      validator: (_rule, value, callback) =>
        confirmForm.scheduleType === 1 && !value
          ? callback(new Error('请选择加工日期'))
          : callback(),
      trigger: 'change'
    }
  ],
  pickupMethod: [{ required: true, message: '请选择取货方式', trigger: 'change' }],
  bagCount: [
    {
      validator: (_rule, value, callback) =>
        isDecoction.value && !value ? callback(new Error('请输入代煎袋数')) : callback(),
      trigger: 'change'
    }
  ],
  volumeMl: [
    {
      validator: (_rule, value, callback) =>
        isDecoction.value && !value ? callback(new Error('请输入每袋毫升数')) : callback(),
      trigger: 'change'
    }
  ]
};

const selectedProcessType = computed(() =>
  processTypes.value.find((item) => item.id === Number(confirmForm.processTypeId))
);
const isDecoction = computed(
  () =>
    selectedProcessType.value?.code === 'DECOCTION' || selectedProcessType.value?.name === '代煎'
);
const rawPayloadText = computed(() => JSON.stringify(detail.value?.rawPayload || {}, null, 2));

function statusMeta(status) {
  return e6ImportStatusMeta(status);
}
function money(value) {
  return Number(value || 0).toFixed(2);
}
function todayText() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
function canConfirm(row) {
  return [0, 1, 2].includes(Number(row.status)) && !row.prescriptionId;
}

async function loadData() {
  loading.value = true;
  try {
    const data = await getE6Imports({
      ...query,
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

async function loadReferences() {
  const requests = [getDictionaries('ProcessType'), getDoctors()];
  if (userStore.isSuperAdmin) requests.push(getStores({ page: 1, pageSize: 100, status: 1 }));
  const [types, doctorData, storeData] = await Promise.all(requests);
  processTypes.value = types || [];
  doctors.value = doctorData || [];
  stores.value = storeData?.list || [];
  await loadOperatorOptions();
}

async function loadOperatorOptions() {
  const result = await getE6OperatorMappings(
    userStore.isSuperAdmin && query.storeId ? { storeId: query.storeId } : undefined
  );
  const mappedNames = new Map((result?.list || []).map((item) => [item.e6OperatorName, item.operatorName]));
  operatorOptions.value = (result?.operators || []).map((name) => ({
    value: name,
    label: mappedNames.get(name) || name
  }));
}

function handleSearch() {
  if (pagination.page !== 1) pagination.page = 1;
  else loadData();
}
function handleReset() {
  Object.assign(query, { keyword: '', orderDate: todayText(), storeId: '', status: '', cashierName: '' });
  handleSearch();
}
function handleShowAll() {
  query.orderDate = '';
  handleSearch();
}

async function openDetail(row) {
  detail.value = await getE6Import(row.id);
  detailVisible.value = true;
}

function editPrescription(row) {
  router.push({ name: 'Prescriptions', query: { editId: row.prescriptionId } });
}

function openConfirm(row) {
  selected.value = row;
  Object.assign(confirmForm, {
    customerName: row.customerName || '',
    phone: row.phone || '',
    doctorId: row.doctorMapping?.doctor?.id || null,
    doseCount: Number(row.doseCount) || 1,
    processTypeId: '',
    scheduleType: 1,
    processDate: todayText(),
    pickupMethod: 0,
    expressAddress: '',
    bagCount: Number(row.doseCount) * 2,
    volumeMl: 200,
    usageMethod: '',
    processRemark: ''
  });
  confirmVisible.value = true;
}

async function submitConfirm() {
  const valid = await confirmFormRef.value?.validate().catch(() => false);
  if (!valid || !selected.value) return;
  confirming.value = true;
  try {
    const payload = { ...confirmForm };
    if (payload.scheduleType === 2) payload.processDate = null;
    if (!isDecoction.value)
      Object.assign(payload, { bagCount: null, volumeMl: null, usageMethod: null });
    await confirmE6Import(selected.value.id, payload);
    ElMessage.success('已生成处方并进入加工工作台');
    confirmVisible.value = false;
    await loadData();
  } finally {
    confirming.value = false;
  }
}

async function handleRevalidate(row) {
  await revalidateE6Import(row.id);
  ElMessage.success('重新校验完成');
  await loadData();
}

async function handleReject(row) {
  const result = await ElMessageBox.prompt('请输入驳回原因', `驳回 ${row.externalOrderNo}`, {
    inputType: 'textarea',
    inputValidator: (value) => Boolean(String(value || '').trim()) || '请输入驳回原因'
  });
  await rejectE6Import(row.id, result.value);
  ElMessage.success('已驳回');
  await loadData();
}

watch(() => [pagination.page, pagination.pageSize], loadData);
watch(() => query.storeId, async () => {
  query.cashierName = '';
  await loadOperatorOptions();
});
onMounted(() => Promise.all([loadData(), loadReferences()]));
</script>

<style scoped>
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.search-form > :first-child {
  flex: 1 1 260px;
}
.search-form > :not(:first-child) {
  flex: 0 0 180px;
}
.search-form > :deep(.el-button) {
  flex-basis: auto;
}
.mapping-missing,
.error-text {
  color: var(--el-color-danger);
}
.detail-wide {
  grid-column: 1 / -1;
}
.raw-section {
  margin-top: 20px;
}
.raw-section pre {
  max-height: 260px;
  margin: 8px 0 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--app-border);
  border-radius: 4px;
  background: var(--app-bg);
  white-space: pre-wrap;
  word-break: break-word;
}
.confirm-form {
  margin-top: 18px;
}
.e6-import-table {
  width: auto;
  min-width: 100%;
}
.e6-import-table :deep(.cell),
.e6-import-table :deep(.el-table__cell) {
  min-width: 0;
}
.e6-import-table :deep(.table-actions) {
  width: auto;
  gap: 3px;
}
.e6-import-table :deep(.table-actions .el-button + .el-button) {
  margin-left: 0;
}
@media (max-width: 900px) {
  .search-form > :first-child {
    flex-basis: 100%;
  }
  .search-form > :not(:first-child) {
    flex: 1 1 180px;
  }
}
</style>
