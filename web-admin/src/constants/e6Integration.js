export const E6_IMPORT_STATUS = Object.freeze({
  IMPORT_PENDING: 0,
  IMPORT_MAPPING_REQUIRED: 1,
  IMPORT_ERROR: 2,
  IMPORT_CONVERTED: 3,
  IMPORT_REJECTED: 4,
  IMPORT_CANCELLED: 5,
  IMPORT_CONFLICT: 6,
  IMPORT_PROCESSING: 7
});

export const E6_IMPORT_STATUS_OPTIONS = Object.freeze([
  { value: 0, label: '待确认', type: 'warning' },
  { value: 1, label: '待映射', type: 'danger' },
  { value: 2, label: '导入异常', type: 'danger' },
  { value: 3, label: '已生成处方', type: 'success' },
  { value: 4, label: '已驳回', type: 'info' },
  { value: 5, label: '已取消', type: 'info' },
  { value: 6, label: '数据冲突', type: 'danger' },
  { value: 7, label: '处理中', type: 'primary' }
]);

export function e6ImportStatusMeta(status) {
  return E6_IMPORT_STATUS_OPTIONS.find((item) => item.value === Number(status)) || {
    label: `未知状态(${status})`,
    type: 'info'
  };
}
