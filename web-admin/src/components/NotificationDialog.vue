<template>
  <el-dialog
    :model-value="modelValue"
    title="发送取货通知"
    width="720px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="notification-dialog">
      <el-descriptions v-if="displayPackage" :column="2" border>
        <el-descriptions-item label="取货码">{{ displayPackage.pickupCode }}</el-descriptions-item>
        <el-descriptions-item label="取货方式">
          {{ pickupMethodText(displayPackage.pickupMethod) }}
        </el-descriptions-item>
        <el-descriptions-item label="收件人">{{
          displayPackage.receiverName
        }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{
          displayPackage.receiverPhone || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="通知次数">
          {{ displayPackage.notificationCount || 0 }} 次
        </el-descriptions-item>
        <el-descriptions-item label="上次通知">
          {{ formatDate(displayPackage.lastNotificationAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top">
        <el-form-item label="通知方式">
          <el-radio-group v-model="channel">
            <el-radio-button value="sms">短信</el-radio-button>
            <el-radio-button value="email">邮件</el-radio-button>
            <el-radio-button value="wechat" disabled>微信通知</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="channel === 'sms'" label="当前供应商">
          <el-tag v-if="notificationInfo?.activeProvider" effect="plain">
            {{ providerText(notificationInfo.activeProvider) }}
          </el-tag>
          <span v-else class="muted">未启用短信供应商</span>
        </el-form-item>
        <el-alert
          v-if="channel === 'sms' && !displayPackage?.receiverPhone"
          title="未填写手机号，不能发送短信"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="isExpressWithoutTracking"
          title="快递包裹填写快递单号后才能发送取货通知"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-form-item v-if="channel === 'sms'" label="短信模板">
          <div v-if="notificationInfo?.template" class="template-preview">
            <div class="template-name">{{ notificationInfo.template.name }}</div>
            <div>{{ notificationInfo.template.preview || '暂无预览内容' }}</div>
          </div>
          <span v-else class="muted">当前取货方式未配置模板</span>
        </el-form-item>
        <el-form-item v-if="channel === 'email'" label="收件邮箱">
          <el-tag v-if="notificationInfo?.email?.verified" type="success" effect="plain">
            {{ notificationInfo.email.address }}（已验证）
          </el-tag>
          <span v-else class="muted">用户未绑定并验证邮箱，不能发送邮件</span>
        </el-form-item>
        <el-form-item v-if="channel === 'email'" label="邮件模板">
          <div v-if="notificationInfo?.email?.template" class="template-preview">
            <div class="template-name">{{ notificationInfo.email.template.name }}</div>
            <div>{{ notificationInfo.email.template.subject }}</div>
            <div>{{ notificationInfo.email.template.content }}</div>
          </div>
          <span v-else class="muted">当前取货方式未配置邮件模板</span>
        </el-form-item>
      </el-form>

      <div class="history-header">最近通知记录</div>
      <el-table :data="notificationInfo?.logs || []" border max-height="240" table-layout="auto">
        <el-table-column label="时间">
          <template #default="{ row }">{{ formatDate(row.sentAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="渠道">
          <template #default="{ row }">{{ providerText(row.provider) }}</template>
        </el-table-column>
        <el-table-column label="状态">
          <template #default="{ row }">
            <el-tag :type="logStatusType(row.status)">
              {{ logStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人">
          <template #default="{ row }">
            {{ row.operator?.nickname || row.operator?.phone || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="失败原因" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="sending" :disabled="!canSend" @click="sendNotification">
        发送{{ channel === 'email' ? '邮件' : '短信' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { getPackageNotifications, sendPackageNotification } from '@/api/notification';
import { formatDate } from '@/utils/date';
import { formatPickupCode, isPicked, pickupMethodText } from '@/utils/status';

const props = defineProps({
  modelValue: Boolean,
  packageInfo: { type: Object, default: null }
});

const emit = defineEmits(['update:modelValue', 'sent']);
const loading = ref(false);
const sending = ref(false);
const channel = ref('sms');
const notificationInfo = ref(null);
const displayPackage = computed(() => {
  const value = notificationInfo.value?.package || props.packageInfo;
  return value ? { ...value, pickupCode: formatPickupCode(value.pickupCode) } : value;
});
const isExpressWithoutTracking = computed(
  () =>
    Number(displayPackage.value?.pickupMethod) === 2 &&
    !String(displayPackage.value?.expressTrackingNo || '').trim()
);

const canSend = computed(
  () =>
    !isPicked(displayPackage.value?.status) &&
    !isExpressWithoutTracking.value &&
    (channel.value === 'sms'
      ? Boolean(
        displayPackage.value?.receiverPhone &&
          notificationInfo.value?.activeProvider &&
          notificationInfo.value?.template?.enabled
      )
      : Boolean(
        channel.value === 'email' &&
          notificationInfo.value?.email?.enabled &&
          notificationInfo.value?.email?.verified &&
          notificationInfo.value?.email?.template?.enabled
      ))
);

function requestId() {
  return typeof crypto?.randomUUID === 'function'
    ? crypto.randomUUID()
    : `notify_${Date.now()}_${Math.random().toString(36).slice(2)}`;
}

function providerText(provider) {
  return { tencent: '腾讯云', aliyun: '阿里云', volcengine: '火山引擎', smtp: 'SMTP' }[provider] || '-';
}

function logStatusText(status) {
  return { 0: '待发送', 1: '成功', 2: '失败', 3: '发送中' }[Number(status)] || '未知';
}

function logStatusType(status) {
  return { 0: 'info', 1: 'success', 2: 'danger', 3: 'primary' }[Number(status)] || 'info';
}

async function loadNotifications() {
  if (!props.packageInfo?.id) return;
  loading.value = true;
  try {
    notificationInfo.value = await getPackageNotifications(props.packageInfo.id);
  } finally {
    loading.value = false;
  }
}

async function sendNotification() {
  sending.value = true;
  try {
    await sendPackageNotification(props.packageInfo.id, {
      channel: channel.value,
      requestId: requestId()
    });
    ElMessage.success('取货通知已发送');
    await loadNotifications();
    emit('sent');
  } finally {
    sending.value = false;
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) loadNotifications();
  }
);
</script>

<style scoped>
.notification-dialog {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  gap: 18px;
}

.template-preview {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--app-border);
  background: #f8fafc;
  line-height: 1.7;
}

.template-name,
.history-header {
  margin-bottom: 6px;
  font-weight: 600;
}

.muted {
  color: var(--app-muted);
}

@media (max-width: 720px) {
  :deep(.el-dialog) {
    width: calc(100% - 24px);
  }

  :deep(.el-descriptions__body .el-descriptions__table) {
    table-layout: fixed;
  }
}
</style>
