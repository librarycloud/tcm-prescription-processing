<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">E6诊所处方导入</h1>
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
          @change="handleSearch"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="全部状态" @change="handleSearch">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="query.cashierName" clearable filterable placeholder="全部操作员" @change="handleSearch">
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
        @selection-change="handleSelectionChange"
      >
        <template #empty><EmptyView description="暂无E6诊所处方导入数据" /></template>
        <el-table-column type="selection" width="44" :selectable="canMerge" />
        <el-table-column label="订单时间">
          <template #default="{ row }">{{ formatDateSeconds(row.sourceCreatedAt) }}</template>
        </el-table-column>
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
        <el-table-column label="付款">
          <template #default="{ row }">
            <el-tag :type="Number(row.isPaid) === 1 ? 'success' : 'warning'" effect="plain">
              {{ Number(row.isPaid) === 1 ? '已付款' : '未付款' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态">
          <template #default="{ row }">
            <el-tag
              v-if="canConfirm(row)"
              class="status-confirm-tag"
              type="warning"
              effect="plain"
              @click="openConfirm(row)"
              >{{ isPlanRegeneration(row) ? '重新生成加工计划' : Number(row.status) === E6_IMPORT_STATUS.IMPORT_CONVERTED ? '重新生成' : '待确认' }}</el-tag
            >
            <el-tag v-else :type="statusMeta(row.status).type" effect="plain">{{ statusMeta(row.status).label }}</el-tag>
          </template>
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
              <el-button v-if="canConfirm(row)" link type="warning" @click="handleRevalidate(row)"
                >重校验</el-button
              >
              <el-button v-if="canConfirm(row)" link type="danger" @click="handleReject(row)"
                >驳回</el-button
              >
            </div>
          </template>
        </el-table-column>
        <el-table-column label="E6订单号">
          <template #default="{ row }">{{ row.externalOrderNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="同步时间">
          <template #default="{ row }">{{ formatDate(row.lastSyncedAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="merge-actions">
        <span>已选 {{ selectedRows.length }} 个订单</span>
        <el-button type="primary" :disabled="selectedRows.length < 2" @click="openMergeConfirm">
          合并生成处方
        </el-button>
      </div>
      <Pagination
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="E6诊所处方导入详情" direction="rtl" size="min(1180px, 96vw)" destroy-on-close>
      <div class="e6-import-detail">
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
          <div class="detail-item">
            <div class="detail-label">付款状态</div>
            <div class="detail-value">{{ Number(detail.isPaid) === 1 ? '已付款' : '未付款' }}</div>
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
        <el-alert
          v-if="detail && Number(detail.status) === E6_IMPORT_STATUS.IMPORT_CONFLICT"
          class="conflict-alert"
          type="error"
          :closable="false"
          title="数据冲突"
          :description="conflictSummary(detail)"
          show-icon
        />
        <div v-if="detail?.prescription?.plans?.length" class="raw-section">
          <div class="detail-label">加工计划批次</div>
          <el-table :data="detail.prescription.plans" border size="small">
            <el-table-column label="批次" width="80">
              <template #default="{ row }">第 {{ row.batchNo }} 批</template>
            </el-table-column>
            <el-table-column prop="totalDose" label="剂数" width="90" />
            <el-table-column label="安排方式" width="110">
              <template #default="{ row }">{{ Number(row.scheduleType) === 2 ? '等待通知' : '指定日期' }}</template>
            </el-table-column>
            <el-table-column label="加工日期">
              <template #default="{ row }">{{ row.processDate ? formatDate(row.processDate) : '-' }}</template>
            </el-table-column>
            <el-table-column label="状态">
              <template #default="{ row }">{{ row.deletedAt ? '已删除' : '有效' }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="detailItems.length" class="raw-section">
          <div class="detail-label">处方明细</div>
          <el-table :data="detailItems" border size="small">
            <el-table-column prop="sequence" label="顺序" width="80" />
            <el-table-column prop="name" label="商品名称" />
            <el-table-column prop="doseCount" label="剂数" width="90" />
            <el-table-column label="单剂量">
              <template #default="{ row }">{{ row.quantity }}{{ row.unit }}</template>
            </el-table-column>
            <el-table-column label="总量">
              <template #default="{ row }">{{ row.totalQuantity }}{{ row.unit }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="raw-section">
          <div class="detail-label">E6原始数据</div>
          <pre>{{ rawPayloadText }}</pre>
        </div>
      </div>
    </el-drawer>

    <el-drawer
      v-model="confirmVisible"
      title="确认导入并生成加工计划"
      direction="rtl"
      size="min(900px, 96vw)"
      destroy-on-close
    >
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
        <el-divider class="confirm-section-divider" content-position="left">订单信息</el-divider>
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
        <el-form-item label="批次数">
          <el-input-number
            v-model="confirmForm.batchCount"
            :min="1"
            :max="Math.max(1, Number(confirmForm.doseCount) || 1)"
            style="width: 100%"
            @change="setBatchCount"
          />
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
        <div class="batch-section">
          <el-divider class="confirm-section-divider" content-position="left">加工批次</el-divider>
          <div class="batch-editor">
            <div v-for="(batch, index) in confirmForm.batches" :key="batch.key" class="batch-row">
              <div class="batch-row-header">
                <span>第 {{ index + 1 }} 批</span>
                <el-button
                  v-if="confirmForm.batches.length > 1"
                  link
                  type="danger"
                  @click="removeBatch(index)"
                >删除</el-button>
              </div>
              <div class="batch-row-fields">
                <el-input-number
                  v-model="batch.totalDose"
                  :min="1"
                  :max="9999"
                  controls-position="right"
                  @change="syncBatchDates"
                />
                <span class="batch-unit">剂</span>
                <el-segmented
                  v-model="batch.scheduleType"
                  :options="scheduleOptions"
                  @change="handleBatchScheduleChange(batch)"
                />
                <el-date-picker
                  v-if="batch.scheduleType === 1"
                  v-model="batch.processDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  @change="handleBatchDateChange(batch)"
                />
                <span v-if="isDecoction" class="batch-bag-hint">本批 {{ batchBagCount(batch) }} 袋</span>
              </div>
            </div>
            <div class="batch-editor-footer">
              <span :class="{ 'batch-total-error': batchDoseTotal !== Number(confirmForm.doseCount) }">
                已分配 {{ batchDoseTotal }} / {{ confirmForm.doseCount }} 剂
              </span>
              <el-button link type="primary" @click="addBatch">新增批次</el-button>
            </div>
          </div>
        </div>
        <el-divider class="confirm-section-divider" content-position="left">取货与备注</el-divider>
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
          <el-form-item label="每剂袋数" prop="bagsPerDose"
            ><el-input-number
              v-model="confirmForm.bagsPerDose"
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
          <el-form-item label="服用方法" class="confirm-form-wide">
            <UsageMethodInput v-model="confirmForm.usageMethod" />
          </el-form-item>
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
    </el-drawer>
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
import UsageMethodInput from '@/components/UsageMethodInput.vue';
import {
  confirmE6Import,
  getE6Import,
  getE6Imports,
  getE6OperatorMappings,
  mergeE6Imports,
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
import { datePlusDays, splitDoseBatches } from '@/utils/processingBatches';

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
const selectedRows = ref([]);
const isMerging = ref(false);
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
  batchCount: 1,
  processTypeId: '',
  batches: [],
  pickupMethod: 0,
  expressAddress: '',
  bagCount: null,
  bagsPerDose: 2,
  volumeMl: null,
  usageMethod: '',
  processRemark: ''
});
let batchKey = 0;
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
  pickupMethod: [{ required: true, message: '请选择取货方式', trigger: 'change' }],
  bagsPerDose: [
    {
      validator: (_rule, value, callback) =>
        isDecoction.value && !value ? callback(new Error('请输入每剂袋数')) : callback(),
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
const detailItems = computed(() => {
  const items = detail.value?.rawPayload?.items;
  return Array.isArray(items)
    ? [...items].sort((left, right) => Number(left.sequence) - Number(right.sequence))
    : [];
});
const batchDoseTotal = computed(() =>
  confirmForm.batches.reduce((sum, batch) => sum + Number(batch.totalDose || 0), 0)
);

function statusMeta(status) {
  return e6ImportStatusMeta(status);
}
function money(value) {
  return Number(value || 0).toFixed(2);
}
function sameText(left, right) {
  return String(left ?? '').trim() === String(right ?? '').trim();
}
function sameMoney(left, right) {
  if (left == null && right == null) return true;
  return Number(left || 0).toFixed(2) === Number(right || 0).toFixed(2);
}
function conflictSummary(row) {
  const fields = [];
  const prescription = row.prescription;
  const plan = row.processingPlan;
  if (!prescription) return '关联处方已删除，无法比对原处方数据；请按最新 E6 数据重新生成。';
  if (!sameText(row.customerName, prescription.customerName)) fields.push('顾客姓名');
  if (!sameText(row.phone, prescription.phone)) fields.push('电话');
  if (!sameMoney(row.totalPrice, prescription.totalPrice)) fields.push('总价');
  if (!sameText(row.remark, prescription.remark)) fields.push('处方备注');
  if (plan && Number(row.doseCount) !== Number(plan.totalDose)) fields.push('剂数');
  if (plan && Number(row.isPaid) !== Number(plan.paymentStatus)) fields.push('付款状态');
  if (plan && !sameText(row.remark, plan.processRemark) && sameText(row.remark, prescription.remark)) {
    fields.push('加工备注');
  }
  return fields.length ? `发生变化：${fields.join('、')}` : '可能是 E6 药材明细或其他字段发生变化，请打开详情核对。';
}
function todayText() {
  const date = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
function createBatch(totalDose, processDate = todayText(), scheduleType = 1) {
  return {
    key: ++batchKey,
    totalDose: Number(totalDose) || 1,
    scheduleType,
    processDate: scheduleType === 1 ? processDate : null,
    autoDate: true
  };
}
function batchBagCount(batch) {
  return Number(batch?.totalDose || 0) * Number(confirmForm.bagsPerDose || 0);
}
function setBatchCount(value = confirmForm.batchCount) {
  const count = Math.max(1, Math.min(Number(value) || 1, Number(confirmForm.doseCount) || 1));
  confirmForm.batchCount = count;
  confirmForm.batches = splitDoseBatches(confirmForm.doseCount, count, todayText())
    .map((batch) => createBatch(batch.totalDose, batch.processDate));
  syncBatchDates();
}
function syncBatchDates() {
  let cursorDate = todayText();
  confirmForm.batches.forEach((batch, index) => {
    if (index === 0 && batch.autoDate && batch.scheduleType === 1) {
      batch.processDate = cursorDate;
    }
    if (batch.scheduleType === 2) {
      batch.processDate = null;
    } else if (batch.autoDate) {
      batch.processDate = cursorDate;
    }
    const effectiveDate = batch.processDate || cursorDate;
    cursorDate = datePlusDays(effectiveDate, batch.totalDose);
  });
}
function handleBatchScheduleChange(batch) {
  if (batch.scheduleType === 2) {
    batch.processDate = null;
    batch.autoDate = true;
  } else {
    batch.autoDate = true;
    syncBatchDates();
  }
}
function handleBatchDateChange(batch) {
  batch.autoDate = false;
  syncBatchDates();
}
function addBatch() {
  const remaining = Math.max(Number(confirmForm.doseCount || 0) - batchDoseTotal.value, 0);
  confirmForm.batches.push(createBatch(remaining || 1));
  confirmForm.batchCount = confirmForm.batches.length;
  syncBatchDates();
}
function removeBatch(index) {
  if (confirmForm.batches.length <= 1) return;
  confirmForm.batches.splice(index, 1);
  confirmForm.batchCount = confirmForm.batches.length;
  syncBatchDates();
}
function canConfirm(row) {
  return (
    ([0, 1, 2].includes(Number(row.status)) && !row.prescriptionId) ||
    ([E6_IMPORT_STATUS.IMPORT_CONVERTED, E6_IMPORT_STATUS.IMPORT_CONFLICT].includes(Number(row.status)) &&
      (!row.prescriptionId || !row.processingPlanId || row.processingPlan?.deletedAt))
  );
}
function isPlanRegeneration(row) {
  return (
    [E6_IMPORT_STATUS.IMPORT_CONVERTED, E6_IMPORT_STATUS.IMPORT_CONFLICT].includes(Number(row.status)) &&
    Boolean(row.prescriptionId) &&
    (!row.processingPlanId || row.processingPlan?.deletedAt)
  );
}
function canMerge(row) {
  return [0, 1, 2].includes(Number(row.status)) && !row.prescriptionId;
}
function handleSelectionChange(rows) {
  selectedRows.value = rows;
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
  isMerging.value = false;
  Object.assign(confirmForm, {
    customerName: row.customerName || '',
    phone: row.phone || '',
    doctorId: row.doctorMapping?.doctor?.id || null,
    doseCount: Number(row.doseCount) || 1,
    batchCount: 1,
    processTypeId: '',
    batches: [createBatch(Number(row.doseCount) || 1)],
    pickupMethod: 0,
    expressAddress: '',
    bagCount: null,
    bagsPerDose: 2,
    volumeMl: 200,
    usageMethod: '',
    processRemark: ''
  });
  confirmVisible.value = true;
}

function openMergeConfirm() {
  if (selectedRows.value.length < 2) return;
  const rows = selectedRows.value;
  const storeId = rows[0].storeId;
  const doseCount = Number(rows[0].doseCount);
  if (rows.some((row) => row.storeId !== storeId)) {
    ElMessage.error('只能合并同一门店的订单');
    return;
  }
  if (rows.some((row) => Number(row.doseCount) !== doseCount)) {
    ElMessage.error('合并订单的剂数必须一致');
    return;
  }
  selected.value = rows[0];
  isMerging.value = true;
  Object.assign(confirmForm, {
    customerName: rows[0].customerName || '',
    phone: rows[0].phone || '',
    doctorId: rows[0].doctorMapping?.doctor?.id || null,
    doseCount,
    batchCount: 1,
    processTypeId: '',
    batches: [createBatch(doseCount)],
    pickupMethod: 0,
    expressAddress: '',
    bagCount: null,
    bagsPerDose: 2,
    volumeMl: 200,
    usageMethod: '',
    processRemark: ''
  });
  confirmVisible.value = true;
}

async function submitConfirm() {
  const valid = await confirmFormRef.value?.validate().catch(() => false);
  if (!valid || !selected.value) return;
  if (batchDoseTotal.value !== Number(confirmForm.doseCount)) {
    ElMessage.error(`分批剂数合计必须等于 ${confirmForm.doseCount} 剂`);
    return;
  }
  if (confirmForm.batches.some((batch) => Number(batch.totalDose) < 1)) {
    ElMessage.error('每批剂数必须大于 0');
    return;
  }
  if (confirmForm.batches.some((batch) => Number(batch.scheduleType) === 1 && !batch.processDate)) {
    ElMessage.error('指定日期批次请选择加工日期');
    return;
  }
  confirming.value = true;
  try {
    const payload = { ...confirmForm };
    payload.batches = confirmForm.batches.map((batch) => ({
      ...batch,
      totalDose: Number(batch.totalDose),
      scheduleType: Number(batch.scheduleType),
      processDate: Number(batch.scheduleType) === 2 ? null : batch.processDate
    }));
    payload.batches = payload.batches.map((batch) => ({
      ...batch,
      bagCount: isDecoction.value ? Number(batch.totalDose) * Number(payload.bagsPerDose || 0) : null
    }));
    payload.scheduleType = payload.batches[0]?.scheduleType;
    payload.processDate = payload.batches[0]?.processDate || null;
    if (!isDecoction.value)
      Object.assign(payload, { bagCount: null, bagsPerDose: null, volumeMl: null, usageMethod: null });
    if (isMerging.value) {
      await mergeE6Imports({ ...payload, ids: selectedRows.value.map((row) => row.id) });
      ElMessage.success('已合并生成处方并进入加工工作台');
    } else {
      await confirmE6Import(selected.value.id, payload);
      ElMessage.success('已生成处方并进入加工工作台');
    }
    confirmVisible.value = false;
    isMerging.value = false;
    selectedRows.value = [];
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
.e6-import-table :deep(.cell) {
  padding-right: 4px;
  padding-left: 4px;
  white-space: nowrap;
}
.mapping-missing,
.error-text {
  color: var(--el-color-danger);
}
.detail-wide {
  grid-column: 1 / -1;
}
.e6-import-detail .detail-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
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
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 16px;
}
.confirm-form > :deep(.el-divider),
.confirm-form > .batch-section,
.confirm-form > .confirm-form-wide,
.confirm-form > :deep(.el-form-item:last-child) {
  grid-column: 1 / -1;
}
.confirm-section-divider {
  margin: 8px 0 14px;
}
.batch-section {
  min-width: 0;
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
.status-confirm-tag {
  cursor: pointer;
  white-space: nowrap;
}
.merge-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.batch-editor {
  width: 100%;
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}
.batch-row {
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: 4px;
}
.batch-row + .batch-row {
  margin-top: 8px;
}
.batch-row-header,
.batch-editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.batch-row-fields {
  display: grid;
  grid-template-columns: 150px 24px minmax(160px, 1fr) minmax(150px, 1fr);
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.batch-row-fields :deep(.el-input-number),
.batch-row-fields :deep(.el-date-editor) {
  width: 100%;
}
.batch-unit {
  color: var(--el-text-color-secondary);
}
.batch-bag-hint {
  grid-column: 1 / -1;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.batch-total-error {
  color: var(--el-color-danger);
}
@media (max-width: 900px) {
  .search-form > :first-child {
    flex-basis: 100%;
  }
  .search-form > :not(:first-child) {
    flex: 1 1 180px;
  }
  .e6-import-detail .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .confirm-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .e6-import-detail .detail-grid {
    grid-template-columns: 1fr;
  }
  .confirm-form {
    display: block;
  }
  .batch-row-fields {
    grid-template-columns: 1fr 24px;
  }
  .batch-row-fields :deep(.el-segmented),
  .batch-row-fields :deep(.el-date-editor) {
    grid-column: 1 / -1;
  }
}
</style>
