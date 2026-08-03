<template>
  <div :class="embedded ? 'embedded-detail' : 'page'">
    <div v-if="!embedded" class="page-header">
      <div>
        <h1 class="page-title">包裹详情</h1>
        <p class="page-subtitle">查看包裹完整信息、取货码与二维码</p>
      </div>
      <div class="page-actions">
        <el-button @click="router.back()">返回</el-button>
        <el-button v-if="detail" :icon="Printer" @click="openPrint">打印标签</el-button>
        <el-button
          v-if="detail && !isPicked(detail.status)"
          type="primary"
          @click="openEdit"
        >
          编辑
        </el-button>
        <el-button v-if="detail && !isPicked(detail.status)" type="success" @click="openVerify">
          核销
        </el-button>
      </div>
    </div>

    <Loading v-if="loading" />

    <div v-else-if="detail" class="detail-layout">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ formatPickupCode(detail.pickupCode) }}</span>
            <div class="card-header-actions">
              <StatusTag :status="detail.status" />
            </div>
          </div>
        </template>

        <div class="detail-grid">
          <div v-for="item in fields" :key="item.label" class="detail-item">
            <div class="detail-label">{{ item.label }}</div>
            <div class="detail-value">
              <NotificationStatus
                v-if="item.notification"
                :status="detail.notificationStatus"
                :count="detail.notificationCount"
                :disabled="isPicked(detail.status)"
                @click="notificationDialogVisible = true"
              />
              <span v-else>{{ item.value }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <QRCodeCard :content="detail.pickupCode" />
    </div>

    <PrintDialog v-model="printVisible" :package-info="detail" />
    <NotificationDialog
      v-model="notificationDialogVisible"
      :package-info="detail"
      @sent="loadDetail"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Printer } from '@element-plus/icons-vue';
import Loading from '@/components/Loading.vue';
import NotificationDialog from '@/components/NotificationDialog.vue';
import NotificationStatus from '@/components/NotificationStatus.vue';
import PrintDialog from '@/components/PrintDialog.vue';
import QRCodeCard from '@/components/QRCodeCard.vue';
import StatusTag from '@/components/StatusTag.vue';
import { getAdminPackageDetail } from '@/api/package';
import { formatDate } from '@/utils/date';
import { formatPickupCode, isPicked, pickupMethodText, statusText } from '@/utils/status';

const route = useRoute();
const router = useRouter();
const props = defineProps({
  id: {
    type: [Number, String],
    default: null
  },
  embedded: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(['edit', 'verify']);
const loading = ref(false);
const printVisible = ref(false);
const notificationDialogVisible = ref(false);
const detail = ref(null);

function accountName(user) {
  if (!user) return '-';
  return user.nickname || user.phone || `ID ${user.id}`;
}

const fields = computed(() => {
  const item = detail.value;
  if (!item) return [];

  return [
    ...(Number(item.pickupMethod) === 2
      ? [
          { label: '快递单号', value: item.expressTrackingNo || '-' },
          { label: '快递地址', value: item.expressAddress || '-' }
        ]
      : []),
    { label: '取货码', value: item.pickupCode },
    { label: '所属门店', value: item.store?.name || '-' },
    { label: '物品名称', value: item.itemName || '-' },
    { label: '备注', value: item.itemInfo || '-' },
    { label: '收件人', value: item.receiverName || '-' },
    { label: '手机号', value: item.receiverPhone || '-' },
    { label: '通知状态', notification: true },
    { label: '状态', value: statusText(item.status) },
    { label: '取货方式', value: pickupMethodText(item.pickupMethod) },
    { label: '录入时间', value: formatDate(item.createdAt) },
    { label: '取货时间', value: formatDate(item.pickedAt) },
    { label: '录入人', value: accountName(item.creator) },
    { label: '核销人', value: accountName(item.verifier) },
    { label: '修改人', value: accountName(item.modifier) },
    { label: '修改时间', value: formatDate(item.modifiedAt) }
  ].map((field) =>
    field.value === item.pickupCode ? { ...field, value: formatPickupCode(field.value) } : field
  );
});

async function loadDetail() {
  loading.value = true;
  try {
    const id = props.id ?? route.params.id;
    detail.value = await getAdminPackageDetail(id);
  } finally {
    loading.value = false;
  }
}

function openEdit() {
  if (props.embedded) emit('edit', detail.value);
  else router.push(`/admin/packages/edit/${detail.value.id}`);
}

function openVerify() {
  if (props.embedded) emit('verify', detail.value);
  else router.push({ path: '/admin/verify', query: { pickupCode: detail.value.pickupCode } });
}

function openPrint() {
  printVisible.value = true;
}

defineExpose({ openPrint });

onMounted(loadDetail);
</script>

<style scoped>
.page-actions {
  display: flex;
  gap: 10px;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}

.detail-layout :deep(.el-card__header) {
  padding: 12px 16px;
}

.detail-layout :deep(.el-card__body) {
  padding: 14px 16px;
}

.detail-layout .detail-grid {
  gap: 12px;
}

.detail-layout .detail-item {
  padding-bottom: 10px;
}

.detail-layout .detail-label {
  margin-bottom: 4px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 700;
}

.card-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 980px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
