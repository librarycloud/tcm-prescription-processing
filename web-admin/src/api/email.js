import request from './request';

export function getEmailSettings() {
  return request.get('/admin/email/settings');
}

export function updateEmailConfig(data) {
  return request.put('/admin/email/settings', data);
}

export function updateEmailTemplate(id, data) {
  return request.put(`/admin/email/templates/${id}`, data);
}

export function sendEmailTest(data) {
  return request.post('/admin/email/test', data);
}
