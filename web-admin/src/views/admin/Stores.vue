<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">门店管理</h1>
        <p class="page-subtitle">{{ userStore.isSuperAdmin ? '维护门店资料、营业状态和数据归属' : '维护本门店的斗柜和柜体布局' }}</p>
      </div>
      <el-button v-if="userStore.isSuperAdmin" type="primary" :icon="Plus" @click="openCreate">新增门店</el-button>
    </div>

    <el-card v-if="userStore.isSuperAdmin" shadow="never">
      <el-form class="search-form" @submit.prevent="handleSearch">
        <el-input v-model.trim="query.keyword" clearable placeholder="搜索名称、编码、地址或电话" />
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty><EmptyView description="暂无门店" /></template>
        <el-table-column prop="name" label="门店名称" align="center" />
        <el-table-column prop="code" label="门店编码" align="center" />
        <el-table-column prop="address" label="地址" align="center">
          <template #default="{ row }">{{ row.address || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" align="center">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="userStore.isSuperAdmin" label="门店管理员" align="center">
          <template #default="{ row }">{{ row._count?.users || 0 }}</template>
        </el-table-column>
        <el-table-column v-if="userStore.isSuperAdmin" label="包裹数量" align="center">
          <template #default="{ row }">{{ row._count?.packages || 0 }}</template>
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
              <el-button v-if="userStore.isSuperAdmin" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="openE6Config(row)">E6配置</el-button>
              <el-button link type="primary" @click="openLayout(row)">柜体布局</el-button>
              <el-button v-if="userStore.isSuperAdmin" link :type="Number(row.status) === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
                {{ Number(row.status) === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button v-if="userStore.isSuperAdmin" link type="danger" @click="openDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-if="userStore.isSuperAdmin"
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑门店' : '新增门店'" width="560px" align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="门店名称" prop="name">
          <el-input v-model.trim="form.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="门店编码" prop="code">
          <el-input v-model.trim="form.code" maxlength="50" placeholder="如 SUZHOU" />
        </el-form-item>
        <el-form-item label="门店地址" prop="address">
          <el-input v-model.trim="form.address" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model.trim="form.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="layoutVisible" :title="`${layoutStore?.name || ''} · 柜体布局`" width="720px" align-center>
      <el-form label-width="110px">
        <el-form-item label="斗柜数量">
          <el-input-number v-model="layoutForm.drawerUnitCount" :min="1" :max="9" controls-position="right" @change="syncDrawerCabinets" />
        </el-form-item>
        <el-form-item label="药斗层数">
          <el-input-number v-model="layoutForm.drawerLayerCount" :min="1" :max="9" controls-position="right" @change="syncLayerColumns" />
        </el-form-item>
        <el-form-item label="各柜列数">
          <el-tabs v-model="activeDrawerUnit" class="drawer-layout-tabs">
            <el-tab-pane v-for="unit in layoutForm.drawerUnitCount" :key="unit" :label="`${unit}号斗柜`" :name="unit">
              <div class="layer-columns-grid">
                <label v-for="(_, layerIndex) in layoutForm.drawerLayerColumns[unit - 1]" :key="layerIndex">
                  <span>{{ layerIndex === 0 ? '顶层' : `第 ${layerIndex} 层` }}</span>
                  <el-input-number v-model="layoutForm.drawerLayerColumns[unit - 1][layerIndex]" :min="1" :max="9" controls-position="right" />
                </label>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-form-item>
        <el-form-item label="G 柜数量">
          <el-input-number v-model="layoutForm.bigCabinetUnitCount" :min="1" :max="9" controls-position="right" />
        </el-form-item>
        <el-form-item label="G 柜层数">
          <el-input-number v-model="layoutForm.bigCabinetLayerCount" :min="1" :max="9" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="layoutVisible = false">取消</el-button>
        <el-button type="primary" :loading="layoutSaving" @click="saveStoreLayout">保存布局</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="e6Visible" :title="`${e6Store?.name || ''} · E6对接配置`" width="820px" align-center>
      <div v-loading="e6Loading" class="e6-config">
        <section class="config-section">
          <div class="section-heading">
            <div>
              <h3>接入凭证</h3>
              <p>门店编码由E6随订单提交，API Key用于验证该门店请求。</p>
            </div>
            <el-switch v-model="e6Config.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </div>
          <el-form label-width="96px">
            <el-form-item label="门店编码"><el-input :model-value="e6Store?.code || ''" readonly /></el-form-item>
            <el-form-item label="API Key">
              <div class="api-key-row">
                <el-input :model-value="e6Config.hasApiKey ? `已配置（末6位：${e6Config.apiKeyHint || '-'}）` : '尚未配置'" readonly />
                <el-button :icon="Key" @click="rotateE6ApiKey">{{ e6Config.hasApiKey ? '重置' : '生成' }}</el-button>
              </div>
            </el-form-item>
          </el-form>
          <el-alert v-if="newApiKey" type="success" :closable="false" show-icon title="新API Key仅显示本次，请妥善保存">
            <div class="api-key-row generated-key">
              <el-input :model-value="newApiKey" readonly />
              <el-button :icon="CopyDocument" @click="copyApiKey">复制</el-button>
            </div>
          </el-alert>
        </section>

        <section class="config-section">
          <div class="section-heading">
            <div><h3>医师编码映射</h3><p>E6只传医师编码，确认导入时映射为系统医生。</p></div>
            <el-button type="primary" :icon="Plus" @click="openMapping()">新增映射</el-button>
          </div>
          <el-table :data="e6Mappings" border row-key="id" table-layout="auto">
            <template #empty><EmptyView description="暂无医师映射" /></template>
            <el-table-column prop="e6DoctorCode" label="E6医师编码" />
            <el-table-column prop="doctor.name" label="系统医生" />
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-switch :model-value="row.status" :active-value="1" :inactive-value="0" @change="(status) => toggleMapping(row, status)" />
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openMapping(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeMapping(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="config-section">
          <div class="section-heading">
            <div><h3>操作员映射</h3><p>每个门店独立配置，用于 E6 导入列表的操作员显示与筛选。</p></div>
            <el-button type="primary" :icon="Plus" @click="openOperatorMapping()">新增映射</el-button>
          </div>
          <el-table :data="e6OperatorMappings" border row-key="id" table-layout="auto">
            <template #empty><EmptyView description="暂无操作员映射" /></template>
            <el-table-column prop="e6OperatorName" label="E6操作员" />
            <el-table-column prop="operatorName" label="显示操作员" />
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-switch :model-value="row.status" :active-value="1" :inactive-value="0" @change="(status) => toggleOperatorMapping(row, status)" />
              </template>
            </el-table-column>
            <el-table-column label="操作">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openOperatorMapping(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeOperatorMapping(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
      <template #footer>
        <el-button @click="e6Visible = false">关闭</el-button>
        <el-button type="primary" :loading="e6Saving" @click="saveE6Config">保存配置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="operatorMappingVisible" :title="operatorMappingForm.id ? '编辑操作员映射' : '新增操作员映射'" width="460px" append-to-body align-center>
      <el-form label-position="top">
        <el-form-item label="E6操作员" required><el-input v-model.trim="operatorMappingForm.e6OperatorName" maxlength="100" /></el-form-item>
        <el-form-item label="显示操作员" required><el-input v-model.trim="operatorMappingForm.operatorName" maxlength="100" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="operatorMappingForm.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="operatorMappingVisible = false">取消</el-button>
        <el-button type="primary" :loading="operatorMappingSaving" @click="saveOperatorMapping">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mappingVisible" :title="mappingForm.id ? '编辑医师映射' : '新增医师映射'" width="460px" append-to-body align-center>
      <el-form label-position="top">
        <el-form-item label="E6医师编码" required><el-input v-model.trim="mappingForm.e6DoctorCode" maxlength="100" /></el-form-item>
        <el-form-item label="系统医生" required>
          <el-select v-model="mappingForm.doctorId" filterable placeholder="请选择系统医生" style="width: 100%">
            <el-option v-for="doctor in e6Doctors" :key="doctor.id" :label="doctor.name" :value="doctor.id" :disabled="Number(doctor.status) !== 1" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="mappingForm.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mappingVisible = false">取消</el-button>
        <el-button type="primary" :loading="mappingSaving" @click="saveMapping">保存</el-button>
      </template>
    </el-dialog>

    <ConfirmDialog
      v-model="deleteVisible"
      title="确认删除门店"
      :content="`确认删除门店“${selectedStore?.name || ''}”吗？仅无管理员且无包裹的门店可以删除。`"
      confirm-type="danger"
      :loading="deleting"
      @confirm="handleDelete"
    />
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import { CopyDocument, Key, Plus, Refresh, Search } from '@element-plus/icons-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { createStore, deleteStore, getStores, updateStore } from '@/api/store';
import { getHerbLocationLayout, updateHerbLocationLayout } from '@/api/herbLocation';
import {
  createE6DoctorMapping,
  createE6OperatorMapping,
  deleteE6DoctorMapping,
  deleteE6OperatorMapping,
  getE6DoctorMappings,
  getE6OperatorMappings,
  getE6StoreConfig,
  saveE6StoreConfig as saveStoreE6Config,
  updateE6DoctorMapping,
  updateE6OperatorMapping
} from '@/api/e6Integration';
import { getDoctors } from '@/api/processing';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const formRef = ref(null);
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const dialogVisible = ref(false);
const deleteVisible = ref(false);
const editingId = ref(null);
const selectedStore = ref(null);
const layoutStore = ref(null);
const layoutVisible = ref(false);
const layoutSaving = ref(false);
const activeDrawerUnit = ref(1);
const e6Store = ref(null);
const e6Visible = ref(false);
const e6Loading = ref(false);
const e6Saving = ref(false);
const mappingVisible = ref(false);
const mappingSaving = ref(false);
const e6Mappings = ref([]);
const operatorMappingVisible = ref(false);
const operatorMappingSaving = ref(false);
const e6OperatorMappings = ref([]);
const e6Doctors = ref([]);
const newApiKey = ref('');
const list = ref([]);
const query = reactive({ keyword: '', status: '' });
const pagination = reactive({ page: 1, pageSize: 10, total: 0 });
const form = reactive({ name: '', code: '', address: '', phone: '', status: 1 });
const e6Config = reactive({ enabled: 0, hasApiKey: false, apiKeyHint: '', lastUsedAt: null });
const mappingForm = reactive({ id: null, e6DoctorCode: '', doctorId: '', status: 1 });
const operatorMappingForm = reactive({ id: null, e6OperatorName: '', operatorName: '', status: 1 });
const layoutForm = reactive({
  drawerUnitCount: 5,
  drawerLayerCount: 8,
  drawerLayerColumns: Array.from({ length: 5 }, () => [6, 6, 6, 6, 6, 6, 6, 6, 3]),
  bigCabinetUnitCount: 5,
  bigCabinetLayerCount: 3
});
const rules = {
  name: [{ required: true, message: '请输入门店名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入门店编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]{2,50}$/, message: '仅支持字母、数字、横线和下划线', trigger: 'blur' }
  ]
};

async function loadData() {
  loading.value = true;
  try {
    if (userStore.isStoreAdmin) {
      const data = await getHerbLocationLayout();
      list.value = data?.store ? [{ ...data.store, ...data.layout }] : [];
      pagination.total = list.value.length;
      return;
    }
    const data = await getStores({ ...query, page: pagination.page, pageSize: pagination.pageSize });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  Object.assign(form, { name: '', code: '', address: '', phone: '', status: 1 });
}

function openCreate() {
  editingId.value = null;
  resetForm();
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    name: row.name || '',
    code: row.code || '',
    address: row.address || '',
    phone: row.phone || '',
    status: Number(row.status)
  });
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}

function parseLayerColumns(row) {
  const unitCount = Number(row.drawerUnitCount) || 5;
  const layerCount = Number(row.drawerLayerCount) || 8;
  try {
    const values = Array.isArray(row.drawerLayerColumns)
      ? row.drawerLayerColumns
      : JSON.parse(row.drawerLayerColumns || '[]');
    if (Array.isArray(values) && values.length === unitCount && values.every(Array.isArray)) {
      return values.map((cabinet) => cabinet.map(Number));
    }
    if (Array.isArray(values) && values.length === layerCount) {
      return Array.from({ length: unitCount }, () => [Number(row.drawerTopColumnCount) || 6, ...values.map(Number)]);
    }
  } catch {
    // Fall back to the standard layout for invalid legacy data.
  }
  return Array.from({ length: unitCount }, () => [
    Number(row.drawerTopColumnCount) || 6,
    ...Array.from({ length: layerCount }, (_, index) => index === layerCount - 1 ? 3 : 6)
  ]);
}

function openLayout(row) {
  layoutStore.value = row;
  Object.assign(layoutForm, {
    drawerUnitCount: Number(row.drawerUnitCount) || 5,
    drawerLayerCount: Number(row.drawerLayerCount) || 8,
    drawerLayerColumns: parseLayerColumns(row),
    bigCabinetUnitCount: Number(row.bigCabinetUnitCount) || 5,
    bigCabinetLayerCount: Number(row.bigCabinetLayerCount) || 3
  });
  activeDrawerUnit.value = 1;
  layoutVisible.value = true;
}

function syncDrawerCabinets(drawerUnitCount) {
  const count = Number(drawerUnitCount);
  const cabinets = layoutForm.drawerLayerColumns.slice(0, count);
  while (cabinets.length < count) {
    cabinets.push([6, ...Array.from({ length: layoutForm.drawerLayerCount }, (_, index) =>
      index === layoutForm.drawerLayerCount - 1 ? 3 : 6
    )]);
  }
  layoutForm.drawerLayerColumns = cabinets;
  if (activeDrawerUnit.value > count) activeDrawerUnit.value = count;
}

function syncLayerColumns(layerCount) {
  const count = Number(layerCount);
  layoutForm.drawerLayerColumns = layoutForm.drawerLayerColumns.map((cabinet) => {
    const columns = cabinet.slice(0, count + 1);
    while (columns.length < count + 1) columns.push(3);
    return columns;
  });
}

async function saveStoreLayout() {
  if (!layoutStore.value) return;
  layoutSaving.value = true;
  try {
    await updateHerbLocationLayout({
      storeId: layoutStore.value.id,
      drawerUnitCount: layoutForm.drawerUnitCount,
      drawerLayerCount: layoutForm.drawerLayerCount,
      drawerLayerColumns: layoutForm.drawerLayerColumns.map((cabinet) => [...cabinet]),
      bigCabinetUnitCount: layoutForm.bigCabinetUnitCount,
      bigCabinetLayerCount: layoutForm.bigCabinetLayerCount
    });
    ElMessage.success('柜体布局已更新');
    layoutVisible.value = false;
    await loadData();
  } finally {
    layoutSaving.value = false;
  }
}

async function loadE6Config() {
  if (!e6Store.value) return;
  e6Loading.value = true;
  try {
    const [configData, mappings, operatorMappings, doctors] = await Promise.all([
      getE6StoreConfig(e6Store.value.id),
      getE6DoctorMappings({ storeId: e6Store.value.id }),
      getE6OperatorMappings({ storeId: e6Store.value.id }),
      getDoctors(true)
    ]);
    Object.assign(e6Config, configData?.config || { enabled: 0, hasApiKey: false, apiKeyHint: '', lastUsedAt: null });
    e6Mappings.value = mappings || [];
    e6OperatorMappings.value = operatorMappings?.list || [];
    e6Doctors.value = doctors || [];
  } finally {
    e6Loading.value = false;
  }
}

async function openE6Config(row) {
  e6Store.value = row;
  newApiKey.value = '';
  e6Visible.value = true;
  await loadE6Config();
}

async function persistE6Config(rotateApiKey = false) {
  if (!e6Store.value) return;
  e6Saving.value = true;
  try {
    const result = await saveStoreE6Config(e6Store.value.id, {
      enabled: e6Config.enabled,
      rotateApiKey
    });
    Object.assign(e6Config, result?.config || {});
    if (result?.config?.apiKey) newApiKey.value = result.config.apiKey;
    ElMessage.success(rotateApiKey ? 'API Key已生成' : 'E6配置已保存');
  } finally {
    e6Saving.value = false;
  }
}

function saveE6Config() {
  return persistE6Config(false);
}

async function rotateE6ApiKey() {
  if (e6Config.hasApiKey) {
    await ElMessageBox.confirm('重置后旧API Key立即失效，确认继续吗？', '重置API Key', { type: 'warning' });
  }
  await persistE6Config(true);
}

async function copyApiKey() {
  if (!newApiKey.value) return;
  await navigator.clipboard.writeText(newApiKey.value);
  ElMessage.success('API Key已复制');
}

function openMapping(row) {
  Object.assign(mappingForm, row
    ? { id: row.id, e6DoctorCode: row.e6DoctorCode, doctorId: row.doctorId, status: Number(row.status) }
    : { id: null, e6DoctorCode: '', doctorId: '', status: 1 });
  mappingVisible.value = true;
}

async function saveMapping() {
  if (!e6Store.value || !mappingForm.e6DoctorCode || !mappingForm.doctorId) {
    return ElMessage.warning('请填写E6医师编码并选择系统医生');
  }
  mappingSaving.value = true;
  try {
    const payload = { ...mappingForm, storeId: e6Store.value.id };
    if (mappingForm.id) await updateE6DoctorMapping(mappingForm.id, payload);
    else await createE6DoctorMapping(payload);
    mappingVisible.value = false;
    ElMessage.success('医师映射已保存');
    await loadE6Config();
  } finally {
    mappingSaving.value = false;
  }
}

async function toggleMapping(row, status) {
  await updateE6DoctorMapping(row.id, {
    storeId: row.storeId,
    e6DoctorCode: row.e6DoctorCode,
    doctorId: row.doctorId,
    status
  });
  ElMessage.success(status ? '映射已启用' : '映射已停用');
  await loadE6Config();
}

async function removeMapping(row) {
  await ElMessageBox.confirm(`确认删除医师编码“${row.e6DoctorCode}”的映射吗？`, '删除映射', { type: 'warning' });
  await deleteE6DoctorMapping(row.id);
  ElMessage.success('医师映射已删除');
  await loadE6Config();
}

function openOperatorMapping(row) {
  Object.assign(operatorMappingForm, row
    ? { id: row.id, e6OperatorName: row.e6OperatorName, operatorName: row.operatorName, status: Number(row.status) }
    : { id: null, e6OperatorName: '', operatorName: '', status: 1 });
  operatorMappingVisible.value = true;
}

async function saveOperatorMapping() {
  if (!e6Store.value || !operatorMappingForm.e6OperatorName || !operatorMappingForm.operatorName) {
    return ElMessage.warning('请填写E6操作员和显示操作员');
  }
  operatorMappingSaving.value = true;
  try {
    const payload = { ...operatorMappingForm, storeId: e6Store.value.id };
    if (operatorMappingForm.id) await updateE6OperatorMapping(operatorMappingForm.id, payload);
    else await createE6OperatorMapping(payload);
    operatorMappingVisible.value = false;
    ElMessage.success('操作员映射已保存');
    await loadE6Config();
  } finally {
    operatorMappingSaving.value = false;
  }
}

async function toggleOperatorMapping(row, status) {
  await updateE6OperatorMapping(row.id, {
    storeId: row.storeId,
    e6OperatorName: row.e6OperatorName,
    operatorName: row.operatorName,
    status
  });
  ElMessage.success(status ? '映射已启用' : '映射已停用');
  await loadE6Config();
}

async function removeOperatorMapping(row) {
  await ElMessageBox.confirm(`确认删除操作员“${row.e6OperatorName}”的映射吗？`, '删除映射', { type: 'warning' });
  await deleteE6OperatorMapping(row.id);
  ElMessage.success('操作员映射已删除');
  await loadE6Config();
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    if (editingId.value) await updateStore(editingId.value, form);
    else await createStore(form);
    ElMessage.success(editingId.value ? '门店已更新' : '门店已创建');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(row) {
  const nextStatus = Number(row.status) === 1 ? 0 : 1;
  await ElMessageBox.confirm(
    `确认${nextStatus === 1 ? '启用' : '停用'}门店“${row.name}”吗？`,
    '门店状态',
    { type: 'warning' }
  );
  await updateStore(row.id, { status: nextStatus });
  ElMessage.success('门店状态已更新');
  await loadData();
}

function openDelete(row) {
  selectedStore.value = row;
  deleteVisible.value = true;
}

async function handleDelete() {
  if (!selectedStore.value) return;
  deleting.value = true;
  try {
    await deleteStore(selectedStore.value.id);
    ElMessage.success('门店已删除');
    deleteVisible.value = false;
    await loadData();
  } finally {
    deleting.value = false;
  }
}

function handleSearch() {
  if (pagination.page !== 1) pagination.page = 1;
  else loadData();
}

function handleReset() {
  Object.assign(query, { keyword: '', status: '' });
  handleSearch();
}

watch(() => [pagination.page, pagination.pageSize], loadData);
onMounted(loadData);
</script>

<style scoped>
.search-form {
  display: grid;
  grid-template-columns: minmax(260px, 420px) 160px auto auto;
  gap: 10px;
  justify-content: start;
}

.table-actions {
  justify-content: center;
}

.layer-columns-grid {
  display: grid;
  width: 100%;
  grid-template-columns: 1fr 1fr;
  gap: 12px 18px;
}

.drawer-layout-tabs {
  width: 100%;
}

.layer-columns-grid label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.layer-columns-grid span {
  color: var(--app-muted);
  font-size: 13px;
}

.e6-config {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-section + .config-section {
  padding-top: 20px;
  border-top: 1px solid var(--app-border);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-heading h3,
.section-heading p {
  margin: 0;
}

.section-heading h3 {
  font-size: 16px;
}

.section-heading p {
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 13px;
}

.api-key-row {
  display: flex;
  width: 100%;
  gap: 10px;
}

.generated-key {
  margin-top: 10px;
}

@media (max-width: 720px) {
  .search-form {
    grid-template-columns: 1fr 1fr;
  }

  .search-form :deep(.el-input) {
    grid-column: 1 / -1;
  }

  .layer-columns-grid {
    grid-template-columns: 1fr;
  }
}
</style>
