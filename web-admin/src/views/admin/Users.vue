<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">
          {{ userStore.isStoreAdmin ? '查看并修改普通用户资料' : '查看用户资料、联系方式和最后登录信息' }}
        </p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
        <el-button v-if="userStore.isSuperAdmin" type="primary" :icon="Plus" @click="openCreate">
          新增用户
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-form class="search-form" @submit.prevent="handleSearch">
        <el-input
          v-model.trim="keyword"
          clearable
          placeholder="搜索姓名、昵称、手机号、邮箱或备注"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty>
          <EmptyView description="暂无用户" />
        </template>
        <el-table-column prop="nickname" label="用户昵称" align="center">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" align="center">
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" align="center">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" align="center">
          <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" align="center">
          <template #default="{ row }">
            <span>{{ truncate(row.email) }}</span>
            <el-tag v-if="row.email" size="small" :type="row.emailVerified ? 'success' : 'info'">
              {{ row.emailVerified ? '已验证' : '未验证' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" align="center">
          <template #default="{ row }">{{ truncate(row.remark) }}</template>
        </el-table-column>
        <el-table-column prop="role" label="账号角色" align="center">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" effect="plain">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属门店" align="center">
          <template #default="{ row }">{{ row.store?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lastLoginIp" label="最近登录 IP" align="center">
          <template #default="{ row }">{{ row.lastLoginIp || '-' }}</template>
        </el-table-column>
        <el-table-column label="登录归属地" align="center">
          <template #default="{ row }">{{ locationText(row.lastLoginLocation) }}</template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录时间" align="center">
          <template #default="{ row }">{{ formatDate(row.lastLoginAt, '从未登录') }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-if="userStore.isSuperAdmin"
                link
                type="danger"
                :disabled="Number(row.id) === Number(userStore.user?.id)"
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

    <el-dialog
      v-model="editVisible"
      :title="selectedUser ? '编辑用户' : '新增用户'"
      width="620px"
      align-center
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
        <el-form-item label="用户昵称" prop="nickname">
          <el-input v-model.trim="form.nickname" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" maxlength="64" placeholder="可选，2-64位英文和数字，不能是纯数字" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model.trim="form.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model.trim="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="selectedUser ? '重置密码' : '登录密码'" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            maxlength="32"
            :placeholder="selectedUser ? '不修改请留空' : '请输入 6-32 位密码'"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            maxlength="32"
            placeholder="再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <ConfirmDialog
      v-model="deleteVisible"
      title="确认删除用户"
      :content="`确认删除用户 ${selectedUser?.nickname || selectedUser?.phone || ''}（${selectedUser?.phone || '-'}）吗？删除后无法恢复。`"
      confirm-type="danger"
      :loading="deleting"
      @confirm="handleDelete"
    />
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { createAdminUser, deleteAdminUser, getAdminUsers, updateAdminUser } from '@/api/adminUser';
import { useUserStore } from '@/stores/user';
import { formatDate } from '@/utils/date';
import { isValidPhone, maskPhone } from '@/utils/phone';
import { ROLES } from '@/utils/permission';

const userStore = useUserStore();
const formRef = ref(null);
const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const editVisible = ref(false);
const deleteVisible = ref(false);
const keyword = ref('');
const list = ref([]);
const selectedUser = ref(null);

const pagination = reactive({ page: 1, pageSize: 10, total: 0 });
const form = reactive({
  nickname: '',
  username: '',
  name: '',
  phone: '',
  email: '',
  remark: '',
  password: '',
  confirmPassword: ''
});

const rules = {
  username: [{
    validator: (_rule, value, callback) => {
      const username = String(value || '').trim();
      if (!username || (/^[A-Za-z0-9]{2,64}$/.test(username) && /[A-Za-z]/.test(username))) callback();
      else callback(new Error('用户名需为2-64位英文和数字，且不能是纯数字'));
    },
    trigger: 'blur'
  }],
  email: [
    {
      type: 'email',
      message: '请输入正确的邮箱地址',
      trigger: ['blur', 'change']
    }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (isValidPhone(value)) callback();
        else callback(new Error('请输入正确的手机号'));
      },
      trigger: 'blur'
    }
  ],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (!selectedUser.value && !value) callback(new Error('请输入登录密码'));
        else if (!value || (value.length >= 6 && value.length <= 32)) callback();
        else callback(new Error('密码长度必须为 6-32 位'));
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        if (!form.password && !value) callback();
        else if (!form.password) callback(new Error('请先输入重置密码'));
        else if (value !== form.password) callback(new Error('两次密码不一致'));
        else callback();
      },
      trigger: ['blur', 'change']
    }
  ]
};

function locationText(location) {
  if (!location) return '-';
  const parts = [location.province || location.country, location.city, location.isp].filter(
    Boolean
  );
  return [...new Set(parts)].join(' ') || '-';
}

function truncate(value) {
  const text = String(value || '').trim();
  return text ? (text.length > 5 ? `${text.slice(0, 5)}...` : text) : '-';
}

function roleLabel(role) {
  return (
    {
      [ROLES.SUPER_ADMIN]: '全局管理员',
      [ROLES.USER]: '普通用户',
      [ROLES.STORE_ADMIN]: '门店管理员'
    }[Number(role)] || '未知角色'
  );
}

function roleTagType(role) {
  return (
    {
      [ROLES.SUPER_ADMIN]: 'danger',
      [ROLES.USER]: 'info',
      [ROLES.STORE_ADMIN]: 'warning'
    }[Number(role)] || 'info'
  );
}

async function loadData() {
  loading.value = true;
  try {
    const data = await getAdminUsers({
      keyword: keyword.value,
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  if (pagination.page !== 1) pagination.page = 1;
  else loadData();
}

function handleReset() {
  keyword.value = '';
  handleSearch();
}

function openEdit(row) {
  selectedUser.value = row;
  form.nickname = row.nickname || '';
  form.name = row.name || '';
  form.username = row.username || '';
  form.phone = row.phone || '';
  form.email = row.email || '';
  form.remark = row.remark || '';
  form.password = '';
  form.confirmPassword = '';
  editVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}

function openCreate() {
  selectedUser.value = null;
  Object.assign(form, {
    nickname: '',
    name: '',
    username: '',
    phone: '',
    email: '',
    remark: '',
    password: '',
    confirmPassword: ''
  });
  editVisible.value = true;
  nextTick(() => formRef.value?.clearValidate());
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  saving.value = true;
  try {
    const payload = {
      nickname: form.nickname,
      name: form.name,
      username: form.username || null,
      phone: form.phone,
      remark: form.remark
    };
    if (form.password) payload.password = form.password;
    if (selectedUser.value) await updateAdminUser(selectedUser.value.id, payload);
    else await createAdminUser(payload);
    if (selectedUser.value && Number(selectedUser.value.id) === Number(userStore.user?.id)) {
      await userStore.refreshProfile();
    }
    ElMessage.success(selectedUser.value ? '用户信息已更新' : '用户已创建');
    editVisible.value = false;
    await loadData();
  } finally {
    saving.value = false;
  }
}

function openDelete(row) {
  selectedUser.value = row;
  deleteVisible.value = true;
}

async function handleDelete() {
  if (!selectedUser.value) return;
  deleting.value = true;
  try {
    await deleteAdminUser(selectedUser.value.id);
    ElMessage.success('用户已删除');
    deleteVisible.value = false;
    if (list.value.length === 1 && pagination.page > 1) pagination.page -= 1;
    else await loadData();
  } finally {
    deleting.value = false;
  }
}

watch(
  () => [pagination.page, pagination.pageSize],
  () => loadData()
);

onMounted(loadData);
</script>

<style scoped>
.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: start;
}

.page-actions {
  display: flex;
  gap: 10px;
}

.table-actions {
  justify-content: center;
}

@media (max-width: 640px) {
  :deep(.el-dialog) {
    width: calc(100% - 24px);
  }

  .search-form {
    display: flex;
  }

  .search-form :deep(.el-input) {
    flex: 1 1 100%;
  }
}
</style>
