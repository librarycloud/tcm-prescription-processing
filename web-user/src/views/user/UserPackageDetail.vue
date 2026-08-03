<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">包裹详情</h1>
        <p class="page-subtitle">请凭取货码或二维码完成取货</p>
      </div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <Loading v-if="loading" />

    <div v-else-if="detail" class="detail-layout">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ formatPickupCode(detail.pickupCode) }}</span>
            <StatusTag :status="detail.status" />
          </div>
        </template>

        <div class="detail-grid">
          <div v-for="item in fields" :key="item.label" class="detail-item">
            <div class="detail-label">{{ item.label }}</div>
            <div class="detail-value">{{ item.value }}</div>
          </div>
        </div>
      </el-card>

      <QRCodeCard :content="detail.pickupCode" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Loading from '@/components/Loading.vue';
import QRCodeCard from '@/components/QRCodeCard.vue';
import StatusTag from '@/components/StatusTag.vue';
import { getUserPackageDetail } from '@/api/package';
import { formatDate } from '@/utils/date';
import { formatPickupCode, isPicked, pickupMethodText, statusText } from '@/utils/status';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref(null);

function accountName(user) {
  if (!user) return '-';
  return user.nickname || user.phone || `ID ${user.id}`;
}

const fields = computed(() => {
  const item = detail.value;
  if (!item) return [];

  const base = [
    { label: '取货码', value: item.pickupCode },
    { label: '物品名称', value: item.itemName || '-' },
    { label: '备注', value: item.itemInfo || '-' },
    { label: '状态', value: statusText(item.status) },
    { label: '取货方式', value: pickupMethodText(item.pickupMethod) },
    { label: '录入时间', value: formatDate(item.createdAt) }
  ];

  if (isPicked(item.status)) {
    base.push(
      { label: '取货时间', value: formatDate(item.pickedAt) },
      { label: '核销人', value: accountName(item.verifier) }
    );
  }

  return base.map((field) =>
    field.value === item.pickupCode ? { ...field, value: formatPickupCode(field.value) } : field
  );
});

async function loadDetail() {
  loading.value = true;
  try {
    detail.value = await getUserPackageDetail(route.params.id);
  } finally {
    loading.value = false;
  }
}

onMounted(loadDetail);
</script>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 700;
}

@media (max-width: 980px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
