<template>
  <div class="legal-docs-page">
    <div class="page-header">
      <h2>法律文档配置</h2>
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>

    <el-alert
      title="配置说明"
      type="info"
      description="此处使用 Markdown 格式编辑《隐私政策》和《用户协议》。内容将自动在 App 端渲染为图文排版。如需插入标题请使用 '#' 开头，无需使用 HTML 标签。"
      show-icon
      :closable="false"
      class="mb-4"
    />

    <el-card v-loading="loading" shadow="never" class="docs-card">
      <el-tabs v-model="activeTab" class="docs-tabs">
        <el-tab-pane label="《隐私政策》" name="privacy_policy">
          <el-input
            v-model="form.privacy_policy"
            type="textarea"
            :autosize="{ minRows: 20, maxRows: 30 }"
            placeholder="请输入隐私政策内容 (Markdown格式)"
            class="markdown-input"
          />
        </el-tab-pane>
        <el-tab-pane label="《用户协议》" name="user_agreement">
          <el-input
            v-model="form.user_agreement"
            type="textarea"
            :autosize="{ minRows: 20, maxRows: 30 }"
            placeholder="请输入用户协议内容 (Markdown格式)"
            class="markdown-input"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import request from '@/api/request';

const activeTab = ref('privacy_policy');
const loading = ref(false);
const saving = ref(false);

const form = reactive({
  privacy_policy: '',
  user_agreement: ''
});

const loadConfigs = async () => {
  loading.value = true;
  try {
    const res = await request.get('/admin/system/legal-docs');
    form.privacy_policy = res.privacy_policy || '';
    form.user_agreement = res.user_agreement || '';
  } catch {
    ElMessage.error('加载配置失败');
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  saving.value = true;
  try {
    await request.put('/admin/system/legal-docs', form);
    ElMessage.success('保存成功');
  } catch {
    ElMessage.error('保存失败');
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadConfigs();
});
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}
.mb-4 {
  margin-bottom: 16px;
}
.docs-card {
  min-height: 600px;
}
.markdown-input {
  font-family: Consolas, Monaco, "Courier New", monospace;
  font-size: 14px;
}
</style>
