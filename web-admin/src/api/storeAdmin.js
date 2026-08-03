import request from './request';

export function getStoreAdmins(params) {
  return request.get('/admin/store-admins', { params });
}

export function createStoreAdmin(data) {
  return request.post('/admin/store-admins', data);
}

export function updateStoreAdmin(id, data) {
  return request.put(`/admin/store-admins/${id}`, data);
}

export function deleteStoreAdmin(id) {
  return request.delete(`/admin/store-admins/${id}`);
}
