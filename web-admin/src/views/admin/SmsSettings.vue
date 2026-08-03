<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">短信设置</h1>
        <p class="page-subtitle">配置短信供应商、取货方式模板与测试发送</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadSettings">刷新</el-button>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-tabs v-model="activeProvider">
        <el-tab-pane
          v-for="provider in providers"
          :key="provider.provider"
          :label="provider.providerName"
          :name="provider.provider"
        >
          <el-form
            class="provider-form"
            label-position="top"
            :model="providerForms[provider.provider]"
          >
            <el-form-item label="AccessKey ID">
              <el-input v-model.trim="providerForms[provider.provider].accessKeyId" />
            </el-form-item>
            <el-form-item label="AccessKey Secret">
              <el-input
                v-model="providerForms[provider.provider].secretKey"
                type="password"
                show-password
                :placeholder="provider.secretConfigured ? '已配置，留空则不修改' : '请输入密钥'"
              />
            </el-form-item>
            <el-form-item label="短信签名">
              <el-input v-model.trim="providerForms[provider.provider].signName" />
            </el-form-item>
            <el-form-item v-if="provider.provider === 'tencent'" label="SDK App ID">
              <el-input v-model.trim="providerForms[provider.provider].sdkAppId" />
            </el-form-item>
            <el-form-item v-if="provider.provider === 'volcengine'" label="短信账号">
              <el-input v-model.trim="providerForms[provider.provider].smsAccount" />
            </el-form-item>
            <el-form-item label="地域">
              <el-input v-model.trim="providerForms[provider.provider].region" />
            </el-form-item>
            <el-form-item label="启用供应商">
              <el-switch v-model="providerForms[provider.provider].enabled" />
            </el-form-item>
          </el-form>
          <div class="form-actions">
            <el-button
              type="primary"
              :loading="savingProvider === provider.provider"
              @click="saveProvider(provider.provider)"
            >
              保存配置
            </el-button>
            <el-button :icon="Promotion" @click="openTest(provider.provider)">测试发送</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>取货通知模板</span>
          <el-tag effect="plain">{{ activeProviderName }}</el-tag>
        </div>
      </template>
      <el-table :data="activeTemplates" border table-layout="auto">
        <el-table-column label="取货方式">
          <template #default="{ row }">{{ pickupMethodText(row.pickupMethod) }}</template>
        </el-table-column>
        <el-table-column prop="name" label="模板名称" />
        <el-table-column prop="templateCode" label="云平台模板编号">
          <template #default="{ row }">{{ row.templateCode || '-' }}</template>
        </el-table-column>
        <el-table-column
          prop="contentPreview"
          label="内容预览"
          show-overflow-tooltip
        />
        <el-table-column label="状态">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '已启用' : '未启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="editTemplate(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="templateDialogVisible" title="编辑短信模板" width="680px">
      <el-form v-if="templateForm" label-position="top">
        <div class="template-grid">
          <el-form-item label="模板名称">
            <el-input v-model.trim="templateForm.name" maxlength="100" />
          </el-form-item>
          <el-form-item label="云平台模板编号">
            <el-input v-model.trim="templateForm.templateCode" maxlength="128" />
          </el-form-item>
        </div>
        <el-form-item label="内容预览">
          <el-input
            v-model="templateForm.contentPreview"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="模板参数">
          <div class="mapping-list">
            <div
              v-for="(mapping, index) in templateForm.variableMapping"
              :key="index"
              class="mapping-row"
            >
              <el-input v-model.trim="mapping.key" placeholder="云模板参数名" />
              <el-select v-model="mapping.source" placeholder="业务字段">
                <el-option
                  v-for="source in templateSources"
                  :key="source"
                  :label="templateSourceLabel(source)"
                  :value="source"
                />
              </el-select>
              <el-button
                circle
                :icon="Delete"
                :disabled="templateForm.variableMapping.length === 1"
                @click="removeMapping(index)"
              />
            </div>
            <el-button
              :icon="Plus"
              :disabled="templateForm.variableMapping.length >= 10"
              @click="addMapping"
            >
              添加参数
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="启用模板">
          <el-switch v-model="templateForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplate">
          保存模板
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDialogVisible" title="测试发送" width="460px">
      <el-form label-position="top">
        <el-form-item label="供应商">
          <el-input :model-value="providerName(testForm.provider)" disabled />
        </el-form-item>
        <el-form-item label="测试手机号">
          <el-input v-model.trim="testForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="取货方式">
          <el-select v-model="testForm.pickupMethod">
            <el-option label="自提" :value="0" />
            <el-option label="跑腿" :value="1" />
            <el-option label="快递" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="testing" @click="sendTest">发送测试短信</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Delete, Plus, Promotion, Refresh } from '@element-plus/icons-vue';
import {
  getSmsSettings,
  sendSmsTest,
  updateSmsProvider,
  updateSmsTemplate
} from '@/api/notification';
import { pickupMethodText } from '@/utils/status';

const loading = ref(false);
const savingProvider = ref('');
const savingTemplate = ref(false);
const testing = ref(false);
const activeProvider = ref('tencent');
const providers = ref([]);
const templates = ref([]);
const templateSources = ref([]);
const providerForms = reactive({});
const templateDialogVisible = ref(false);
const testDialogVisible = ref(false);
const templateForm = ref(null);
const testForm = reactive({ provider: '', phone: '', pickupMethod: 0 });
const templateSourceLabels = {
  receiverName: '收件人姓名',
  receiverPhone: '收件人手机号',
  pickupCode: '取货码',
  itemName: '物品名称',
  itemInfo: '物品信息',
  pickupMethod: '取货方式',
  storeName: '门店名称',
  storeAddress: '门店地址',
  storePhone: '门店电话',
  expressTrackingNo: '快递单号',
  expressAddress: '快递地址',
  createdAt: '创建时间'
};

const activeTemplates = computed(() =>
  templates.value.filter((item) => item.provider === activeProvider.value)
);
const activeProviderName = computed(() => providerName(activeProvider.value));

function requestId() {
  return typeof crypto?.randomUUID === 'function'
    ? crypto.randomUUID()
    : `sms_${Date.now()}_${Math.random().toString(36).slice(2)}`;
}

function providerName(provider) {
  return providers.value.find((item) => item.provider === provider)?.providerName || provider;
}

function templateSourceLabel(source) {
  const label = templateSourceLabels[source];
  return label ? `${label}（${source}）` : source;
}

async function loadSettings() {
  loading.value = true;
  try {
    const data = await getSmsSettings();
    providers.value = data.providers || [];
    templates.value = data.templates || [];
    templateSources.value = data.templateSources || [];
    providers.value.forEach((item) => {
      providerForms[item.provider] = {
        accessKeyId: item.accessKeyId || '',
        secretKey: '',
        signName: item.signName || '',
        sdkAppId: item.sdkAppId || '',
        smsAccount: item.smsAccount || '',
        region: item.region || '',
        enabled: Boolean(item.enabled)
      };
    });
    if (!providers.value.some((item) => item.provider === activeProvider.value)) {
      activeProvider.value = providers.value[0]?.provider || 'tencent';
    }
  } finally {
    loading.value = false;
  }
}

async function saveProvider(provider) {
  savingProvider.value = provider;
  try {
    await updateSmsProvider(provider, providerForms[provider]);
    ElMessage.success('供应商配置已保存');
    await loadSettings();
  } finally {
    savingProvider.value = '';
  }
}

function editTemplate(row) {
  templateForm.value = {
    ...row,
    variableMapping: (row.variableMapping || []).map((item) => ({ ...item }))
  };
  templateDialogVisible.value = true;
}

function addMapping() {
  templateForm.value.variableMapping.push({ key: '', source: '' });
}

function removeMapping(index) {
  templateForm.value.variableMapping.splice(index, 1);
}

async function saveTemplate() {
  savingTemplate.value = true;
  try {
    await updateSmsTemplate(templateForm.value.id, templateForm.value);
    ElMessage.success('短信模板已保存');
    templateDialogVisible.value = false;
    await loadSettings();
  } finally {
    savingTemplate.value = false;
  }
}

function openTest(provider) {
  testForm.provider = provider;
  testForm.phone = '';
  testForm.pickupMethod = 0;
  testDialogVisible.value = true;
}

async function sendTest() {
  testing.value = true;
  try {
    await sendSmsTest({ ...testForm, requestId: requestId() });
    ElMessage.success('测试短信已提交');
    testDialogVisible.value = false;
  } finally {
    testing.value = false;
  }
}

onMounted(loadSettings);
</script>

<style scoped>
.provider-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}

.form-actions,
.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-header {
  justify-content: space-between;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.mapping-list {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 10px;
}

.mapping-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 32px;
  gap: 10px;
}

@media (max-width: 800px) {
  .provider-form,
  .template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
