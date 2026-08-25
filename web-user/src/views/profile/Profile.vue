<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人资料</h1>
        <p class="page-subtitle">查看登录用户信息，需要修改时再进入编辑状态</p>
      </div>
      <el-button v-if="!editing" type="primary" @click="startEdit">修改资料</el-button>
    </div>

    <el-card class="form-card" shadow="never" v-loading="loading">
      <div v-if="!editing" class="profile-view">
        <el-avatar :size="72" :src="avatar" />
        <div class="profile-info">
          <div class="profile-name">{{ userStore.user?.nickname || '未设置昵称' }}</div>
          <div class="profile-phone">{{ userStore.user?.phone || '-' }}</div>
          <div class="profile-email">{{ userStore.user?.email || '未绑定邮箱' }}</div>
          <el-tag :type="userStore.user?.emailVerified ? 'success' : 'info'" effect="plain">
            {{ userStore.user?.emailVerified ? '邮箱已验证' : '邮箱未验证' }}
          </el-tag>
          <el-tag type="primary" effect="plain">用户</el-tag>
        </div>
      </div>

      <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="昵称" prop="nickname"><el-input v-model.trim="form.nickname" maxlength="30" show-word-limit /></el-form-item>
        <el-form-item label="用户名" prop="username"><el-input v-model.trim="form.username" maxlength="64" placeholder="可选，仅英文和数字且不能是纯数字" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model.trim="form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="邮箱" prop="email">
          <div class="email-control">
            <el-input v-model.trim="form.email" maxlength="191" placeholder="请输入邮箱" />
            <el-button :disabled="emailCountdown > 0 || !isValidEmail(form.email)" :loading="emailSending" @click="handleSendEmailCode">
              {{ emailCountdown > 0 ? `${emailCountdown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="验证码" prop="emailCode">
          <div class="email-control"><el-input v-model.trim="form.emailCode" maxlength="6" placeholder="请输入邮箱验证码" /><el-button type="success" :loading="emailVerifying" :disabled="!isValidEmail(form.email) || form.emailCode.length !== 6" @click="handleVerifyEmail">确认邮箱</el-button></div>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="不修改请留空" maxlength="32" @input="handlePasswordInput" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入新密码" maxlength="32" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button><el-button @click="cancelEdit">取消</el-button></el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import avatar from '@/assets/avatar.svg';
import { sendEmailCode, updateProfile, verifyEmail } from '@/api/user';
import { useUserStore } from '@/stores/user';
import { isValidPhone } from '@/utils/phone';

const userStore = useUserStore();
const formRef = ref(null);
const editing = ref(false);
const loading = ref(false);
const saving = ref(false);
const emailSending = ref(false);
const emailVerifying = ref(false);
const emailCountdown = ref(0);
let countdownTimer = null;

const form = reactive({
  nickname: '',
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
  email: '',
  emailCode: ''
});

function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || '').trim());
}

const rules = {
  username: [{ validator: (_rule, value, callback) => {
    if (!value || (/^[A-Za-z0-9]{2,64}$/.test(value) && /[A-Za-z]/.test(value))) callback();
    else callback(new Error('用户名需为2-64位英文数字且不能是纯数字'));
  }, trigger: 'blur' }],
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
      trigger: ['blur', 'change']
    }
  ]
};

function fillForm() {
  form.nickname = userStore.user?.nickname || '';
  form.phone = userStore.user?.phone || '';
  form.username = userStore.user?.username || '';
  form.password = '';
  form.confirmPassword = '';
  form.email = userStore.user?.email || '';
  form.emailCode = '';
}

async function handleSendEmailCode() {
  if (!isValidEmail(form.email)) return;
  emailSending.value = true;
  try {
    await sendEmailCode(form.email);
    ElMessage.success('验证码已发送');
    emailCountdown.value = 60;
    countdownTimer = window.setInterval(() => {
      emailCountdown.value -= 1;
      if (emailCountdown.value <= 0) window.clearInterval(countdownTimer);
    }, 1000);
  } finally {
    emailSending.value = false;
  }
}

async function handleVerifyEmail() {
  emailVerifying.value = true;
  try {
    const authData = await verifyEmail(form.email, form.emailCode);
    userStore.updateAuth(authData);
    form.emailCode = '';
    ElMessage.success('邮箱绑定成功');
  } finally {
    emailVerifying.value = false;
  }
}

function startEdit() {
  fillForm();
  editing.value = true;
}

function cancelEdit() {
  fillForm();
  editing.value = false;
}

function handlePasswordInput() {
  if (form.confirmPassword) {
    formRef.value?.validateField('confirmPassword').catch(() => {});
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  if (String(form.email || '').trim().toLowerCase() !== String(userStore.user?.email || '').trim().toLowerCase()) {
    ElMessage.warning('请先完成邮箱验证码验证，邮箱才会保存');
    return;
  }

  saving.value = true;
  try {
    const data = {
      nickname: form.nickname,
      username: form.username || null,
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

onBeforeUnmount(() => {
  if (countdownTimer) window.clearInterval(countdownTimer);
});
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

.profile-info {
  align-items: flex-start;
}

.profile-email {
  color: var(--app-muted);
}

.email-control {
  display: flex;
  width: 100%;
  gap: 8px;
}

.email-control :deep(.el-input) {
  min-width: 0;
}
</style>
