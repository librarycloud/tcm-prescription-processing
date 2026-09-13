import { ref, reactive, watch } from 'vue';
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
  const total = ref(0);
  
  const query = reactive({ ...defaultQuery });
  
  const pagination = reactive({
    page: 1,
    pageSize: options.pageSize || 10,
    total: 0
  });

  const getList = async () => {
    loading.value = true;
    try {
      const params = {
        ...query,
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
        pagination.total = res.total || 0;
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
      query[key] = defaultQuery[key];
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
      ([newPage, newPageSize], [oldPage, oldPageSize]) => {
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
    total: pagination.total,
    getList,
    search,
    reset,
    handleSizeChange,
    handleCurrentChange
  };
}
