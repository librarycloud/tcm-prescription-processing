<template>
  <div class="page e6-pharmacy-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">E6药店商品库存</h1>
        <p class="page-subtitle">查询 E6 当前库存中存在的商品及批号库存</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Download" @click="downloadBarcodeTemplate">条形码模板</el-button>
        <el-button type="primary" :icon="Upload" @click="openBarcodeImport">上传条形码</el-button>
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
        <el-select v-model="query.categoryCode" clearable placeholder="全部分类" @change="search">
          <el-option
            v-for="item in categoryMappings"
            :key="item.categoryCode"
            :label="item.categoryName"
            :value="item.categoryCode"
          />
        </el-select>
        <el-select v-model="query.expiryWithinMonths" placeholder="有效期小于" clearable @change="handleExpiryChange">
          <el-option label="有效期小于1个月" :value="1" />
          <el-option label="有效期小于3个月" :value="3" />
          <el-option label="有效期小于6个月" :value="6" />
          <el-option label="自定义月份" value="custom" />
        </el-select>
        <el-input-number
          v-if="query.expiryWithinMonths === 'custom'"
          v-model="query.customExpiryMonths"
          :min="1"
          :max="120"
          :step="1"
          controls-position="right"
          placeholder="月数"
          @change="search"
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
                <el-table-column v-if="userStore.isSuperAdmin" label="门店" min-width="120">
                  <template #default="{ row: batch }">{{ batch.store?.name || '-' }}</template>
                </el-table-column>
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
                <el-table-column label="更新时间" min-width="170">
                  <template #default="{ row: batch }">{{ dateTimeText(batch.updatedAt) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="productCode" label="商品编号" min-width="130" />
        <el-table-column prop="categoryName" label="分类" min-width="120">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>
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
        <el-table-column label="零售价" width="100" align="right">
          <template #default="{ row }">¥ {{ Number(row.retailPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="批号数" align="center">
          <template #default="{ row }">{{ row.batchCount }}</template>
        </el-table-column>
        <el-table-column label="总库存" align="right">
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

    <el-dialog v-model="barcodeImportVisible" title="上传商品条形码" width="520px" destroy-on-close>
      <el-alert
        title="按商品编号匹配，只补充没有条形码的商品；已有条形码会跳过，不会覆盖。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-upload
        class="barcode-upload"
        drag
        :auto-upload="false"
        accept=".xlsx"
        :limit="1"
        :show-file-list="true"
        :on-change="handleBarcodeFileChange"
        :on-remove="clearBarcodeFile"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖放或点击选择 .xlsx 文件</div>
      </el-upload>
      <div v-if="barcodeResult" class="barcode-result">
        <el-tag type="success" effect="plain">更新 {{ barcodeResult.updated }}</el-tag>
        <el-tag type="info" effect="plain">已有条形码跳过 {{ barcodeResult.skippedExisting }}</el-tag>
        <el-tag type="warning" effect="plain">未找到商品 {{ barcodeResult.notFound }}</el-tag>
        <el-tag v-if="barcodeResult.invalid" type="danger" effect="plain">无效行 {{ barcodeResult.invalid }}</el-tag>
      </div>
      <template #footer>
        <el-button @click="barcodeImportVisible = false">取消</el-button>
        <el-button type="primary" :loading="barcodeImporting" :disabled="!barcodeFile" @click="submitBarcodeImport">开始上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Download, Refresh, Search, Upload, UploadFilled } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { getProductStores } from '@/api/productDifference';
import {
  downloadE6PharmacyBarcodeTemplate,
  getE6PharmacyCategoryMappings,
  getE6PharmacyProducts,
  importE6PharmacyBarcodes
} from '@/api/e6Pharmacy';
import { formatDateSeconds } from '@/utils/date';

const userStore = useUserStore();
const tableRef = ref();
const loading = ref(false);
const list = ref([]);
const stores = ref([]);
const categoryMappings = ref([]);
const barcodeImportVisible = ref(false);
const barcodeImporting = ref(false);
const barcodeFile = ref(null);
const barcodeResult = ref(null);
const query = reactive({ keyword: '', storeId: undefined, categoryCode: undefined, expiryWithinMonths: undefined, customExpiryMonths: 1 });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });

function dateText(value) {
  if (!value) return '-';
  return String(value).slice(0, 10);
}

function dateTimeText(value) {
  return formatDateSeconds(value);
}

function quantityText(value) {
  const number = Number(value || 0);
  return Number.isInteger(number) ? String(number) : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
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

async function loadCategoryMappings() {
  categoryMappings.value = await getE6PharmacyCategoryMappings();
}

async function load() {
  loading.value = true;
  try {
    const data = await getE6PharmacyProducts({
      keyword: query.keyword || undefined,
      storeId: query.storeId || undefined,
      categoryCode: query.categoryCode || undefined,
      expiryWithinMonths: expiryFilterValue(),
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data.list || [];
    Object.assign(pagination, data.pagination || {});
    const keyword = String(query.keyword || '').trim();
    const exactMatches = list.value.filter((item) =>
      String(item.productCode || '').trim() === keyword || String(item.barcode || '').trim() === keyword,
    );
    if (exactMatches.length === 1) {
      await nextTick();
      tableRef.value?.toggleRowExpansion(exactMatches[0], true);
    }
  } finally {
    loading.value = false;
  }
}

function expiryFilterValue() {
  if (query.expiryWithinMonths === 'custom') return query.customExpiryMonths || undefined;
  return query.expiryWithinMonths || undefined;
}

function handleExpiryChange(value) {
  if (value !== 'custom') search();
}

function search() {
  pagination.page = 1;
  load();
}

function resetSearch() {
  query.keyword = '';
  query.storeId = undefined;
  query.categoryCode = undefined;
  query.expiryWithinMonths = undefined;
  query.customExpiryMonths = 1;
  pagination.page = 1;
  load();
}

function toggleRow(row) {
  tableRef.value?.toggleRowExpansion(row);
}

async function downloadBarcodeTemplate() {
  const blob = await downloadE6PharmacyBarcodeTemplate();
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.href = url;
  link.download = 'E6药店条形码模板.xlsx';
  link.click();
  URL.revokeObjectURL(url);
}

function openBarcodeImport() {
  barcodeFile.value = null;
  barcodeResult.value = null;
  barcodeImportVisible.value = true;
}

function handleBarcodeFileChange(uploadFile) {
  barcodeFile.value = uploadFile.raw;
  barcodeResult.value = null;
}

function clearBarcodeFile() {
  barcodeFile.value = null;
  barcodeResult.value = null;
}

async function submitBarcodeImport() {
  if (!barcodeFile.value) return ElMessage.warning('请选择 Excel 文件');
  barcodeImporting.value = true;
  try {
    barcodeResult.value = await importE6PharmacyBarcodes(barcodeFile.value);
    ElMessage.success('条形码上传完成');
    await load();
  } finally {
    barcodeImporting.value = false;
  }
}

watch(() => [pagination.page, pagination.pageSize], load);

onMounted(async () => {
  await loadStores();
  await Promise.all([loadCategoryMappings(), load()]);
});
</script>

<style scoped>
.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.barcode-upload {
  margin-top: 16px;
}

.barcode-result {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
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
