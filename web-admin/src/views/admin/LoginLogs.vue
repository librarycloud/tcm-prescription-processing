<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">登录日志</h1>
        <p class="page-subtitle">查看管理员、用户和微信登录的成功与失败记录</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-form class="search-form" @submit.prevent="handleSearch">
        <el-input
          v-model.trim="filters.keyword"
          clearable
          placeholder="手机号、昵称、IP、User-Agent"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.success" clearable placeholder="全部结果">
          <el-option label="登录成功" :value="1" />
          <el-option label="登录失败" :value="0" />
        </el-select>
        <el-select v-model="filters.loginType" clearable placeholder="全部类型">
          <el-option label="管理员登录" value="admin" />
          <el-option label="用户登录" value="user" />
          <el-option label="微信登录" value="wechat" />
          <el-option label="微信绑定" value="wechat-bind" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
        />
        <div class="search-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" row-key="id" border table-layout="auto">
        <template #empty>
          <EmptyView description="暂无登录日志" />
        </template>
        <el-table-column prop="createdAt" label="登录时间" align="center">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="success" label="结果" align="center">
          <template #default="{ row }">
            <el-tag :type="Number(row.success) === 1 ? 'success' : 'danger'" effect="plain">
              {{ Number(row.success) === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginType" label="登录类型" align="center">
          <template #default="{ row }">{{ loginTypeText(row.loginType) }}</template>
        </el-table-column>
        <el-table-column label="用户" align="center">
          <template #default="{ row }">{{ row.user?.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" align="center">
          <template #default="{ row }">{{ row.phone || row.user?.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" align="center" show-overflow-tooltip />
        <el-table-column label="归属地" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ locationText(row.location) }}</template>
        </el-table-column>
        <el-table-column
          prop="userAgent"
          label="User-Agent"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.userAgent || '-' }}</template>
        </el-table-column>
        <el-table-column prop="message" label="说明" show-overflow-tooltip>
          <template #default="{ row }">{{ row.message || '-' }}</template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { Refresh, Search } from '@element-plus/icons-vue';
import EmptyView from '@/components/EmptyView.vue';
import Pagination from '@/components/Pagination.vue';
import { getLoginLogs } from '@/api/loginLog';
import { formatDate } from '@/utils/date';

const loading = ref(false);
const list = ref([]);
const dateRange = ref([]);
const filters = reactive({ keyword: '', success: '', loginType: '' });
const pagination = reactive({ page: 1, pageSize: 20, total: 0 });

function loginTypeText(value) {
  return {
    admin: '管理员登录',
    user: '用户登录',
    wechat: '微信登录',
    'wechat-bind': '微信绑定'
  }[value] || value || '-';
}

function locationText(location) {
  if (!location) return '-';
  const parts = [location.province || location.country, location.city, location.isp].filter(Boolean);
  return [...new Set(parts)].join(' ') || '-';
}

async function loadData() {
  loading.value = true;
  try {
    const data = await getLoginLogs({
      ...filters,
      startDate: dateRange.value?.[0] || '',
      endDate: dateRange.value?.[1] || '',
      page: pagination.page,
      pageSize: pagination.pageSize
    });
    list.value = data?.list || [];
    pagination.total = data?.pagination?.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  if (pagination.page !== 1) pagination.page = 1;
  else loadData();
}

function handleReset() {
  filters.keyword = '';
  filters.success = '';
  filters.loginType = '';
  dateRange.value = [];
  handleSearch();
}

watch(
  () => [pagination.page, pagination.pageSize],
  () => loadData()
);

onMounted(loadData);
</script>

<style scoped>
.search-form {
  display: grid;
  grid-template-columns: minmax(220px, 1.2fr) minmax(140px, 0.6fr) minmax(150px, 0.7fr) minmax(
      260px,
      1fr
    ) auto;
  gap: 12px;
  align-items: center;
}

.search-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 1180px) {
  .search-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .search-form {
    grid-template-columns: 1fr;
  }

  .search-actions {
    justify-content: flex-end;
  }
}
</style>
