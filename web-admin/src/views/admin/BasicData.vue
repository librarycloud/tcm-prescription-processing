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

const dictionaryTypes = [
  { label: '处方来源', value: 'PrescriptionSource' },
  { label: '加工方式', value: 'ProcessType' },
  { label: '提醒方式', value: 'NotifyType' }
];
const tab = ref('doctor');
const type = ref('PrescriptionSource');
const doctors = ref([]);
const dictionaries = ref([]);
const loading = ref(false);
const saving = ref(false);
const doctorDialog = ref(false);
const dictionaryDialog = ref(false);
const doctorForm = reactive({ id: null, name: '', sort: 0, status: 1 });
const dictionaryForm = reactive({ id: null, type: '', code: '', name: '', sort: 0, status: 1 });

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
  return tab.value === 'doctor' ? loadDoctors() : loadDictionaries();
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

onMounted(() => Promise.all([loadDoctors(), loadDictionaries()]));
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

@media (max-width: 640px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
