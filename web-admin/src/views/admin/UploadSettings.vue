<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">S3/OSS 上传设置</h1>
        <p class="page-subtitle">配置兼容 S3 协议的对象存储（阿里云、腾讯云、SeaweedFS、MinIO 等）</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadSettings">刷新</el-button>
    </div>

    <el-alert
      title="配置说明"
      description="配置保存后即时生效。微信小程序因底层机制将通过本服务器中转（极速），Web/App端可直接将文件直传至您配置的对象存储中。如果您使用自建 SeaweedFS/MinIO，请选择「自建对象存储」并填写完整的 Endpoint（如 http://192.168.1.100:9000）。"
      type="info"
      show-icon
      class="mb-20"
    />

    <el-card v-loading="loading" shadow="never">
      <el-tabs v-model="form.activeProvider">
        <el-tab-pane
          v-for="provider in providers"
          :key="provider.key"
          :label="provider.name"
          :name="provider.key"
        >
          <el-form class="config-form" label-position="top" :model="form.providers[provider.key]">
            <el-form-item label="Endpoint (连接地址)">
              <el-input v-model.trim="form.providers[provider.key].endpoint" :placeholder="provider.endpointPlaceholder" />
            </el-form-item>
            <el-form-item label="Region (区域)">
              <el-input v-model.trim="form.providers[provider.key].region" :placeholder="provider.regionPlaceholder" />
            </el-form-item>
            <el-form-item label="Bucket (存储桶名称)">
              <el-input v-model.trim="form.providers[provider.key].bucket" placeholder="例如: tcm-uploads" />
            </el-form-item>
            <el-form-item label="Access Key (访问密钥 ID)">
              <el-input v-model.trim="form.providers[provider.key].accessKey" placeholder="请输入 Access Key" />
            </el-form-item>
            <el-form-item label="Secret Key (私有访问密钥)">
              <el-input v-model.trim="form.providers[provider.key].secretKey" type="password" show-password placeholder="请输入 Secret Key" />
            </el-form-item>
            <el-form-item label="CDN 域名 (选填)">
              <el-input v-model.trim="form.providers[provider.key].cdnDomain" placeholder="如果有绑定的 CDN 域名，请填写（如 https://img.example.com）" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      
      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="saveConfig">保存并启用当前配置</el-button>
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

const providers = [
  {
    key: 'seaweedfs',
    name: '自建存储 (SeaweedFS/MinIO)',
    endpointPlaceholder: '例如: http://192.168.1.100:9000 或 https://admin.trthz.com',
    regionPlaceholder: '例如: us-east-1'
  },
  {
    key: 'aliyun',
    name: '阿里云 OSS',
    endpointPlaceholder: '例如: https://oss-cn-hangzhou.aliyuncs.com',
    regionPlaceholder: '例如: oss-cn-hangzhou'
  },
  {
    key: 'tencent',
    name: '腾讯云 COS',
    endpointPlaceholder: '例如: https://cos.ap-guangzhou.myqcloud.com',
    regionPlaceholder: '例如: ap-guangzhou'
  }
];

const form = ref({
  activeProvider: 'seaweedfs',
  providers: {
    seaweedfs: { endpoint: '', bucket: '', accessKey: '', secretKey: '', region: 'us-east-1', cdnDomain: '' },
    aliyun: { endpoint: '', bucket: '', accessKey: '', secretKey: '', region: 'oss-cn-hangzhou', cdnDomain: '' },
    tencent: { endpoint: '', bucket: '', accessKey: '', secretKey: '', region: 'ap-guangzhou', cdnDomain: '' }
  }
});

async function loadSettings() {
  loading.value = true;
  try {
    const res = await request.get('/admin/system/upload-config');
    // 兼容新老配置格式
    if (res.activeProvider && res.providers) {
      form.value.activeProvider = res.activeProvider;
      Object.keys(res.providers).forEach(key => {
        if (form.value.providers[key]) {
          form.value.providers[key] = { ...form.value.providers[key], ...res.providers[key] };
        }
      });
    } else {
      // 老配置，迁移到 seaweedfs 选项下
      form.value.activeProvider = 'seaweedfs';
      form.value.providers.seaweedfs = {
        endpoint: res.endpoint || '',
        region: res.region || 'us-east-1',
        bucket: res.bucket || '',
        accessKey: res.accessKey || '',
        secretKey: res.secretKey || '',
        cdnDomain: res.cdnDomain || ''
      };
    }
  } catch (err) { console.error(err);
    ElMessage.error('加载配置失败');
  } finally {
    loading.value = false;
  }
}

async function saveConfig() {
  saving.value = true;
  try {
    await request.put('/admin/system/upload-config', form.value);
    ElMessage.success('配置已保存并生效');
    await loadSettings();
  } catch (err) { console.error(err);
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
  margin-top: 20px;
}

.form-actions {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
