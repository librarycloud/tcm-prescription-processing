<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>加工设备</h2>
        <p>维护浸泡桶、煎药机和包装机，打印固定设备码。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增设备</el-button>
    </div>

    <div class="toolbar">
      <el-input
        v-model.trim="query.keyword"
        clearable
        placeholder="设备编号 / 名称"
        :prefix-icon="Search"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.type" clearable placeholder="全部类型" @change="search">
        <el-option
          v-for="item in typeOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="全部状态" @change="search">
        <el-option label="启用" :value="1" />
        <el-option label="维修" :value="2" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-select
        v-if="userStore.isSuperAdmin"
        v-model="query.storeId"
        clearable
        placeholder="全部门店"
        @change="search"
      >
        <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
      </el-select>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border row-key="id" table-layout="auto">
      <template #empty><EmptyView description="暂无加工设备" /></template>
      <el-table-column prop="equipmentNo" label="设备编号" min-width="110" />
      <el-table-column prop="name" label="设备名称" min-width="150" />
      <el-table-column prop="typeName" label="类型" min-width="100" />
      <el-table-column
        v-if="userStore.isSuperAdmin"
        prop="store.name"
        label="门店"
        min-width="130"
      />
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="占用情况" min-width="190">
        <template #default="{ row }">
          <span v-if="row.currentUsage">
            {{ row.currentUsage.processingPlan?.prescription?.customerName }} ·
            {{ row.currentUsage.processingPlan?.planCode }}
          </span>
          <span v-else class="muted">空闲</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Printer" @click="openPrint(row)">打印</el-button>
          <el-button link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button
            link
            type="danger"
            :disabled="Boolean(row.currentUsageId)"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <Pagination
      v-model:page="query.page"
      v-model:page-size="query.pageSize"
      :total="total"
      @change="load"
    />

    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑设备' : '新增设备'"
      width="500px"
      align-center
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item v-if="userStore.isSuperAdmin" label="所属门店" prop="storeId">
          <el-select v-model="form.storeId" :disabled="Boolean(form.id)" style="width: 100%">
            <el-option
              v-for="store in stores"
              :key="store.id"
              :label="store.name"
              :value="store.id"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="设备编号" prop="equipmentNo">
            <el-input v-model.trim="form.equipmentNo" maxlength="32" placeholder="例如 P01" />
          </el-form-item>
          <el-form-item label="设备类型" prop="type">
            <el-select
              v-model="form.type"
              :disabled="Boolean(form.currentUsageId)"
              style="width: 100%"
            >
              <el-option
                v-for="item in typeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="设备名称" prop="name">
          <el-input v-model.trim="form.name" maxlength="100" placeholder="例如 1号煎药机" />
        </el-form-item>
        <el-form-item label="设备状态">
          <el-segmented
            v-model="form.status"
            :disabled="Boolean(form.currentUsageId)"
            :options="statusOptions"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model.trim="form.remark"
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

    <EquipmentPrintDialog v-model="printVisible" :equipment="printingEquipment" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Printer, Refresh, Search } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import EquipmentPrintDialog from '@/components/EquipmentPrintDialog.vue';
import {
  createProcessingEquipment,
  deleteProcessingEquipment,
  getProcessingEquipment,
  updateProcessingEquipment
} from '@/api/processingEquipment';
import { getStores } from '@/api/store';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const saving = ref(false);
const list = ref([]);
const total = ref(0);
const stores = ref([]);
const typeOptions = ref([
  { value: 'SOAK_BUCKET', label: '浸泡桶' },
  { value: 'DECOCTION_POT', label: '煎药机' },
  { value: 'PACKAGING_MACHINE', label: '包装机' }
]);
const query = reactive({ page: 1, pageSize: 20, keyword: '', type: '', status: '', storeId: '' });
const formVisible = ref(false);
const printVisible = ref(false);
const printingEquipment = ref(null);
const formRef = ref(null);
const form = reactive({
  id: null,
  storeId: null,
  equipmentNo: '',
  name: '',
  type: 'SOAK_BUCKET',
  status: 1,
  remark: '',
  currentUsageId: null
});
const statusOptions = [
  { label: '启用', value: 1 },
  { label: '维修', value: 2 },
  { label: '停用', value: 0 }
];
const rules = {
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
  equipmentNo: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择设备类型', trigger: 'change' }]
};

function statusText(status) {
  return { 0: '停用', 1: '启用', 2: '维修' }[Number(status)] || '-';
}
function statusType(status) {
  return { 0: 'info', 1: 'success', 2: 'warning' }[Number(status)] || 'info';
}

async function load() {
  loading.value = true;
  try {
    const data = await getProcessingEquipment(query);
    list.value = data.list || [];
    total.value = data.pagination?.total || 0;
    if (data.types?.length) typeOptions.value = data.types;
  } finally {
    loading.value = false;
  }
}
function search() {
  query.page = 1;
  load();
}
function openForm(row = null) {
  Object.assign(form, {
    id: row?.id || null,
    storeId: row?.storeId || (userStore.isSuperAdmin ? query.storeId || null : undefined),
    equipmentNo: row?.equipmentNo || '',
    name: row?.name || '',
    type: row?.type || 'SOAK_BUCKET',
    status: Number(row?.status ?? 1),
    remark: row?.remark || '',
    currentUsageId: row?.currentUsageId || null
  });
  formVisible.value = true;
}
async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload = { ...form };
    if (form.id) await updateProcessingEquipment(form.id, payload);
    else await createProcessingEquipment(payload);
    ElMessage.success(form.id ? '设备已更新' : '设备已新增');
    formVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除设备“${row.equipmentNo} ${row.name}”吗？`, '删除设备', {
    type: 'warning'
  });
  await deleteProcessingEquipment(row.id);
  ElMessage.success('设备已删除');
  await load();
}
function openPrint(row) {
  printingEquipment.value = row;
  printVisible.value = true;
}

onMounted(async () => {
  if (userStore.isSuperAdmin) {
    stores.value = (await getStores({ page: 1, pageSize: 100 }))?.list || [];
  }
  await load();
});
</script>

<style scoped>
.page-header,
.toolbar,
.form-grid {
  display: flex;
  align-items: center;
}
.page-header {
  justify-content: space-between;
  margin-bottom: 18px;
}
.page-header h2,
.page-header p {
  margin: 0;
}
.page-header p,
.muted {
  color: var(--app-muted);
}
.toolbar {
  gap: 10px;
  margin-bottom: 14px;
}
.toolbar .el-input {
  width: 240px;
}
.toolbar .el-select {
  width: 150px;
}
.form-grid {
  align-items: start;
  gap: 12px;
}
.form-grid > * {
  flex: 1;
}
@media (max-width: 760px) {
  .toolbar,
  .form-grid {
    flex-wrap: wrap;
  }
  .toolbar > * {
    width: 100% !important;
  }
}
</style>
