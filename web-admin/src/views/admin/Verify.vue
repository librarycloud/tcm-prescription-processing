<template>
  <div :class="embedded ? 'embedded-verify' : 'page'">
    <div v-if="!embedded" class="page-header">
      <div>
        <h1 class="page-title">包裹核销</h1>
        <p class="page-subtitle">先查询并核对包裹信息，再选择实际取货方式完成核销</p>
      </div>
    </div>

    <div class="verify-layout">
      <el-card class="verify-card" shadow="never">
        <template #header>查询包裹</template>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="84px" @submit.prevent>
          <el-form-item label="取货码" prop="pickupCode">
            <el-input
              v-model="form.pickupCode"
              maxlength="7"
              placeholder="例如 457123"
              clearable
              @input="handleCodeInput"
              @keyup.enter="handleLookup"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="lookupLoading" @click="handleLookup">
              查询包裹
            </el-button>
            <el-button :icon="Camera" disabled>扫码查询</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card v-if="packageInfo" class="verify-result-card" shadow="never">
        <template #header>
          <div class="result-header">
            <span>待核销包裹</span>
            <StatusTag :status="packageInfo.status" />
          </div>
        </template>

        <el-alert
          v-if="isPicked(packageInfo.status)"
          class="picked-alert"
          title="该包裹已经核销，不能重复核销"
          type="warning"
          show-icon
          :closable="false"
        />

        <div class="result-content">
          <QRCodeCard :content="packageInfo.pickupCode" />
          <div>
            <div class="detail-grid">
              <div class="detail-item">
                <div class="detail-label">取货码</div>
                <div class="detail-value">{{ formatPickupCode(packageInfo.pickupCode) }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">物品名称</div>
                <div class="detail-value">{{ packageInfo.itemName || '-' }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">收件人</div>
                <div class="detail-value">{{ packageInfo.receiverName || '-' }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">手机号</div>
                <div class="detail-value">{{ packageInfo.receiverPhone || '-' }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">备注</div>
                <div class="detail-value">{{ packageInfo.itemInfo || '-' }}</div>
              </div>
              <div class="detail-item">
                <div class="detail-label">录入时间</div>
                <div class="detail-value">{{ formatDate(packageInfo.createdAt) }}</div>
              </div>
            </div>

            <div class="verify-controls">
              <div class="control-label">实际取货方式</div>
              <el-select
                v-model="form.pickupMethod"
                class="method-select"
                placeholder="请选择取货方式"
                :disabled="isPicked(packageInfo.status)"
              >
                <el-option
                  v-for="item in PICKUP_METHOD_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <div v-if="Number(form.pickupMethod) === 2" class="tracking-row">
                <TrackingNumberInput
                  v-model="form.expressTrackingNo"
                  class="tracking-input"
                />
              </div>
              <el-button
                type="success"
                :disabled="isPicked(packageInfo.status)"
                @click="openConfirm"
              >
                确认核销
              </el-button>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <ConfirmDialog
      v-model="confirmVisible"
      title="再次确认核销"
      :content="`确认将“${packageInfo?.itemName || '-'}”（取货码 ${packageInfo?.pickupCode || '-'}）按“${pickupMethodText(form.pickupMethod)}”方式核销吗？`"
      confirm-type="success"
      :loading="verifyLoading"
      @confirm="handleVerify"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { Camera } from '@element-plus/icons-vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import QRCodeCard from '@/components/QRCodeCard.vue';
import StatusTag from '@/components/StatusTag.vue';
import TrackingNumberInput from '@/components/TrackingNumberInput.vue';
import { getAdminPackageByPickupCode, verifyPackage } from '@/api/package';
import { formatDate } from '@/utils/date';
import {
  formatPickupCode,
  normalizePickupCode,
  isPicked,
  PICKUP_METHOD_OPTIONS,
  pickupMethodText
} from '@/utils/status';

const route = useRoute();
const router = useRouter();
const props = defineProps({
  initialPickupCode: {
    type: String,
    default: ''
  },
  embedded: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(['success', 'cancel']);
const formRef = ref(null);
const lookupLoading = ref(false);
const verifyLoading = ref(false);
const confirmVisible = ref(false);
const packageInfo = ref(null);

const form = reactive({
  pickupCode: '',
  pickupMethod: null,
  expressTrackingNo: ''
});

const rules = {
  pickupCode: [
    { required: true, message: '请输入取货码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (/^\d{3}-?\d{3}$/.test(value)) callback();
        else callback(new Error('请输入 6 位数字取货码'));
      },
      trigger: 'blur'
    }
  ]
};

function handleCodeInput(value) {
  const pickupCode = formatPickupCode(value);
  form.pickupCode = pickupCode;
  if (normalizePickupCode(packageInfo.value?.pickupCode) !== normalizePickupCode(pickupCode)) {
    packageInfo.value = null;
    form.pickupMethod = null;
    form.expressTrackingNo = '';
  }
}

async function handleLookup() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  lookupLoading.value = true;
  try {
    const data = await getAdminPackageByPickupCode(normalizePickupCode(form.pickupCode));
    packageInfo.value = data;
    form.pickupMethod = data.pickupMethod ?? null;
    form.expressTrackingNo = data.expressTrackingNo || '';
    if (isPicked(data.status)) ElMessage.warning('该包裹已经核销');
  } finally {
    lookupLoading.value = false;
  }
}

function openConfirm() {
  if (form.pickupMethod === null || ![0, 1, 2].includes(Number(form.pickupMethod))) {
    ElMessage.warning('请选择取货方式');
    return;
  }
  if (Number(form.pickupMethod) === 2 && !form.expressTrackingNo.trim()) {
    ElMessage.warning('请录入或扫描快递单号');
    return;
  }
  confirmVisible.value = true;
}

async function handleVerify() {
  if (!packageInfo.value) return;
  verifyLoading.value = true;
  try {
    packageInfo.value = await verifyPackage(
      normalizePickupCode(packageInfo.value.pickupCode),
      form.pickupMethod,
      form.expressTrackingNo
    );
    confirmVisible.value = false;
    ElMessage.success('核销成功');
    if (props.embedded) emit('success', packageInfo.value);
    else router.back();
  } finally {
    verifyLoading.value = false;
  }
}

onMounted(() => {
  const queryCode =
    props.initialPickupCode ||
    (Array.isArray(route.query.pickupCode) ? route.query.pickupCode[0] : route.query.pickupCode);
  const pickupCode = normalizePickupCode(queryCode || '');
  if (/^\d{6}$/.test(pickupCode)) {
    form.pickupCode = formatPickupCode(pickupCode);
    handleLookup();
  }
});
</script>

<style scoped>
.verify-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
}

.verify-card {
  align-self: start;
}

.verify-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.verify-card :deep(.el-card__body) {
  padding: 14px 16px 10px;
}

.verify-card :deep(.el-form-item) {
  margin-bottom: 12px;
}

.verify-card :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.verify-result-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.verify-result-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.verify-result-card .detail-grid {
  gap: 12px;
}

.verify-result-card .detail-item {
  padding-bottom: 10px;
}

.verify-result-card .detail-label {
  margin-bottom: 4px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.picked-alert {
  margin-bottom: 16px;
}

.result-content {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
}

.verify-controls {
  display: grid;
  grid-template-columns: auto auto auto;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
}

.control-label {
  flex: 0 0 auto;
  font-size: 14px;
}

.method-select {
  width: auto;
}

.tracking-row {
  grid-column: 1 / -1;
  width: 100%;
  min-width: 0;
}

.tracking-input {
  width: 100%;
  min-width: 0;
}

.verify-controls > .el-button {
  grid-column: 3;
  grid-row: 1;
  justify-self: start;
}

@media (max-width: 1100px) {
  .verify-layout,
  .result-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .verify-controls {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .verify-controls > .el-button {
    grid-column: 1;
    grid-row: auto;
    justify-self: start;
  }

  .method-select {
    width: 100%;
  }
}
</style>
