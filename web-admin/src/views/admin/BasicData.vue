<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">基础资料</h1>
        <p class="page-subtitle">统一维护医生、处方来源、加工方式与提醒方式</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadCurrent">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="tab" @tab-change="loadCurrent">
        <el-tab-pane label="医生" name="doctor">
          <div class="toolbar">
            <el-button type="primary" :icon="Plus" @click="openDoctor()">新增医生</el-button>
          </div>
          <el-table v-loading="loading" :data="doctors" border row-key="id" table-layout="auto">
            <el-table-column prop="name" label="姓名" />
            <el-table-column prop="sort" label="排序" align="center" />
            <el-table-column label="状态" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status"
                  :active-value="1"
                  :inactive-value="0"
                  @change="(status) => changeDoctorStatus(row, status)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openDoctor(row)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="removeDoctor(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="字典" name="dictionary">
          <div class="toolbar">
            <el-segmented v-model="type" :options="dictionaryTypes" @change="loadDictionaries" />
            <el-button type="primary" :icon="Plus" @click="openDictionary()">新增字典项</el-button>
          </div>
          <el-table v-loading="loading" :data="dictionaries" border row-key="id" table-layout="auto">
            <el-table-column prop="code" label="编码" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="sort" label="排序" align="center" />
            <el-table-column label="状态" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status"
                  :active-value="1"
                  :inactive-value="0"
                  @change="(status) => changeDictionaryStatus(row, status)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openDictionary(row)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="removeDictionary(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="E6商品分类" name="e6-category">
          <div class="toolbar">
            <span class="tab-tip">全局映射，不区分门店；药店库存按分类编号显示映射后的分类名称。</span>
            <el-button type="primary" :icon="Plus" @click="openE6Category()">新增分类映射</el-button>
          </div>
          <el-table v-loading="loading" :data="e6Categories" border row-key="id" table-layout="auto">
            <el-table-column prop="categoryCode" label="分类编号" min-width="180" />
            <el-table-column prop="categoryName" label="分类名称" min-width="180" />
            <el-table-column prop="sort" label="排序" align="center" width="100" />
            <el-table-column label="状态" align="center" width="100">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status"
                  :active-value="1"
                  :inactive-value="0"
                  @change="(status) => changeE6CategoryStatus(row, status)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="150">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="openE6Category(row)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="removeE6Category(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="doctorDialog" :title="doctorForm.id ? '编辑医生' : '新增医生'" width="440px">
      <el-form label-position="top">
        <el-form-item label="医生姓名" required>
          <el-input v-model.trim="doctorForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="doctorForm.sort" :min="0" :max="99999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="doctorForm.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="doctorDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDoctor">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dictionaryDialog" :title="dictionaryForm.id ? '编辑字典项' : '新增字典项'" width="440px">
      <el-form label-position="top">
        <el-form-item label="编码" required>
          <el-input v-model.trim="dictionaryForm.code" maxlength="50" :disabled="Boolean(dictionaryForm.id)" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model.trim="dictionaryForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dictionaryForm.sort" :min="0" :max="99999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dictionaryForm.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictionaryDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDictionary">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="e6CategoryDialog" :title="e6CategoryForm.id ? '编辑 E6 商品分类映射' : '新增 E6 商品分类映射'" width="440px">
      <el-form label-position="top">
        <el-form-item label="分类编号" required>
          <el-input v-model.trim="e6CategoryForm.categoryCode" maxlength="64" :disabled="Boolean(e6CategoryForm.id)" />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model.trim="e6CategoryForm.categoryName" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="e6CategoryForm.sort" :min="0" :max="99999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="e6CategoryForm.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="e6CategoryDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitE6Category">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import {
  deleteDictionary,
  deleteDoctor,
  getDictionaries,
  getDoctors,
  saveDictionary,
  saveDoctor
} from '@/api/processing';
import {
  deleteE6PharmacyCategoryMapping,
  getE6PharmacyCategoryMappings,
  saveE6PharmacyCategoryMapping
} from '@/api/e6Pharmacy';

const dictionaryTypes = [
  { label: '处方来源', value: 'PrescriptionSource' },
  { label: '加工方式', value: 'ProcessType' },
  { label: '提醒方式', value: 'NotifyType' }
];
const tab = ref('doctor');
const type = ref('PrescriptionSource');
const doctors = ref([]);
const dictionaries = ref([]);
const e6Categories = ref([]);
const loading = ref(false);
const saving = ref(false);
const doctorDialog = ref(false);
const dictionaryDialog = ref(false);
const e6CategoryDialog = ref(false);
const doctorForm = reactive({ id: null, name: '', sort: 0, status: 1 });
const dictionaryForm = reactive({ id: null, type: '', code: '', name: '', sort: 0, status: 1 });
const e6CategoryForm = reactive({ id: null, categoryCode: '', categoryName: '', sort: 0, status: 1 });

async function loadDoctors() {
  loading.value = true;
  try {
    doctors.value = await getDoctors(true);
  } finally {
    loading.value = false;
  }
}

async function loadDictionaries() {
  loading.value = true;
  try {
    dictionaries.value = await getDictionaries(type.value, true);
  } finally {
    loading.value = false;
  }
}

function loadCurrent() {
  if (tab.value === 'doctor') return loadDoctors();
  if (tab.value === 'dictionary') return loadDictionaries();
  return loadE6Categories();
}

async function loadE6Categories() {
  loading.value = true;
  try {
    e6Categories.value = await getE6PharmacyCategoryMappings(true);
  } finally {
    loading.value = false;
  }
}

function openDoctor(row) {
  Object.assign(doctorForm, row ? { id: row.id, name: row.name, sort: row.sort, status: row.status } : { id: null, name: '', sort: 0, status: 1 });
  doctorDialog.value = true;
}

async function submitDoctor() {
  if (!doctorForm.name) return ElMessage.warning('请输入医生姓名');
  saving.value = true;
  try {
    await saveDoctor(doctorForm.id, doctorForm);
    doctorDialog.value = false;
    ElMessage.success('保存成功');
    await loadDoctors();
  } finally {
    saving.value = false;
  }
}

async function changeDoctorStatus(row, status) {
  await saveDoctor(row.id, { ...row, status });
  ElMessage.success(status ? '已启用' : '已停用');
  await loadDoctors();
}

async function removeDoctor(row) {
  await ElMessageBox.confirm(`确认删除医生“${row.name}”？`, '删除确认', { type: 'warning' });
  await deleteDoctor(row.id);
  ElMessage.success('删除成功');
  await loadDoctors();
}

function openDictionary(row) {
  Object.assign(dictionaryForm, row ? { id: row.id, type: row.type, code: row.code, name: row.name, sort: row.sort, status: row.status } : { id: null, type: type.value, code: '', name: '', sort: 0, status: 1 });
  dictionaryDialog.value = true;
}

async function submitDictionary() {
  if (!dictionaryForm.code || !dictionaryForm.name) return ElMessage.warning('请输入编码和名称');
  saving.value = true;
  try {
    await saveDictionary(dictionaryForm.id, dictionaryForm);
    dictionaryDialog.value = false;
    ElMessage.success('保存成功');
    await loadDictionaries();
  } finally {
    saving.value = false;
  }
}

async function changeDictionaryStatus(row, status) {
  await saveDictionary(row.id, { ...row, status });
  ElMessage.success(status ? '已启用' : '已停用');
  await loadDictionaries();
}

async function removeDictionary(row) {
  await ElMessageBox.confirm(`确认删除字典项“${row.name}”？`, '删除确认', { type: 'warning' });
  await deleteDictionary(row.id);
  ElMessage.success('删除成功');
  await loadDictionaries();
}

function openE6Category(row) {
  Object.assign(e6CategoryForm, row
    ? { id: row.id, categoryCode: row.categoryCode, categoryName: row.categoryName, sort: row.sort, status: row.status }
    : { id: null, categoryCode: '', categoryName: '', sort: 0, status: 1 });
  e6CategoryDialog.value = true;
}

async function submitE6Category() {
  if (!e6CategoryForm.categoryCode || !e6CategoryForm.categoryName) return ElMessage.warning('请输入分类编号和分类名称');
  saving.value = true;
  try {
    await saveE6PharmacyCategoryMapping(e6CategoryForm.id, e6CategoryForm);
    e6CategoryDialog.value = false;
    ElMessage.success('保存成功');
    await loadE6Categories();
  } finally {
    saving.value = false;
  }
}

async function changeE6CategoryStatus(row, status) {
  await saveE6PharmacyCategoryMapping(row.id, { ...row, status });
  ElMessage.success(status ? '已启用' : '已停用');
  await loadE6Categories();
}

async function removeE6Category(row) {
  await ElMessageBox.confirm(`确认删除分类映射“${row.categoryCode} / ${row.categoryName}”？`, '删除确认', { type: 'warning' });
  await deleteE6PharmacyCategoryMapping(row.id);
  ElMessage.success('删除成功');
  await loadE6Categories();
}

onMounted(() => Promise.all([loadDoctors(), loadDictionaries(), loadE6Categories()]));
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.tab-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 640px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
