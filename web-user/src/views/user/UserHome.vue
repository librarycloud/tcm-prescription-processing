<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">我的包裹</h1>
        <p class="page-subtitle">按登录手机号查询您的包裹记录</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty>
          <EmptyView description="暂无包裹" />
        </template>
        <el-table-column
          prop="itemName"
          label="物品名称"
          align="center"
          show-overflow-tooltip
        />
        <el-table-column prop="status" label="状态" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="pickupMethod" label="取货方式" align="center">
          <template #default="{ row }">
            <el-tag :type="pickupMethodTagType(row.pickupMethod)" effect="plain">
              {{ pickupMethodText(row.pickupMethod) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="录入时间" align="center">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="pickedAt" label="取货时间" align="center">
          <template #default="{ row }">{{ formatDate(row.pickedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/user/packages/${row.id}`)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Refresh } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import StatusTag from '@/components/StatusTag.vue';
import { getUserPackages } from '@/api/package';
import { formatDate } from '@/utils/date';
import { pickupMethodTagType, pickupMethodText } from '@/utils/status';

const router = useRouter();
const loading = ref(false);
const list = ref([]);

async function loadData() {
  loading.value = true;
  try {
    list.value = await getUserPackages();
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>
