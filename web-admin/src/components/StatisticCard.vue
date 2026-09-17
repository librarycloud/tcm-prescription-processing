<template>
  <el-card :class="['stat-card', { 'stat-card-compact': size === 'compact' }]" shadow="never">
    <div class="stat-content">
      <div class="stat-icon" :class="`stat-${type}`">
        <el-icon><component :is="iconComponent" /></el-icon>
      </div>
      <div>
        <div class="stat-label">{{ label }}</div>
        <div class="stat-value">{{ value }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue';
import {
  Bell,
  Box,
  Calendar,
  CircleCheck,
  Clock,
  DataLine,
  List as ListIcon,
  Loading,
  OfficeBuilding,
  Refresh,
  ShoppingBag,
  Sort,
  Tickets,
  Warning
} from '@element-plus/icons-vue';

const props = defineProps({
  label: {
    type: String,
    required: true
  },
  value: {
    type: [Number, String],
    default: 0
  },
  icon: {
    type: String,
    default: 'DataLine'
  },
  type: {
    type: String,
    default: 'primary'
  },
  size: {
    type: String,
    default: 'default'
  }
});

const iconComponents = {
  Bell,
  Box,
  Calendar,
  CircleCheck,
  Clock,
  DataLine,
  List: ListIcon,
  Loading,
  OfficeBuilding,
  Refresh,
  ShoppingBag,
  Sort,
  Tickets,
  Warning
};

const iconComponent = computed(() => iconComponents[props.icon] || DataLine);
</script>

<style scoped>
.stat-card {
  border: 1px solid var(--app-border);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 8px;
  font-size: 24px;
}

.stat-primary {
  color: #2563eb;
  background: var(--el-color-primary-light-9);
}

.stat-success {
  color: #16a34a;
  background: var(--el-color-success-light-9);
}

.stat-warning {
  color: #d97706;
  background: var(--el-color-warning-light-9);
}

.stat-danger {
  color: #dc2626;
  background: var(--el-color-danger-light-9);
}

.stat-info {
  color: #475569;
  background: var(--el-fill-color-light);
}

.stat-label {
  color: var(--app-muted);
  font-size: 14px;
}

.stat-value {
  margin-top: 4px;
  font-size: 26px;
  font-weight: 700;
}

.stat-card-compact :deep(.el-card__body) {
  padding: 10px 12px;
}

.stat-card-compact .stat-content {
  gap: 8px;
}

.stat-card-compact .stat-icon {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  font-size: 15px;
}

.stat-card-compact .stat-label {
  font-size: 12px;
}

.stat-card-compact .stat-value {
  margin-top: 2px;
  font-size: 16px;
}
</style>
