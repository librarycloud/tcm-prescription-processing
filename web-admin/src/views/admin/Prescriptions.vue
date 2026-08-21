<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">处方管理</h1>
        <p class="page-subtitle">管理处方信息、加工批次与领取进度</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建处方</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="filters" inline @submit.prevent>
        <el-form-item label="搜索">
          <el-input
            v-model.trim="query.keyword"
            :prefix-icon="Search"
            clearable
            placeholder="处方编号、姓名、手机号或医生"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="query.doctorId" clearable filterable placeholder="全部医生">
            <el-option v-for="item in doctors" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userStore.isSuperAdmin" label="门店">
          <el-select v-model="query.storeId" clearable filterable placeholder="全部门店">
            <el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table
        v-loading="loading"
        class="prescription-table"
        :data="list"
        row-key="id"
        border table-layout="auto">
        <template #empty><EmptyView description="暂无处方" /></template>
        <el-table-column prop="prescriptionNo" label="处方编号" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">{{
              row.prescriptionNo
            }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="顾客" align="center">
          <template #default="{ row }">
            <strong>{{ row.customerName }}</strong>
            <div class="secondary-text">{{ maskPhone(row.phone) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="医生 / 来源" align="center">
          <template #default="{ row }">
            <span>{{ row.doctor?.name || '-' }}</span>
            <div class="secondary-text">{{ row.source?.name || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="userStore.isSuperAdmin"
          label="所属门店"
          align="center"
        >
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="处方类型" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isExternal" type="warning" effect="plain">外方</el-tag>
            <span v-else>本方</span>
          </template>
        </el-table-column>
        <el-table-column label="剂数进度" align="center">
          <template #default="{ row }">
            <span>{{ row.takenDose }} / {{ row.totalDose }}</span>
            <div class="secondary-text">剩余 {{ row.remainingDose }} 剂</div>
          </template>
        </el-table-column>
        <el-table-column label="总价" align="center">
          <template #default="{ row }">{{ row.totalPrice == null ? '-' : `¥${Number(row.totalPrice).toFixed(2)}` }}</template>
        </el-table-column>
        <el-table-column label="批次数" align="center">
          <template #default="{ row }">{{ row.plans?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="录入时间" align="center">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
            <el-button
              link
              type="primary"
              :disabled="row.status === PRESCRIPTION_STATUS.COMPLETED"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              :disabled="Boolean(row.plans?.length)"
              @click="remove(row)"
            >
              删除
            </el-button>
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
      v-model="formVisible"
      :title="form.id ? '编辑处方' : '新建处方'"
      width="680px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item v-if="userStore.isSuperAdmin" label="所属门店" prop="storeId">
          <el-select
            v-model="form.storeId"
            filterable
            :disabled="Boolean(form.id)"
            placeholder="请选择所属门店"
            style="width: 100%"
          >
            <el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="顾客姓名" prop="customerName">
            <el-input v-model.trim="form.customerName" maxlength="64" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model.trim="form.phone" maxlength="11" placeholder="选填，留空不创建用户" />
          </el-form-item>
          <el-form-item label="医生" prop="doctorId">
            <el-select v-model="form.doctorId" filterable placeholder="请选择医生">
              <el-option
                v-for="item in doctors"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="处方来源" prop="sourceId">
            <el-select v-model="form.sourceId" filterable placeholder="请选择处方来源">
              <el-option
                v-for="item in sources"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="总价" prop="totalPrice">
            <el-input v-model.trim="form.totalPrice" maxlength="15" placeholder="选填，如 268.00">
              <template #prefix>¥</template>
            </el-input>
          </el-form-item>
        </div>
        <el-form-item label="外方">
          <el-switch v-model="form.isExternal" />
        </el-form-item>
        <div v-if="form.isExternal" class="external-box">
          <div class="form-grid">
            <el-form-item label="医院名称">
              <el-input v-model.trim="form.externalHospital" maxlength="150" />
            </el-form-item>
            <el-form-item label="医生姓名">
              <el-input v-model.trim="form.externalDoctor" maxlength="100" />
            </el-form-item>
          </div>
          <el-form-item label="外方备注">
            <el-input
              v-model="form.externalRemark"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </div>
        <el-form-item v-if="form.id" label="处方状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="PRESCRIPTION_STATUS.ACTIVE">进行中</el-radio>
            <el-radio :value="PRESCRIPTION_STATUS.CANCELLED">已取消</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="detailDrawerVisible"
      :title="detailDrawerTitle"
      size="min(960px, 96vw)"
      destroy-on-close
    >
      <PrescriptionDetail
        v-if="detailPrescriptionId"
        :key="detailPrescriptionId"
        :id="detailPrescriptionId"
        embedded
        @package-detail="openPackageDrawer('detail', $event)"
      />
    </el-drawer>

    <el-drawer
      v-model="packageDrawerVisible"
      size="min(720px, 96vw)"
      destroy-on-close
    >
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
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import { Plus, Printer, Search } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import PrescriptionDetail from './PrescriptionDetail.vue';
import PackageDetail from './PackageDetail.vue';
import PackageEdit from './PackageEdit.vue';
import Verify from './Verify.vue';
import {
  createPrescription,
  deletePrescription,
  getPrescription,
  getPrescriptions,
  updatePrescription
} from '@/api/prescription';
import { getDictionaries, getDoctors } from '@/api/processing';
import { getStores } from '@/api/store';
import { useUserStore } from '@/stores/user';
import { formatDate } from '@/utils/date';
import { isValidPhone, maskPhone } from '@/utils/phone';

const PRESCRIPTION_STATUS = Object.freeze({ ACTIVE: 0, COMPLETED: 1, CANCELLED: 2 });
const statusOptions = Object.freeze([
  { label: '进行中', value: PRESCRIPTION_STATUS.ACTIVE },
  { label: '已完成', value: PRESCRIPTION_STATUS.COMPLETED },
  { label: '已取消', value: PRESCRIPTION_STATUS.CANCELLED }
]);
const route = useRoute();
const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const formVisible = ref(false);
const detailDrawerVisible = ref(false);
const detailPrescriptionId = ref(null);
const packageDrawerVisible = ref(false);
const packageDrawerMode = ref('detail');
const packageDrawerId = ref(null);
const packageDrawerCode = ref('');
const packageDetailRef = ref(null);
const formRef = ref(null);
const list = ref([]);
const doctors = ref([]);
const sources = ref([]);
const stores = ref([]);
const query = reactive({ keyword: '', status: '', doctorId: '', storeId: '' });
const pagination = reactive({ page: 1, pageSize: 10, total: 0 });
const form = reactive({});
const rules = {
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
  customerName: [{ required: true, message: '请输入顾客姓名', trigger: 'blur' }],
  phone: [
    {
      validator: (_rule, value, callback) => {
        if (value && !isValidPhone(value)) callback(new Error('请输入正确的手机号'));
        else callback();
      },
      trigger: 'blur'
    }
  ],
  doctorId: [{ required: true, message: '请选择医生', trigger: 'change' }],
  sourceId: [{ required: true, message: '请选择处方来源', trigger: 'change' }],
  totalPrice: [
    {
      validator: (_rule, value, callback) => {
        if (value && !/^\d{1,12}(\.\d{1,2})?$/.test(String(value))) {
          callback(new Error('请输入正确的总价'));
        }
        else callback();
      },
      trigger: 'blur'
    }
  ]
};

function resetForm() {
  Object.assign(form, {
    id: null,
    storeId: null,
    customerName: '',
    phone: '',
    doctorId: null,
    sourceId: null,
    totalPrice: '',
    isExternal: false,
    externalHospital: '',
    externalDoctor: '',
    externalRemark: '',
    remark: '',
    status: PRESCRIPTION_STATUS.ACTIVE
  });
}

function statusText(status) {
  return statusOptions.find((item) => item.value === Number(status))?.label || '未知';
}

function statusType(status) {
  return ['info', 'success', 'danger'][Number(status)] || 'info';
}

async function load() {
  loading.value = true;
  try {
    const data = await getPrescriptions({
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

function search() {
  pagination.page = 1;
  load();
}

function resetSearch() {
  Object.assign(query, { keyword: '', status: '', doctorId: '', storeId: '' });
  search();
}

function openCreate() {
  resetForm();
  formVisible.value = true;
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    storeId: row.storeId,
    customerName: row.customerName,
    phone: row.phone || '',
    doctorId: row.doctorId,
    sourceId: row.sourceId,
    totalPrice: row.totalPrice == null ? '' : String(row.totalPrice),
    isExternal: Boolean(row.isExternal),
    externalHospital: row.externalHospital || '',
    externalDoctor: row.externalDoctor || '',
    externalRemark: row.externalRemark || '',
    remark: row.remark || '',
    status: row.status
  });
  formVisible.value = true;
}

function viewDetail(row) {
  detailPrescriptionId.value = row.id;
  detailDrawerVisible.value = true;
}

const detailDrawerTitle = computed(() => '处方详情');
const packageDrawerTitle = computed(() => {
  if (packageDrawerMode.value === 'verify') return '包裹核销';
  if (packageDrawerMode.value === 'edit') return '编辑包裹';
  return '包裹详情';
});

function openPackageDrawer(mode, pkg) {
  packageDrawerMode.value = mode;
  packageDrawerId.value = pkg?.id || null;
  packageDrawerCode.value = pkg?.pickupCode || '';
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
}

function handlePackageDrawerVerified() {
  closePackageDrawer();
}

async function save() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    const payload = {
      customerName: form.customerName,
      phone: form.phone,
      doctorId: form.doctorId,
      sourceId: form.sourceId,
      totalPrice: form.totalPrice,
      isExternal: form.isExternal,
      externalHospital: form.externalHospital,
      externalDoctor: form.externalDoctor,
      externalRemark: form.externalRemark,
      remark: form.remark,
      ...(form.id ? { status: form.status } : { storeId: form.storeId })
    };
    if (form.id) await updatePrescription(form.id, payload);
    else await createPrescription(payload);
    formVisible.value = false;
    ElMessage.success(form.id ? '处方已更新' : '处方已创建');
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除处方 ${row.prescriptionNo}？`, '删除处方', {
    type: 'warning'
  });
  await deletePrescription(row.id);
  ElMessage.success('处方已删除');
  await load();
}

watch(() => [pagination.page, pagination.pageSize], load);
onMounted(async () => {
  const tasks = [getDoctors(), getDictionaries('PrescriptionSource')];
  if (userStore.isSuperAdmin) tasks.push(getStores({ page: 1, pageSize: 100, status: 1 }));
  const [doctorData, sourceData, storeData] = await Promise.all(tasks);
  doctors.value = doctorData || [];
  sources.value = sourceData || [];
  stores.value = storeData?.list || [];
  if (userStore.isSuperAdmin && route.query.storeId) query.storeId = Number(route.query.storeId);
  resetForm();
  const editId = Number(route.query.editId);
  if (Number.isInteger(editId) && editId > 0) {
    const prescription = await getPrescription(editId);
    openEdit(prescription);
  }
  await load();
});
</script>

<style scoped>
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

.prescription-table {
  width: 100%;
  table-layout: auto;
}

.prescription-table :deep(.el-table__header),
.prescription-table :deep(.el-table__body),
.prescription-table :deep(.el-table__footer) {
  table-layout: auto !important;
}

.filters :deep(.el-input) {
  width: 250px;
}

.filters :deep(.el-select) {
  width: 160px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.form-grid :deep(.el-select) {
  width: 100%;
}

.external-box {
  margin: 0 0 18px;
  padding: 16px 12px 1px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
