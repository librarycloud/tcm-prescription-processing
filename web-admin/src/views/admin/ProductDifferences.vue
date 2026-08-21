<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ activeTab === 'current' ? '库存差异' : '差异流水' }}</h1>
        <p class="page-subtitle">
          {{
            activeTab === 'current'
              ? '只记录实货与系统库存的临时差异，不执行正常入库或销库'
              : '查询库存差异的登记、销账、导入调整与冲销记录'
          }}
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Goods" @click="router.push('/admin/products')">货品项目</el-button>
        <el-button
          :icon="activeTab === 'current' ? Document : ArrowLeft"
          @click="toggleDifferenceView"
        >
          {{ activeTab === 'current' ? '差异流水' : '返回当前差异' }}
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openRegister">登记差异</el-button>
      </div>
    </div>

    <div v-if="activeTab === 'current'" class="summary-band">
      <button
        type="button"
        :class="{ active: currentQuery.direction === '' }"
        @click="setDirection('')"
      >
        <span>有差异货品</span><strong>{{ stats.total }}</strong>
      </button>
      <button
        type="button"
        :class="{ active: currentQuery.direction === 'MORE' }"
        @click="setDirection('MORE')"
      >
        <span>实货多</span><strong class="diff-more">{{ stats.more }}</strong>
      </button>
      <button
        type="button"
        :class="{ active: currentQuery.direction === 'LESS' }"
        @click="setDirection('LESS')"
      >
        <span>实货少</span><strong class="diff-less">{{ stats.less }}</strong>
      </button>
    </div>

    <el-card v-if="activeTab === 'current'" shadow="never">
      <el-form class="filters" @submit.prevent="searchCurrent">
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="currentQuery.storeId"
          clearable
          placeholder="全部门店"
          @change="storeFilterChanged"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <el-input
          v-model.trim="currentQuery.keyword"
          clearable
          placeholder="商品编号、名称或规格"
        />
        <el-checkbox v-model="includeBalanced" @change="searchCurrent">包含已对平</el-checkbox>
        <el-button type="primary" :icon="Search" @click="searchCurrent">查询</el-button>
        <el-button :icon="Refresh" @click="resetCurrent">重置</el-button>
      </el-form>

      <el-table
        v-loading="currentLoading"
        :data="currentList"
        row-key="id"
        border
        table-layout="auto"
      >
        <template #empty><EmptyView description="暂无库存差异" /></template>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="productCode" label="商品编号" />
        <el-table-column prop="name" label="商品名称" />
        <el-table-column label="规格">
          <template #default="{ row }">{{ row.specification || '-' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" align="center" />
        <el-table-column label="零售价" align="right">
          <template #default="{ row }">{{ Number(row.retailPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="当前差异" align="right">
          <template #default="{ row }">
            <strong :class="diffClass(row.diffQuantity)">{{
              signedQuantity(row.diffQuantity)
            }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="差异说明">
          <template #default="{ row }">{{ differenceText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                link
                type="primary"
                :icon="CircleCheck"
                :disabled="Number(row.diffQuantity) === 0"
                @click="openWriteOff(row)"
                >销账</el-button
              >
              <el-button link type="primary" :icon="Document" @click="showProductLogs(row)"
                >流水</el-button
              >
            </div>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="currentPagination.page"
        v-model:page-size="currentPagination.pageSize"
        :total="currentPagination.total"
      />
    </el-card>

    <el-card v-else shadow="never">
      <el-form class="filters log-filters" @submit.prevent="searchLogs">
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="logQuery.storeId"
          clearable
          placeholder="全部门店"
          @change="searchLogs"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <el-input
          v-model.trim="logQuery.keyword"
          clearable
          placeholder="单号、商品、供货商、经办人"
        />
        <el-select v-model="logQuery.operationType" clearable placeholder="全部类型" @change="searchLogs">
          <el-option
            v-for="item in operationOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-date-picker
          v-model="logDateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="searchLogs"
        />
        <el-button type="primary" :icon="Search" @click="searchLogs">查询</el-button>
        <el-button :icon="Refresh" @click="resetLogs">重置</el-button>
      </el-form>

      <el-table v-loading="logsLoading" :data="logs" row-key="id" border table-layout="auto">
        <template #empty><EmptyView description="暂无差异流水" /></template>
        <el-table-column prop="operationNo" label="操作单号" />
        <el-table-column label="业务日期" align="center">
          <template #default="{ row }">{{ dateText(row.businessDate) }}</template>
        </el-table-column>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作类型">
          <template #default="{ row }">
            <el-tag :type="operationTag(row.operationType)" effect="plain">
              {{ operationText(row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品">
          <template #default="{ row }"
            >{{ row.product?.productCode }} {{ row.product?.name }}</template
          >
        </el-table-column>
        <el-table-column label="规格">
          <template #default="{ row }">{{ row.product?.specification || '-' }}</template>
        </el-table-column>
        <el-table-column label="数量变化" align="right">
          <template #default="{ row }">
            <strong :class="diffClass(row.changeQuantity)">{{
              signedQuantity(row.changeQuantity)
            }}</strong>
            {{ row.product?.unit }}
          </template>
        </el-table-column>
        <el-table-column label="操作后差异" align="right">
          <template #default="{ row }"
            >{{ signedQuantity(row.balanceAfter) }} {{ row.product?.unit }}</template
          >
        </el-table-column>
        <el-table-column label="供货商/经办人">
          <template #default="{ row }">
            {{ [row.supplierName, row.borrowerName].filter(Boolean).join(' / ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="批号/单据号">
          <template #default="{ row }">
            {{ [row.batchNote, row.systemDocumentNo].filter(Boolean).join(' / ') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="创建人">
          <template #default="{ row }">{{ operatorName(row.creator) }}</template>
        </el-table-column>
        <el-table-column label="备注" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              :icon="RefreshLeft"
              :disabled="row.operationType === 'REVERSAL' || Boolean(row.childLogs?.length)"
              @click="reverseLog(row)"
              >冲销</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="logPagination.page"
        v-model:page-size="logPagination.pageSize"
        :total="logPagination.total"
      />
    </el-card>

    <el-drawer
      v-model="registerVisible"
      title="登记库存差异"
      size="min(760px, 100%)"
      destroy-on-close
    >
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
      >
        <div class="form-grid">
          <el-form-item v-if="userStore.isSuperAdmin" label="所属门店" prop="storeId">
            <el-select
              v-model="registerForm.storeId"
              placeholder="请选择门店"
              @change="registerStoreChanged"
            >
              <el-option
                v-for="store in stores"
                :key="store.id"
                :label="store.name"
                :value="store.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="业务类型" prop="operationType">
            <el-segmented v-model="registerForm.operationType" :options="registerTypes" />
          </el-form-item>
          <el-form-item label="发生日期" prop="businessDate">
            <el-date-picker
              v-model="registerForm.businessDate"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="供货商">
            <el-input v-model.trim="registerForm.supplierName" maxlength="120" />
          </el-form-item>
          <el-form-item label="借货人/经办人">
            <el-input v-model.trim="registerForm.borrowerName" maxlength="100" />
          </el-form-item>
        </div>

        <div class="section-heading">
          <span>商品明细</span>
          <el-button :icon="Plus" @click="addRegisterItem">添加商品</el-button>
        </div>
        <div class="item-list">
          <div v-for="(item, index) in registerForm.items" :key="item.key" class="item-row">
            <div class="item-title">
              <strong>商品 {{ index + 1 }}</strong>
              <el-button
                v-if="registerForm.items.length > 1"
                link
                type="danger"
                :icon="Delete"
                @click="registerForm.items.splice(index, 1)"
                >删除</el-button
              >
            </div>
            <div class="item-grid">
              <label class="item-product">
                <span>商品</span>
                <el-select
                  v-model="item.productId"
                  filterable
                  remote
                  :remote-method="searchProductOptions"
                  :loading="productOptionsLoading"
                  placeholder="输入编号、名称或规格"
                  @change="productSelected(item)"
                >
                  <el-option
                    v-for="product in productOptions"
                    :key="product.id"
                    :label="`${product.productCode} · ${product.name} · ${product.specification || '无规格'}`"
                    :value="product.id"
                    :disabled="
                      selectedProductIds.includes(product.id) && item.productId !== product.id
                    "
                  />
                </el-select>
              </label>
              <label>
                <span>单位</span>
                <el-input :model-value="item.product?.unit || '-'" disabled />
              </label>
              <label>
                <span>零售价</span>
                <el-input
                  :model-value="
                    item.product ? `¥ ${Number(item.product.retailPrice || 0).toFixed(2)}` : '-'
                  "
                  disabled
                />
              </label>
              <label>
                <span>数量</span>
                <el-input-number
                  v-model="item.quantity"
                  :min="0.001"
                  :precision="3"
                  controls-position="right"
                />
              </label>
              <label class="item-batch">
                <span>批号/备注</span>
                <el-input v-model.trim="item.batchNote" maxlength="120" />
              </label>
            </div>
            <div v-if="item.product" class="impact-preview">
              当前差异 {{ signedQuantity(item.product.diffQuantity) }}
              {{ item.product.unit }}，登记后
              <strong :class="diffClass(nextDifference(item))">
                {{ signedQuantity(nextDifference(item)) }} {{ item.product.unit }}
              </strong>
            </div>
          </div>
        </div>
        <el-form-item label="备注" class="register-remark">
          <el-input
            v-model.trim="registerForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRegister">确认登记</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="writeOffVisible" title="库存差异销账" width="520px" align-center>
      <el-alert
        v-if="writeOffProduct"
        :title="writeOffTitle"
        :description="`当前差异 ${signedQuantity(writeOffProduct.diffQuantity)} ${writeOffProduct.unit}`"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form
        ref="writeOffFormRef"
        :model="writeOffForm"
        :rules="writeOffRules"
        label-width="90px"
        class="write-off-form"
      >
        <el-form-item label="销账日期" prop="businessDate">
          <el-date-picker
            v-model="writeOffForm.businessDate"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="销账数量" prop="quantity">
          <el-input-number
            v-model="writeOffForm.quantity"
            :min="0.001"
            :max="Math.abs(Number(writeOffProduct?.diffQuantity || 0))"
            :precision="3"
            controls-position="right"
          />
          <span class="unit-text">{{ writeOffProduct?.unit }}</span>
        </el-form-item>
        <el-form-item label="系统单据号">
          <el-input v-model.trim="writeOffForm.systemDocumentNo" maxlength="100" />
        </el-form-item>
        <el-form-item label="经办人">
          <el-input v-model.trim="writeOffForm.borrowerName" maxlength="100" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model.trim="writeOffForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <div v-if="writeOffProduct" class="write-off-preview">
          销账后差异：
          <strong :class="diffClass(writeOffBalance)">
            {{ signedQuantity(writeOffBalance) }} {{ writeOffProduct.unit }}
          </strong>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="writeOffVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveWriteOff">确认销账</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import {
  ArrowLeft,
  CircleCheck,
  Delete,
  Document,
  Goods,
  Plus,
  Refresh,
  RefreshLeft,
  Search
} from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { useUserStore } from '@/stores/user';
import {
  getProductDiffLogs,
  getProductDiffStats,
  getProducts,
  getProductStores,
  registerProductDifference,
  reverseProductDiffLog,
  writeOffProductDifference
} from '@/api/productDifference';

const router = useRouter();
const userStore = useUserStore();
const activeTab = ref('current');
const stores = ref([]);
const stats = reactive({ total: 0, more: 0, less: 0 });
const currentList = ref([]);
const currentLoading = ref(false);
const includeBalanced = ref(false);
const currentQuery = reactive({ storeId: '', keyword: '', direction: '' });
const currentPagination = reactive({ page: 1, pageSize: 20, total: 0 });
const logs = ref([]);
const logsLoading = ref(false);
const logQuery = reactive({ storeId: '', keyword: '', operationType: '', productId: '' });
const logDateRange = ref([]);
const logPagination = reactive({ page: 1, pageSize: 20, total: 0 });
const saving = ref(false);

const operationOptions = [
  { value: 'PRE_RECEIPT', label: '先到货未入库' },
  { value: 'PRE_SHIPMENT', label: '先出货未销库' },
  { value: 'WRITE_OFF_RECEIPT', label: '入库销账' },
  { value: 'WRITE_OFF_SHIPMENT', label: '销库销账' },
  { value: 'IMPORT_OPENING', label: '导入期初差异' },
  { value: 'IMPORT_ADJUSTMENT', label: '导入调整' },
  { value: 'REVERSAL', label: '冲销' }
];
const registerTypes = [
  { value: 'PRE_RECEIPT', label: '先到货未入库' },
  { value: 'PRE_SHIPMENT', label: '先出货未销库' }
];

function today() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function quantityText(value) {
  const number = Number(value || 0);
  return Number.isInteger(number)
    ? String(number)
    : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

function signedQuantity(value) {
  const number = Number(value || 0);
  return number > 0 ? `+${quantityText(number)}` : quantityText(number);
}

function diffClass(value) {
  const number = Number(value || 0);
  return number > 0 ? 'diff-more' : number < 0 ? 'diff-less' : 'diff-zero';
}

function differenceText(row) {
  const amount = Math.abs(Number(row.diffQuantity || 0));
  if (amount === 0) return '已对平';
  return `实货${Number(row.diffQuantity) > 0 ? '多' : '少'} ${quantityText(amount)} ${row.unit}`;
}

function dateText(value) {
  return String(value || '').slice(0, 10) || '-';
}

function operationText(value) {
  return operationOptions.find((item) => item.value === value)?.label || value || '-';
}

function operationTag(value) {
  if (value === 'REVERSAL') return 'info';
  if (value.includes('WRITE_OFF')) return 'success';
  if (value === 'PRE_SHIPMENT') return 'danger';
  if (value.includes('IMPORT')) return 'primary';
  return 'warning';
}

function operatorName(user) {
  return user?.name || user?.nickname || user?.phone || '-';
}

async function loadCurrent() {
  currentLoading.value = true;
  try {
    const data = await getProducts({
      ...currentQuery,
      includeDisabled: '1',
      onlyDifference: includeBalanced.value ? undefined : '1',
      page: currentPagination.page,
      pageSize: currentPagination.pageSize
    });
    currentList.value = data?.list || [];
    currentPagination.total = data?.pagination?.total || 0;
  } finally {
    currentLoading.value = false;
  }
}

async function loadStats() {
  Object.assign(stats, await getProductDiffStats({ storeId: currentQuery.storeId || undefined }));
}

async function loadLogs() {
  logsLoading.value = true;
  try {
    const data = await getProductDiffLogs({
      ...logQuery,
      startDate: logDateRange.value?.[0] || undefined,
      endDate: logDateRange.value?.[1] || undefined,
      page: logPagination.page,
      pageSize: logPagination.pageSize
    });
    logs.value = data?.list || [];
    logPagination.total = data?.pagination?.total || 0;
  } finally {
    logsLoading.value = false;
  }
}

function searchCurrent() {
  if (currentPagination.page !== 1) currentPagination.page = 1;
  else Promise.all([loadCurrent(), loadStats()]);
}

function resetCurrent() {
  Object.assign(currentQuery, { storeId: '', keyword: '', direction: '' });
  includeBalanced.value = false;
  searchCurrent();
}

function setDirection(direction) {
  currentQuery.direction = direction;
  includeBalanced.value = false;
  searchCurrent();
}

function storeFilterChanged() {
  searchCurrent();
}

function searchLogs() {
  if (logPagination.page !== 1) logPagination.page = 1;
  else loadLogs();
}

function resetLogs() {
  Object.assign(logQuery, { storeId: '', keyword: '', operationType: '', productId: '' });
  logDateRange.value = [];
  searchLogs();
}

function toggleDifferenceView() {
  if (activeTab.value === 'current') {
    activeTab.value = 'logs';
    Object.assign(logQuery, {
      storeId: currentQuery.storeId,
      productId: '',
      keyword: '',
      operationType: ''
    });
    logPagination.page = 1;
    loadLogs();
    return;
  }
  activeTab.value = 'current';
  Promise.all([loadCurrent(), loadStats()]);
}

function showProductLogs(row) {
  activeTab.value = 'logs';
  Object.assign(logQuery, {
    storeId: row.storeId,
    productId: row.id,
    keyword: '',
    operationType: ''
  });
  logPagination.page = 1;
  loadLogs();
}

const registerVisible = ref(false);
const registerFormRef = ref(null);
const productOptions = ref([]);
const productOptionsLoading = ref(false);
let itemKey = 0;
const newItem = () => ({
  key: ++itemKey,
  productId: null,
  product: null,
  quantity: 1,
  batchNote: ''
});
const registerForm = reactive({
  storeId: null,
  operationType: 'PRE_RECEIPT',
  businessDate: today(),
  supplierName: '',
  borrowerName: '',
  remark: '',
  items: [newItem()]
});
const registerRules = {
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
  operationType: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
  businessDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }]
};
const selectedProductIds = computed(() =>
  registerForm.items.map((item) => item.productId).filter(Boolean)
);

async function loadProductOptions(keyword = '') {
  if (!registerForm.storeId) {
    productOptions.value = [];
    return;
  }
  productOptionsLoading.value = true;
  try {
    const data = await getProducts({
      storeId: registerForm.storeId,
      keyword,
      page: 1,
      pageSize: 200
    });
    productOptions.value = data?.list || [];
  } finally {
    productOptionsLoading.value = false;
  }
}

function searchProductOptions(keyword) {
  loadProductOptions(keyword);
}

function resetRegister() {
  Object.assign(registerForm, {
    storeId: userStore.isSuperAdmin
      ? currentQuery.storeId || null
      : Number(userStore.user?.storeId),
    operationType: 'PRE_RECEIPT',
    businessDate: today(),
    supplierName: '',
    borrowerName: '',
    remark: '',
    items: [newItem()]
  });
}

async function openRegister() {
  resetRegister();
  registerVisible.value = true;
  await loadProductOptions();
}

function registerStoreChanged() {
  registerForm.items = [newItem()];
  loadProductOptions();
}

function addRegisterItem() {
  registerForm.items.push(newItem());
}

function productSelected(item) {
  item.product = productOptions.value.find((product) => product.id === item.productId) || null;
}

function nextDifference(item) {
  const current = Number(item.product?.diffQuantity || 0);
  const amount = Number(item.quantity || 0);
  return current + (registerForm.operationType === 'PRE_RECEIPT' ? amount : -amount);
}

async function saveRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (registerForm.items.some((item) => !item.productId || Number(item.quantity) <= 0))
    return ElMessage.warning('请完整选择商品并填写数量');
  if (new Set(registerForm.items.map((item) => item.productId)).size !== registerForm.items.length)
    return ElMessage.warning('同一次登记不能重复选择商品');
  saving.value = true;
  try {
    await registerProductDifference({
      ...registerForm,
      items: registerForm.items.map((item) => ({
        productId: item.productId,
        quantity: item.quantity,
        batchNote: item.batchNote
      }))
    });
    ElMessage.success('库存差异已登记');
    registerVisible.value = false;
    await Promise.all([
      loadCurrent(),
      loadStats(),
      ...(activeTab.value === 'logs' ? [loadLogs()] : [])
    ]);
  } finally {
    saving.value = false;
  }
}

const writeOffVisible = ref(false);
const writeOffProduct = ref(null);
const writeOffFormRef = ref(null);
const writeOffForm = reactive({
  storeId: null,
  productId: null,
  businessDate: today(),
  quantity: 1,
  systemDocumentNo: '',
  borrowerName: '',
  remark: ''
});
const writeOffRules = {
  businessDate: [{ required: true, message: '请选择销账日期', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入销账数量', trigger: 'change' }]
};
const writeOffTitle = computed(() =>
  Number(writeOffProduct.value?.diffQuantity || 0) > 0 ? '入库销账' : '销库销账'
);
const writeOffBalance = computed(() => {
  const current = Number(writeOffProduct.value?.diffQuantity || 0);
  const amount = Number(writeOffForm.quantity || 0);
  return current > 0 ? current - amount : current + amount;
});

function openWriteOff(row) {
  writeOffProduct.value = row;
  Object.assign(writeOffForm, {
    storeId: row.storeId,
    productId: row.id,
    businessDate: today(),
    quantity: Math.abs(Number(row.diffQuantity)),
    systemDocumentNo: '',
    borrowerName: '',
    remark: ''
  });
  writeOffVisible.value = true;
}

async function saveWriteOff() {
  const valid = await writeOffFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    await writeOffProductDifference(writeOffForm);
    ElMessage.success('销账成功');
    writeOffVisible.value = false;
    await Promise.all([loadCurrent(), loadStats()]);
  } finally {
    saving.value = false;
  }
}

async function reverseLog(row) {
  const result = await ElMessageBox.prompt(
    `冲销后会生成一条相反数量的流水，请输入冲销原因。`,
    `冲销 ${row.operationNo}`,
    {
      confirmButtonText: '确认冲销',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '请输入冲销原因',
      type: 'warning'
    }
  );
  await reverseProductDiffLog(row.id, {
    storeId: row.storeId,
    businessDate: today(),
    reason: result.value
  });
  ElMessage.success('流水已冲销');
  await Promise.all([loadLogs(), loadCurrent(), loadStats()]);
}

watch(() => [currentPagination.page, currentPagination.pageSize], loadCurrent);
watch(() => [logPagination.page, logPagination.pageSize], loadLogs);
onMounted(async () => {
  stores.value = await getProductStores();
  await Promise.all([loadCurrent(), loadStats()]);
});
</script>

<style scoped>
.header-actions,
.filters,
.summary-band,
.section-heading,
.item-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.summary-band {
  border: 1px solid var(--app-border);
  background: #fff;
}

.summary-band button {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  padding: 0 20px;
  border: 0;
  border-right: 1px solid var(--app-border);
  color: var(--app-text);
  background: transparent;
  cursor: pointer;
}

.summary-band button.active {
  box-shadow: inset 0 -2px var(--el-color-primary);
}

.summary-band span {
  color: var(--app-muted);
}

.summary-band strong {
  font-size: 20px;
}

.filters {
  margin-bottom: 16px;
}

.filters :deep(.el-select) {
  width: 180px;
}

.filters :deep(.el-input) {
  width: 280px;
}

.log-filters :deep(.el-date-editor) {
  width: 260px;
}

.diff-more {
  color: var(--el-color-warning-dark-2);
}

.diff-less {
  color: var(--el-color-danger);
}

.diff-zero {
  color: var(--app-muted);
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
.item-title {
  justify-content: space-between;
}

.section-heading {
  margin: 8px 0 12px;
  font-weight: 600;
}

.item-list {
  display: grid;
  gap: 12px;
}

.item-row {
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.item-grid {
  display: grid;
  grid-template-columns: minmax(240px, 2fr) repeat(3, minmax(100px, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.item-batch {
  grid-column: 1 / -1;
}

.item-grid label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: var(--app-muted);
  font-size: 13px;
}

.item-grid :deep(.el-select),
.item-grid :deep(.el-input),
.item-grid :deep(.el-input-number) {
  width: 100%;
}

.impact-preview,
.write-off-preview {
  margin-top: 10px;
  color: var(--app-muted);
  font-size: 13px;
}

.register-remark,
.write-off-form {
  margin-top: 18px;
}

.unit-text {
  margin-left: 8px;
  color: var(--app-muted);
}

@media (max-width: 760px) {
  .header-actions,
  .header-actions .el-button,
  .filters :deep(.el-input),
  .filters :deep(.el-select),
  .log-filters :deep(.el-date-editor) {
    width: 100%;
  }

  .summary-band {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
  }

  .summary-band button {
    justify-content: center;
    padding: 0 8px;
  }

  .form-grid,
  .item-grid {
    grid-template-columns: 1fr;
  }
}
</style>
