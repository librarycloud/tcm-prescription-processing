import request from './request';

export function getStores(params) {
  return request.get('/stores', { params });
}

export function getStore(id) {
  return request.get(`/stores/${id}`);
}

export function createStore(data) {
  return request.post('/stores', data);
}

export function updateStore(id, data) {
  return request.put(`/stores/${id}`, data);
}

export function deleteStore(id) {
  return request.delete(`/stores/${id}`);
}
