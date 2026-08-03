<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">邮件设置</h1>
        <p class="page-subtitle">配置 SMTP、邮箱验证码和包裹邮件模板</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadSettings">刷新</el-button>
    </div>

    <el-card v-loading="loading" shadow="never">
      <template #header><span>SMTP 配置</span></template>
      <el-form class="smtp-form" label-position="top" :model="form">
        <el-form-item label="SMTP 主机"><el-input v-model.trim="form.host" placeholder="smtp.example.com" /></el-form-item>
        <el-form-item label="端口"><el-input-number v-model="form.port" :min="1" :max="65535" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model.trim="form.username" /></el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="form.passwordConfigured ? '已配置，留空不修改' : '请输入 SMTP 密码'" />
        </el-form-item>
        <el-form-item label="发件人名称"><el-input v-model.trim="form.fromName" /></el-form-item>
        <el-form-item label="发件邮箱"><el-input v-model.trim="form.fromEmail" /></el-form-item>
        <el-form-item label="SSL/TLS"><el-switch v-model="form.secure" /></el-form-item>
        <el-form-item label="启用邮件服务"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
        <el-button :icon="Promotion" @click="testVisible = true">发送测试邮件</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header><span>邮件模板</span></template>
      <el-table :data="templates" border table-layout="auto">
        <el-table-column prop="sceneName" label="场景" align="center" />
        <el-table-column prop="name" label="模板名称" align="center" />
        <el-table-column prop="subject" label="邮件主题" align="center" />
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column label="状态" align="center">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }"><el-button link type="primary" @click="editTemplate(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="templateVisible" title="编辑邮件模板" width="680px">
      <el-form v-if="templateForm" label-position="top">
        <el-form-item label="模板名称"><el-input v-model.trim="templateForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="邮件主题"><el-input v-model.trim="templateForm.subject" maxlength="255" /></el-form-item>
        <el-form-item label="邮件内容">
          <el-input v-model="templateForm.content" type="textarea" :rows="8" maxlength="10000" show-word-limit />
        </el-form-item>
        <el-form-item label="启用模板"><el-switch v-model="templateForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testVisible" title="测试邮件" width="460px">
      <el-form label-position="top"><el-form-item label="测试邮箱"><el-input v-model.trim="testEmail" /></el-form-item></el-form>
      <template #footer>
        <el-button @click="testVisible = false">取消</el-button>
        <el-button type="primary" :loading="testing" @click="sendTest">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Promotion, Refresh } from '@element-plus/icons-vue';
import { getEmailSettings, sendEmailTest, updateEmailConfig, updateEmailTemplate } from '@/api/email';

const loading = ref(false);
const saving = ref(false);
const savingTemplate = ref(false);
const testing = ref(false);
const templates = ref([]);
const templateVisible = ref(false);
const testVisible = ref(false);
const templateForm = ref(null);
const testEmail = ref('');
const form = reactive({ host: '', port: 465, secure: true, username: '', password: '', passwordConfigured: false, fromName: '', fromEmail: '', enabled: false });

async function loadSettings() {
  loading.value = true;
  try {
    const data = await getEmailSettings();
    Object.assign(form, { ...data.config, password: '' });
    templates.value = data.templates || [];
  } finally {
    loading.value = false;
  }
}

async function saveConfig() {
  saving.value = true;
  try {
    await updateEmailConfig(form);
    ElMessage.success('SMTP 配置已保存');
    await loadSettings();
  } finally {
    saving.value = false;
  }
}

function editTemplate(row) {
  templateForm.value = { ...row };
  templateVisible.value = true;
}

async function saveTemplate() {
  savingTemplate.value = true;
  try {
    await updateEmailTemplate(templateForm.value.id, templateForm.value);
    ElMessage.success('邮件模板已保存');
    templateVisible.value = false;
    await loadSettings();
  } finally {
    savingTemplate.value = false;
  }
}

async function sendTest() {
  testing.value = true;
  try {
    await sendEmailTest({ email: testEmail.value });
    ElMessage.success('测试邮件已发送');
    testVisible.value = false;
  } finally {
    testing.value = false;
  }
}

onMounted(loadSettings);
</script>

<style scoped>
.smtp-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}

.form-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 800px) {
  .smtp-form { grid-template-columns: 1fr; }
}
</style>
