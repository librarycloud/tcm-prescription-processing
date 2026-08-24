<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人资料</h1>
        <p class="page-subtitle">查看登录用户信息，需要修改时再进入编辑状态</p>
      </div>
      <el-button v-if="!editing" type="primary" @click="startEdit">修改资料</el-button>
    </div>

    <el-card v-loading="loading" class="form-card" shadow="never">
      <div v-if="!editing" class="profile-view">
        <el-avatar :size="72" :src="avatar" />
        <div class="profile-info">
          <div class="profile-name">{{ userStore.user?.nickname || '未设置昵称' }}</div>
          <div class="profile-phone">{{ userStore.user?.phone || '-' }}</div>
          <el-tag type="primary" effect="plain">
            {{ roleText(userStore.user) }}
          </el-tag>
          <div v-if="userStore.isStoreAdmin" class="profile-phone">
            所属门店：{{ userStore.user?.store?.name || '-' }}
          </div>
        </div>
      </div>

      <el-form
        v-else
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="96px"
        @submit.prevent
      >
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model.trim="form.nickname" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model.trim="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="不修改请留空"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
          <el-button @click="cancelEdit">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import avatar from '@/assets/avatar.svg';
import { updateProfile } from '@/api/user';
import { useUserStore } from '@/stores/user';
import { isValidPhone } from '@/utils/phone';
import { roleText } from '@/utils/permission';

const userStore = useUserStore();
const formRef = ref(null);
const editing = ref(false);
const loading = ref(false);
const saving = ref(false);

const form = reactive({
  nickname: '',
  phone: '',
  password: '',
  confirmPassword: ''
});

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isValidPhone(value)) callback(new Error('请输入正确的手机号'));
        else callback();
      },
      trigger: 'blur'
    }
  ],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (!value || value.length >= 6) callback();
        else callback(new Error('密码至少 6 位'));
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        if (!form.password && !value) {
          callback();
          return;
        }
        if (!form.password) {
          callback(new Error('请先输入新密码'));
          return;
        }
        if (value !== form.password) {
          callback(new Error('两次密码不一致'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
};

function fillForm() {
  form.nickname = userStore.user?.nickname || '';
  form.phone = userStore.user?.phone || '';
  form.password = '';
  form.confirmPassword = '';
}

function startEdit() {
  fillForm();
  editing.value = true;
}

function cancelEdit() {
  fillForm();
  editing.value = false;
}

async function handleSubmit() {
  await formRef.value.validate();
  saving.value = true;
  try {
    const data = {
      nickname: form.nickname,
      phone: form.phone
    };
    if (form.password) data.password = form.password;
    const authData = await updateProfile(data);
    userStore.updateAuth(authData);
    ElMessage.success('保存成功');
    editing.value = false;
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.profile-view {
  display: flex;
  align-items: center;
  gap: 18px;
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-name {
  font-size: 20px;
  font-weight: 700;
}

.profile-phone {
  color: var(--app-muted);
}
</style>
