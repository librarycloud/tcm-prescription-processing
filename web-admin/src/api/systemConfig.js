import request from './request';

export function getSystemConfigs() {
  return request.get('/admin/system-configs');
}

export function updateSystemConfigs(data) {
  return request.put('/admin/system-configs', data);
}
