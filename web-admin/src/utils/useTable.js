import { ref, reactive, watch, computed } from 'vue';
import { ElMessage } from 'element-plus';

/**
 * 封装通用表格逻辑
 * @param {Function} apiFunc 获取列表的 API 函数
 * @param {Object} defaultQuery 默认查询参数
 * @param {Object} options 额外配置
 */
export function useTable(apiFunc, defaultQuery = {}, options = {}) {
  const list = ref([]);
  const loading = ref(false);

  const queryRef = ref({ ...defaultQuery });
  const query = new Proxy(queryRef, {
    get(target, prop, receiver) {
      if (prop === 'value') return target.value;
      if (prop in target) return Reflect.get(target, prop, receiver);
      return target.value[prop];
    },
    set(target, prop, val, receiver) {
      if (prop === 'value') {
        target.value = val;
        return true;
      }
      if (prop in target) {
        return Reflect.set(target, prop, val, receiver);
      }
      target.value[prop] = val;
      return true;
    },
    ownKeys(target) {
      return Reflect.ownKeys(target.value);
    },
    getOwnPropertyDescriptor(target, prop) {
      return Object.getOwnPropertyDescriptor(target.value, prop);
    }
  });

  const pagination = reactive({
    page: 1,
    pageSize: options.pageSize || 10,
    total: 0
  });

  const getList = async () => {
    loading.value = true;
    try {
      const params = {
        ...queryRef.value,
        page: pagination.page,
        pageSize: pagination.pageSize
      };

      // Hook: before fetch
      if (options.beforeFetch) {
        options.beforeFetch(params);
      }

      const res = await apiFunc(params);

      // Handle standard list/total format
      if (res && res.list !== undefined) {
        list.value = res.list;
        pagination.total = res.pagination?.total || res.total || 0;
      } else if (Array.isArray(res)) {
        list.value = res;
        pagination.total = res.length;
      }

      // Hook: after fetch
      if (options.afterFetch) {
        options.afterFetch(res);
      }
    } catch (error) {
      console.error('Failed to fetch table data:', error);
      if (options.onError) {
        options.onError(error);
      } else {
        ElMessage.error(error.message || '获取列表失败');
      }
    } finally {
      loading.value = false;
    }
  };

  const search = () => {
    pagination.page = 1;
    getList();
  };

  const reset = () => {
    Object.keys(defaultQuery).forEach((key) => {
      queryRef.value[key] = defaultQuery[key];
    });
    search();
  };

  const handleSizeChange = (val) => {
    pagination.pageSize = val;
    search();
  };

  const handleCurrentChange = (val) => {
    pagination.page = val;
    getList();
  };

  if (options.autoWatch !== false) {
    watch(
      () => [pagination.page, pagination.pageSize],
      ([newPage, newPageSize], [, oldPageSize]) => {
        if (newPageSize !== oldPageSize && newPage !== 1) {
          pagination.page = 1;
          return;
        }
        getList();
      },
      { deep: false }
    );
  }

  return {
    list,
    loading,
    query,
    pagination,
    total: computed(() => pagination.total),
    getList,
    search,
    reset,
    handleSizeChange,
    handleCurrentChange
  };
}
