import request from './request';

export function getAdminUsers(params) {
  return request.get('/admin/users', { params });
}

export function matchAdminUsers(phone) {
  return request.get('/admin/users/match', { params: { phone } });
}

export function createAdminUser(data) {
  return request.post('/admin/users', data);
}

export function updateAdminUser(id, data) {
  return request.put(`/admin/users/${id}`, data);
}

export function deleteAdminUser(id) {
  return request.delete(`/admin/users/${id}`);
}
