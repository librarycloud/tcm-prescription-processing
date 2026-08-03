import request from './request';

export function getLoginLogs(params) {
  return request.get('/admin/login-logs', { params });
}
