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
        <el-form-item prop="identifier">
          <el-input v-model.trim="form.identifier" placeholder="请输入手机号或用户名" :prefix-icon="Iphone" @keyup.enter="handleLogin" />
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
import { reactive, ref, onMounted } from 'vue';
import { getPublicConfigs } from '@/api/systemConfig';
import { useRoute, useRouter } from 'vue-router';
import { Iphone, Lock } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const formRef = ref(null);
const errorCount = ref(Number(localStorage.getItem('loginErrorCount') || '0'));
const captchaConfig = ref({ enabled: false, id: '' });
let geetestInstance = null;

onMounted(async () => {
  try {
    const res = await getPublicConfigs();
    if (res && res.enable_captcha === '1' && res.geetest_captcha_id) {
      captchaConfig.value.enabled = true;
      captchaConfig.value.id = res.geetest_captcha_id;
      loadGeetestScript();
    }
  } catch (err) {
    console.error('Failed to load captcha config', err);
  }
});

function loadGeetestScript() {
  if (window.initGeetest4) return;
  const script = document.createElement('script');
  script.src = 'https://static.geetest.com/v4/gt4.js';
  document.head.appendChild(script);
}

function doLogin(geetestParams = null) {
  loading.value = true;
  userStore.login(
    {
      identifier: form.identifier,
      password: form.password,
      geetest: geetestParams,
      errorCount: errorCount.value
    },
    remember.value
  ).then(() => {
    localStorage.removeItem('loginErrorCount');
    errorCount.value = 0;
    ElMessage.success('登录成功');
    router.replace(getAdminRedirectTarget(route.query.redirect));
  }).catch((err) => {
    console.error(err);
    errorCount.value += 1;
    localStorage.setItem('loginErrorCount', errorCount.value.toString());
    if (geetestInstance) {
      geetestInstance.reset();
    }
  }).finally(() => {
    loading.value = false;
  });
}

const loading = ref(false);
const remember = ref(true);

const form = reactive({
  identifier: '',
  password: ''
});

function getAdminRedirectTarget(redirect) {
  const target = Array.isArray(redirect) ? redirect[0] : redirect;
  return typeof target === 'string' && target.startsWith('/admin') ? target : '/admin/dashboard';
}

const rules = {
  identifier: [{ required: true, message: '请输入手机号或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

async function handleLogin() {
  await formRef.value.validate();
  
  if (captchaConfig.value.enabled && errorCount.value >= 3) {
    if (!window.initGeetest4) {
      ElMessage.warning('正在加载安全组件，请稍候重试');
      return;
    }
    if (geetestInstance) {
      geetestInstance.showCaptcha();
      return;
    }
    window.initGeetest4({
      captchaId: captchaConfig.value.id,
      product: 'bind'
    }, function (captcha) {
      geetestInstance = captcha;
      captcha.onReady(() => {
        captcha.showCaptcha();
      }).onSuccess(() => {
        const result = captcha.getValidate();
        doLogin(result);
      }).onError((err) => {
        console.error(err);
        ElMessage.error('验证码加载失败，请重试');
      });
    });
  } else {
    doLogin();
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
  background: var(--el-bg-color);
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
