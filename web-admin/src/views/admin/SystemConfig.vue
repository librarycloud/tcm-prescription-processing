<template>
  <div class="page system-config-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">系统设置</h1>
        <p class="page-subtitle">集中管理系统安全、存储上传、短信服务、邮件服务及法律文档</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="system-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="常规与安全" name="general">
        <el-card shadow="never" class="config-card">
          <template #header>
            <div class="card-header">
              <span>安全验证与登录控制</span>
            </div>
          </template>

          <el-form ref="formRef" :model="form" label-width="180px" style="max-width: 680px;">
            <el-form-item label="开启验证码登录">
              <el-switch v-model="form.enable_captcha" active-value="1" inactive-value="0" />
              <div class="form-tip">开启后，管理后台和店员 App 登录将强制要求完成验证码校验。</div>
            </el-form-item>
            
            <template v-if="form.enable_captcha === '1'">
              <el-form-item label="极验 4.0 Captcha ID">
                <el-input v-model="form.geetest_captcha_id" placeholder="请输入申请到的极验 Captcha ID" />
              </el-form-item>
              
              <el-form-item label="极验 4.0 Captcha Key">
                <el-input v-model="form.geetest_captcha_key" type="password" show-password placeholder="请输入极验 Captcha Key" />
              </el-form-item>
            </template>

            <el-form-item>
              <el-button type="primary" :loading="loading" @click="handleSave">保存配置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="存储与上传" name="upload">
        <UploadSettings :embedded="true" />
      </el-tab-pane>

      <el-tab-pane label="短信服务" name="sms">
        <SmsSettings :embedded="true" />
      </el-tab-pane>

      <el-tab-pane label="邮件服务" name="email">
        <EmailSettings :embedded="true" />
      </el-tab-pane>

      <el-tab-pane label="法律文档" name="legal">
        <LegalDocs :embedded="true" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getSystemConfigs, updateSystemConfigs } from '@/api/systemConfig';
import UploadSettings from './UploadSettings.vue';
import SmsSettings from './SmsSettings.vue';
import EmailSettings from './EmailSettings.vue';
import LegalDocs from './LegalDocs.vue';

const route = useRoute();
const router = useRouter();

const validTabs = ['general', 'upload', 'sms', 'email', 'legal'];
const activeTab = ref(
  validTabs.includes(route.query.tab) ? route.query.tab : 'general'
);

function handleTabChange(tab) {
  router.replace({ query: { ...route.query, tab } });
}

watch(
  () => route.query.tab,
  (tab) => {
    if (tab && validTabs.includes(tab) && tab !== activeTab.value) {
      activeTab.value = tab;
    }
  }
);

const loading = ref(false);
const formRef = ref(null);
const form = ref({
  enable_captcha: '0',
  geetest_captcha_id: '',
  geetest_captcha_key: ''
});

async function fetchConfigs() {
  try {
    const res = await getSystemConfigs();
    if (res) {
      form.value.enable_captcha = res.enable_captcha || '0';
      form.value.geetest_captcha_id = res.geetest_captcha_id || '';
      form.value.geetest_captcha_key = res.geetest_captcha_key || '';
    }
  } catch (error) {
    console.error(error);
  }
}

async function handleSave() {
  try {
    loading.value = true;
    await updateSystemConfigs(form.value);
    ElMessage.success('系统配置保存成功！');
  } catch (error) {
    console.error(error);
    ElMessage.error('保存失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchConfigs();
});
</script>

<style scoped>
.system-config-page {
  padding: 0;
}
.system-tabs {
  padding: 0 16px 16px;
  border: 1px solid var(--app-border);
  background: var(--el-bg-color);
}
.config-card {
  margin-top: 12px;
}
.card-header {
  font-weight: 600;
  font-size: 14px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
:deep(.embedded-tab-pane) {
  padding: 12px 0 0;
}
</style>
