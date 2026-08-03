<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">货品项目</h1>
        <p class="page-subtitle">维护商品编号、名称、规格、单位和零售价</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
        <el-button :icon="Upload" @click="openImport">导入 Excel</el-button>
        <el-button type="primary" :icon="Plus" @click="openProduct()">新增商品</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="search-form" @submit.prevent="search">
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
        <el-input v-model.trim="query.keyword" clearable placeholder="商品编号、名称或规格" />
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" value="1" />
          <el-option label="停用" value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty><EmptyView description="暂无商品" /></template>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="productCode" label="商品编号" />
        <el-table-column prop="name" label="商品名称" />
        <el-table-column label="规格">
          <template #default="{ row }">{{ row.specification || '-' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" align="center" />
        <el-table-column label="差异数量" align="right">
          <template #default="{ row }">
            <span :class="diffClass(row.diffQuantity)">{{ signedQuantity(row.diffQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="零售价" align="right">
          <template #default="{ row }">{{ priceText(row.retailPrice) }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" effect="plain">
              {{ Number(row.status) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" :icon="Edit" @click="openProduct(row)">编辑</el-button>
              <el-button
                link
                :type="Number(row.status) === 1 ? 'warning' : 'success'"
                @click="toggleProduct(row)"
              >
                {{ Number(row.status) === 1 ? '停用' : '启用' }}
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

    <el-dialog
      v-model="productVisible"
      :title="form.id ? '编辑商品' : '新增商品'"
      width="560px"
      align-center
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="userStore.isSuperAdmin" label="所属门店" prop="storeId">
          <el-select v-model="form.storeId" :disabled="Boolean(form.id)" placeholder="请选择门店">
            <el-option
              v-for="store in stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="商品编号" prop="productCode">
          <el-input v-model.trim="form.productCode" maxlength="64" />
        </el-form-item>
        <el-form-item label="商品名称" prop="name">
          <el-input v-model.trim="form.name" maxlength="120" />
        </el-form-item>
        <el-form-item label="规格" prop="specification">
          <el-input v-model.trim="form.specification" maxlength="120" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model.trim="form.unit" maxlength="20" />
        </el-form-item>
        <el-form-item label="零售价" prop="retailPrice">
          <el-input-number
            v-model="form.retailPrice"
            :min="0"
            :max="999999999999.99"
            :precision="2"
            controls-position="right"
          />
          <span class="field-note">仅用于快速区分商品，不参与金额计算</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importVisible"
      title="导入商品"
      width="min(980px, calc(100vw - 32px))"
      align-center
    >
      <div class="import-controls">
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="importStoreId"
          placeholder="请选择所属门店"
          :disabled="previewLoading || importing"
        >
          <el-option
            v-for="store in stores"
            :key="store.id"
            :label="store.name"
            :value="store.id"
          />
        </el-select>
        <el-radio-group v-model="overwriteDifference" :disabled="previewLoading || importing">
          <el-radio-button :value="false">保留已有差异</el-radio-button>
          <el-radio-button :value="true">Excel 数量作为最新差异</el-radio-button>
        </el-radio-group>
      </div>
      <el-alert
        title="新增商品的数量作为期初差异；已有商品默认只更新资料，不覆盖当前差异。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-upload
        class="import-upload"
        drag
        :auto-upload="false"
        accept=".xlsx"
        :limit="1"
        :show-file-list="true"
        :on-change="handleFileChange"
        :on-remove="clearImportFile"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖放或点击选择 .xlsx 文件</div>
      </el-upload>

      <template v-if="preview">
        <div class="preview-summary">
          <span>共 {{ preview.summary.total }} 行</span>
          <span>新增 {{ preview.summary.create }}</span>
          <span>更新 {{ preview.summary.update }}</span>
          <span v-if="preview.summary.restore">待恢复 {{ preview.summary.restore }}</span>
          <span :class="{ 'invalid-text': preview.summary.invalid }"
            >错误 {{ preview.summary.invalid }}</span
          >
        </div>
        <el-table :data="preview.list" border max-height="360" table-layout="auto">
          <el-table-column prop="rowNumber" label="行号" align="center" />
          <el-table-column prop="productCode" label="商品编号" />
          <el-table-column prop="name" label="商品名称" />
          <el-table-column label="规格">
            <template #default="{ row }">{{ row.specification || '-' }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" align="center" />
          <el-table-column label="数量" align="right">
            <template #default="{ row }">{{ signedQuantity(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="零售价" align="right">
            <template #default="{ row }">{{ priceText(row.retailPrice) }}</template>
          </el-table-column>
          <el-table-column label="处理方式" align="center">
            <template #default="{ row }">
              {{ row.action === 'CREATE' ? '新增' : row.action === 'RESTORE' ? '待恢复' : '更新' }}
            </template>
          </el-table-column>
          <el-table-column label="校验结果">
            <template #default="{ row }">
              <span v-if="row.errors.length" class="invalid-text">{{ row.errors.join('；') }}</span>
              <el-tag v-else type="success" effect="plain">通过</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button :loading="previewLoading" :disabled="!importFile" @click="previewImport"
          >预览</el-button
        >
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!preview || preview.summary.invalid > 0"
          @click="confirmImport"
        >
          确认导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import {
  Download,
  Edit,
  Plus,
  Refresh,
  Search,
  Upload,
  UploadFilled
} from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { useUserStore } from '@/stores/user';
import {
  createProduct,
  downloadProductImportTemplate,
  getProducts,
  getProductStores,
  importProducts,
  previewProductImport,
  updateProduct
} from '@/api/productDifference';

const userStore = useUserStore();
const stores = ref([]);
const list = ref([]);
const loading = ref(false);
const saving = ref(false);
const productVisible = ref(false);
const formRef = ref(null);
const query = reactive({ storeId: '', keyword: '', status: '' });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });
const form = reactive({
  id: null,
  storeId: null,
  productCode: '',
  name: '',
  specification: '',
  unit: '',
  retailPrice: 0,
  status: 1
});
const rules = {
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
  productCode: [{ required: true, message: '请输入商品编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }]
};

const importVisible = ref(false);
const importStoreId = ref(null);
const importFile = ref(null);
const overwriteDifference = ref(false);
const preview = ref(null);
const previewLoading = ref(false);
const importing = ref(false);

function signedQuantity(value) {
  const number = Number(value || 0);
  const text = Number.isInteger(number)
    ? String(number)
    : number.toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
  return number > 0 ? `+${text}` : text;
}

function diffClass(value) {
  const number = Number(value || 0);
  return number > 0 ? 'diff-more' : number < 0 ? 'diff-less' : 'diff-zero';
}

function priceText(value) {
  return Number(value || 0).toFixed(2);
}

async function loadProducts() {
  loading.value = true;
  try {
    const data = await getProducts({
      ...query,
      includeDisabled: query.status === '' ? '1' : undefined,
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  if (pagination.page !== 1) pagination.page = 1;
  else loadProducts();
}

function resetSearch() {
  Object.assign(query, { storeId: '', keyword: '', status: '' });
  search();
}

function resetForm() {
  Object.assign(form, {
    id: null,
    storeId: userStore.isSuperAdmin ? query.storeId || null : Number(userStore.user?.storeId),
    productCode: '',
    name: '',
    specification: '',
    unit: '',
    retailPrice: 0,
    status: 1
  });
}

function openProduct(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      storeId: row.storeId,
      productCode: row.productCode,
      name: row.name,
      specification: row.specification || '',
      unit: row.unit,
      retailPrice: Number(row.retailPrice),
      status: Number(row.status)
    });
  } else resetForm();
  productVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}

async function saveProduct() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (form.id) await updateProduct(form.id, form);
    else await createProduct(form);
    ElMessage.success(form.id ? '商品已更新' : '商品已新增');
    productVisible.value = false;
    await loadProducts();
  } finally {
    saving.value = false;
  }
}

async function toggleProduct(row) {
  const status = Number(row.status) === 1 ? 0 : 1;
  await ElMessageBox.confirm(`确认${status ? '启用' : '停用'}商品“${row.name}”？`, '商品状态', {
    type: 'warning'
  });
  await updateProduct(row.id, { ...row, status });
  ElMessage.success(status ? '商品已启用' : '商品已停用');
  await loadProducts();
}

async function downloadTemplate() {
  const blob = await downloadProductImportTemplate();
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.href = url;
  link.download = '商品导入模板.xlsx';
  link.click();
  URL.revokeObjectURL(url);
}

function openImport() {
  importStoreId.value = userStore.isSuperAdmin
    ? query.storeId || null
    : Number(userStore.user?.storeId);
  importFile.value = null;
  preview.value = null;
  overwriteDifference.value = false;
  importVisible.value = true;
}

function handleFileChange(uploadFile) {
  importFile.value = uploadFile.raw;
  preview.value = null;
}

function clearImportFile() {
  importFile.value = null;
  preview.value = null;
}

async function previewImport() {
  if (!importStoreId.value) return ElMessage.warning('请选择所属门店');
  if (!importFile.value) return ElMessage.warning('请选择 Excel 文件');
  previewLoading.value = true;
  try {
    preview.value = await previewProductImport(
      importStoreId.value,
      importFile.value,
      overwriteDifference.value
    );
  } finally {
    previewLoading.value = false;
  }
}

async function confirmImport() {
  if (!preview.value || preview.value.summary.invalid) return;
  importing.value = true;
  try {
    const result = await importProducts(
      importStoreId.value,
      importFile.value,
      overwriteDifference.value
    );
    ElMessage.success(`导入完成：新增 ${result.created}，更新 ${result.updated}`);
    importVisible.value = false;
    await loadProducts();
  } finally {
    importing.value = false;
  }
}

watch(() => [pagination.page, pagination.pageSize], loadProducts);
watch(overwriteDifference, () => {
  preview.value = null;
});
onMounted(async () => {
  stores.value = await getProductStores();
  await loadProducts();
});
</script>

<style scoped>
.header-actions,
.import-controls,
.preview-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-form {
  display: grid;
  grid-template-columns: 180px minmax(260px, 420px) 140px auto auto;
  gap: 10px;
  justify-content: start;
}

.field-note {
  margin-left: 10px;
  color: var(--app-muted);
  font-size: 12px;
}

.diff-more {
  color: var(--el-color-warning-dark-2);
  font-weight: 600;
}

.diff-less,
.invalid-text {
  color: var(--el-color-danger);
  font-weight: 600;
}

.diff-zero {
  color: var(--app-muted);
}

.import-controls {
  margin-bottom: 12px;
}

.import-upload {
  margin: 14px 0;
}

.preview-summary {
  margin: 12px 0;
  color: var(--app-muted);
}

@media (max-width: 800px) {
  .search-form {
    grid-template-columns: 1fr 1fr;
  }

  .search-form :deep(.el-input) {
    grid-column: 1 / -1;
  }

  .header-actions,
  .header-actions .el-button {
    width: 100%;
  }

  .field-note {
    display: block;
    margin: 6px 0 0;
  }
}
</style>
