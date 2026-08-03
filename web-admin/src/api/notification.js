import request from './request';

export function getSmsSettings() {
  return request.get('/admin/sms/settings');
}

export function updateSmsProvider(provider, data) {
  return request.put(`/admin/sms/providers/${provider}`, data);
}

export function updateSmsTemplate(id, data) {
  return request.put(`/admin/sms/templates/${id}`, data);
}

export function sendSmsTest(data) {
  return request.post('/admin/sms/test', data);
}

export function getPackageNotifications(id) {
  return request.get(`/admin/packages/${id}/notifications`);
}

export function sendPackageNotification(id, data) {
  return request.post(`/admin/packages/${id}/notifications`, data);
}
