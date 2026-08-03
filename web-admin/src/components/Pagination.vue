<template>
  <div class="pagination">
    <el-pagination
      background
      layout="total, sizes, prev, pager, next, jumper"
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      @update:current-page="emit('update:page', $event)"
      @update:page-size="emit('update:pageSize', $event)"
    />
  </div>
</template>

<script setup>
defineProps({
  page: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 10
  },
  total: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(['update:page', 'update:pageSize']);
</script>

<style scoped>
.pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

@media (max-width: 768px) {
  .pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
