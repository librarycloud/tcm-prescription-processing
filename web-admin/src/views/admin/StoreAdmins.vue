<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">门店账号</h1>
        <p class="page-subtitle">维护本门店管理员与员工账号，并可双向调整权限</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增账号</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="search-form" @submit.prevent="handleSearch">
        <el-input
          v-model.trim="query.keyword"
          clearable
          placeholder="搜索姓名、昵称、手机号或门店"
        />
        <el-select
          v-if="userStore.isSuperAdmin"
          v-model="query.storeId"
          clearable
          filterable
          placeholder="全部门店"
          @change="handleSearch"
        >
          <el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="全部状态" @change="handleSearch">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty><EmptyView description="暂无门店账号" /></template>
        <el-table-column prop="nickname" label="昵称" align="center">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" align="center">
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" align="center">
          <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column label="所属门店" align="center">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" align="center">
          <template #default="{ row }">
            <el-tag :type="Number(row.role) === ROLES.STORE_ADMIN ? 'primary' : 'info'" effect="plain">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" align="center">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" effect="plain">
              {{ Number(row.status) === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" align="center">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="Number(row.status) === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row)"
              >
                {{ Number(row.status) === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button
                v-if="userStore.isSuperAdmin && Number(row.role) === ROLES.STORE_ADMIN"
                link
                type="danger"
                @click="openDelete(row)"
              >
                删除
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
        <el-form-item label="手机号" prop="phone">
          <el-autocomplete
            v-model="form.phone"
            :fetch-suggestions="searchUsers"
            value-key="phone"
            maxlength="11"
            clearable
            @input="handlePhoneInput"
            @select="handleUserSelect"
          >
            <template #default="{ item }">
              <div class="user-suggestion">
                <span>{{ item.phone }}</span>
                <span class="user-suggestion-meta"
                  >{{ item.nickname || item.name || '未填写姓名' }} ·
                  {{ roleLabel(item.role) }}</span
                >
              </div>
            </template>
          </el-autocomplete>
          <div v-if="form.userId" class="selected-user">已选择普通用户账号，保存后直接绑定门店</div>
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model.trim="form.nickname" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model.trim="form.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="所属门店" prop="storeId">
          <el-select
            v-model="form.storeId"
            filterable
            placeholder="请选择所属门店"
            :disabled="!userStore.isSuperAdmin"
          >
            <el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号权限" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio-button :value="ROLES.STORE_ADMIN">门店管理员</el-radio-button>
            <el-radio-button :value="ROLES.STORE_STAFF">门店员工</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="editingId ? '重置密码' : '登录密码'" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="32"
            :placeholder="editingId || form.userId ? '不修改请留空' : '请输入 6-32 位密码'"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            maxlength="32"
            placeholder="再次输入密码"
          />
        </el-form-item>
        <el-form-item label="账号状态" prop="status">
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

    <ConfirmDialog
      v-model="deleteVisible"
      title="确认删除账号"
      :content="`确认删除门店账号 ${selectedAdmin?.nickname || selectedAdmin?.phone || ''} 吗？存在包裹审计记录时请改为禁用账号。`"
      confirm-type="danger"
      :loading="deleting"
      @confirm="handleDelete"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { getStores } from '@/api/store';
import { matchAdminUsers } from '@/api/adminUser';
import {
  createStoreAdmin as createStoreAdminApi,
  deleteStoreAdmin,
  getStoreAdmins,
  updateStoreAdmin
} from '@/api/storeAdmin';
import { formatDate } from '@/utils/date';
import { isValidPhone, maskPhone } from '@/utils/phone';
import { ROLES } from '@/utils/permission';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const formRef = ref(null);
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const dialogVisible = ref(false);
const deleteVisible = ref(false);
const editingId = ref(null);
const selectedAdmin = ref(null);
const selectedUserPhone = ref('');
const list = ref([]);
const stores = ref([]);
const query = reactive({ keyword: '', storeId: '', status: '' });
const pagination = reactive({ page: 1, pageSize: 10, total: 0 });
const form = reactive({
  userId: null,
  phone: '',
  nickname: '',
  name: '',
  storeId: null,
  role: ROLES.STORE_ADMIN,
  password: '',
  confirmPassword: '',
  status: 1
});
const dialogTitle = computed(() =>
  editingId.value ? '编辑门店账号' : form.userId ? '设置门店账号权限' : '新增门店账号'
);

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        isValidPhone(value) ? callback() : callback(new Error('请输入正确的手机号')),
      trigger: 'blur'
    }
  ],
  storeId: [{ required: true, message: '请选择所属门店', trigger: 'change' }],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (!editingId.value && !form.userId && !value) callback(new Error('请输入登录密码'));
        else if (value && (value.length < 6 || value.length > 32))
          callback(new Error('密码长度必须为 6-32 位'));
        else callback();
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        if (!form.password && !value) callback();
        else if (value !== form.password) callback(new Error('两次密码不一致'));
        else callback();
      },
      trigger: ['blur', 'change']
    }
  ]
};

async function loadStores() {
  if (userStore.isSuperAdmin) {
    const data = await getStores({ page: 1, pageSize: 100 });
    stores.value = data?.list || [];
    return;
  }
  const store = userStore.user?.store;
  stores.value = store ? [store] : [];
}

async function loadData() {
  loading.value = true;
  try {
    const data = await getStoreAdmins({
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

function resetForm() {
  selectedUserPhone.value = '';
  Object.assign(form, {
    userId: null,
    phone: '',
    nickname: '',
    name: '',
    storeId: userStore.isSuperAdmin ? null : Number(userStore.user?.storeId) || null,
    role: ROLES.STORE_ADMIN,
    password: '',
    confirmPassword: '',
    status: 1
  });
}

function openCreate() {
  editingId.value = null;
  resetForm();
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}

function openEdit(row) {
  editingId.value = row.id;
  selectedUserPhone.value = '';
  Object.assign(form, {
    userId: null,
    phone: row.phone || '',
    nickname: row.nickname || '',
    name: row.name || '',
    storeId: row.storeId,
    role: Number(row.role),
    password: '',
    confirmPassword: '',
    status: Number(row.status)
  });
  dialogVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload = {
      phone: form.phone,
      nickname: form.nickname,
      name: form.name,
      storeId: form.storeId,
      role: form.role,
      status: form.status
    };
    if (form.userId) payload.userId = form.userId;
    if (form.password) payload.password = form.password;
    if (editingId.value) await updateStoreAdmin(editingId.value, payload);
    else await createStoreAdminApi(payload);
    ElMessage.success(editingId.value ? '账号权限已更新' : '账号已创建');
    dialogVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

async function searchUsers(queryString, callback) {
  const phone = String(queryString || '').trim();
  if (editingId.value || phone.length < 7) {
    callback([]);
    return;
  }
  try {
    const users = (await matchAdminUsers(phone)) || [];
    const candidates = users.filter((user) => Number(user.role) === ROLES.USER);
    callback(candidates);
  } catch {
    callback([]);
  }
}

function handleUserSelect(user) {
  form.userId = user.id;
  selectedUserPhone.value = user.phone;
  form.phone = user.phone;
  form.nickname = user.nickname || '';
  form.name = user.name || '';
  form.status = Number(user.status) === 0 ? 0 : 1;
  nextTick(() => formRef.value?.clearValidate(['phone', 'password', 'confirmPassword']));
}

function handlePhoneInput(value) {
  if (form.userId && String(value || '') !== selectedUserPhone.value) {
    form.userId = null;
    selectedUserPhone.value = '';
  }
}

function roleLabel(role) {
  return (
    {
      [ROLES.SUPER_ADMIN]: '全局管理员',
      [ROLES.USER]: '普通用户',
      [ROLES.STORE_ADMIN]: '门店管理员',
      [ROLES.STORE_STAFF]: '门店员工'
    }[Number(role)] || '未知角色'
  );
}

async function toggleStatus(row) {
  const status = Number(row.status) === 1 ? 0 : 1;
  await ElMessageBox.confirm(`确认${status ? '启用' : '禁用'}账号 ${row.phone} 吗？`, '账号状态', {
    type: 'warning'
  });
  await updateStoreAdmin(row.id, { status });
  ElMessage.success('账号状态已更新');
  await loadData();
}

function openDelete(row) {
  selectedAdmin.value = row;
  deleteVisible.value = true;
}

async function handleDelete() {
  deleting.value = true;
  try {
    await deleteStoreAdmin(selectedAdmin.value.id);
    ElMessage.success('账号已删除');
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
  Object.assign(query, { keyword: '', storeId: '', status: '' });
  handleSearch();
}

watch(() => [pagination.page, pagination.pageSize], loadData);
onMounted(() => Promise.all([loadStores(), loadData()]));
</script>

<style scoped>
.search-form {
  display: grid;
  grid-template-columns: minmax(240px, 360px) 180px 150px auto auto;
  gap: 10px;
  justify-content: start;
}

.table-actions {
  justify-content: center;
}

.user-suggestion {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.user-suggestion-meta,
.selected-user {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.selected-user {
  line-height: 20px;
}

@media (max-width: 800px) {
  .search-form {
    display: flex;
  }

  .search-form :deep(.el-input) {
    flex: 1 1 100%;
  }
}
</style>
