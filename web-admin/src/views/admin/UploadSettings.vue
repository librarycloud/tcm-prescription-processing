<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">S3/OSS 上传设置</h1>
        <p class="page-subtitle">配置兼容 S3 协议的对象存储（阿里云、腾讯云、MinIO 等）</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadSettings">刷新</el-button>
    </div>

    <el-alert
      title="配置说明"
      description="配置保存后即时生效。前端在传图片或文件时，将不再发送给本服务器，而是直接上传至您配置的对象存储中，大文件天然支持断点续传。如果您使用本服务器自建 MinIO，将 Endpoint 填入即可（例如 http://192.168.1.100:9000）。"
      type="info"
      show-icon
      class="mb-20"
    />

    <el-card v-loading="loading" shadow="never">
      <template #header><span>S3 基础配置</span></template>
      <el-form class="config-form" label-position="top" :model="form">
        <el-form-item label="Endpoint (连接地址)">
          <el-input v-model.trim="form.endpoint" placeholder="例如: oss-cn-hangzhou.aliyuncs.com 或 http://192.168.1.10:9000" />
        </el-form-item>
        <el-form-item label="Region (区域)">
          <el-input v-model.trim="form.region" placeholder="例如: us-east-1 或 oss-cn-hangzhou" />
        </el-form-item>
        <el-form-item label="Bucket (存储桶名称)">
          <el-input v-model.trim="form.bucket" placeholder="例如: tcm-uploads" />
        </el-form-item>
        <el-form-item label="Access Key (访问密钥 ID)">
          <el-input v-model.trim="form.accessKey" placeholder="请输入 Access Key" />
        </el-form-item>
        <el-form-item label="Secret Key (私有访问密钥)">
          <el-input v-model.trim="form.secretKey" type="password" show-password placeholder="请输入 Secret Key" />
        </el-form-item>
        <el-form-item label="CDN 域名 (选填)">
          <el-input v-model.trim="form.cdnDomain" placeholder="如果有绑定的 CDN 域名，请填写（如 https://img.example.com）" />
        </el-form-item>
      </el-form>
      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import request from '@/api/request';

const loading = ref(false);
const saving = ref(false);

const form = ref({
  endpoint: '',
  region: 'us-east-1',
  bucket: '',
  accessKey: '',
  secretKey: '',
  cdnDomain: ''
});

async function loadSettings() {
  loading.value = true;
  try {
    const res = await request.get('/admin/system/upload-config');
    form.value = {
      endpoint: res.endpoint || '',
      region: res.region || 'us-east-1',
      bucket: res.bucket || '',
      accessKey: res.accessKey || '',
      secretKey: res.secretKey || '',
      cdnDomain: res.cdnDomain || ''
    };
  } catch (err) {
    ElMessage.error('加载配置失败');
  } finally {
    loading.value = false;
  }
}

async function saveConfig() {
  saving.value = true;
  try {
    await request.put('/admin/system/upload-config', form.value);
    ElMessage.success('配置已保存');
    await loadSettings();
  } catch (err) {
    ElMessage.error('保存失败');
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  loadSettings();
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  margin: 4px 0 0;
  color: var(--app-muted);
  font-size: 14px;
}

.mb-20 {
  margin-bottom: 20px;
}

.config-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 0 24px;
}

.form-actions {
  margin-top: 16px;
}
</style>
