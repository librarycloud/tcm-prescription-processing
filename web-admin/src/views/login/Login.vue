<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-brand">
        <img src="@/assets/logo.svg" alt="logo" class="login-logo" />
        <div>
          <h1>中药处方加工与取药管理系统</h1>
          <p>统一管理处方、加工、待领取与取药核销</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
        <el-form-item prop="phone">
          <el-input v-model.trim="form.phone" placeholder="请输入手机号" :prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            type="password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <div class="login-options">
          <el-checkbox v-model="remember">记住登录状态</el-checkbox>
        </div>
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Iphone, Lock } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { isValidPhone } from '@/utils/phone';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const formRef = ref(null);
const loading = ref(false);
const remember = ref(true);

const form = reactive({
  phone: '',
  password: ''
});

function getAdminRedirectTarget(redirect) {
  const target = Array.isArray(redirect) ? redirect[0] : redirect;
  return typeof target === 'string' && target.startsWith('/admin') ? target : '/admin/dashboard';
}

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
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

async function handleLogin() {
  await formRef.value.validate();
  loading.value = true;
  try {
    await userStore.login(
      {
        phone: form.phone,
        password: form.password
      },
      remember.value
    );
    ElMessage.success('登录成功');
    await router.replace(getAdminRedirectTarget(route.query.redirect));
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(37, 99, 235, 0)),
    #f5f7fb;
}

.login-panel {
  width: min(440px, 100%);
  padding: 34px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.08);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 30px;
}

.login-logo {
  width: 48px;
  height: 48px;
}

.login-brand h1 {
  margin: 0;
  font-size: 21px;
  line-height: 1.3;
}

.login-brand p {
  margin: 6px 0 0;
  color: var(--app-muted);
  font-size: 14px;
}

.login-options {
  display: flex;
  justify-content: space-between;
  margin-bottom: 18px;
}

.login-button {
  width: 100%;
}
</style>
