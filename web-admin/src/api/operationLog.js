import request from './request';

export function getOperationLogs(params) {
  return request.get('/admin/operation-logs', { params });
}
