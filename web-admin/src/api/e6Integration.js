import request from './request';

export const getE6StoreConfig = (storeId) =>
  request.get(`/admin/e6/stores/${storeId}/config`);
export const saveE6StoreConfig = (storeId, data) =>
  request.put(`/admin/e6/stores/${storeId}/config`, data);

export const getE6DoctorMappings = (params) =>
  request.get('/admin/e6/doctor-mappings', { params });
export const createE6DoctorMapping = (data) =>
  request.post('/admin/e6/doctor-mappings', data);
export const updateE6DoctorMapping = (id, data) =>
  request.put(`/admin/e6/doctor-mappings/${id}`, data);
export const deleteE6DoctorMapping = (id) =>
  request.delete(`/admin/e6/doctor-mappings/${id}`);
export const getE6OperatorMappings = (params) =>
  request.get('/admin/e6/operator-mappings', { params });
export const createE6OperatorMapping = (data) =>
  request.post('/admin/e6/operator-mappings', data);
export const updateE6OperatorMapping = (id, data) =>
  request.put(`/admin/e6/operator-mappings/${id}`, data);
export const deleteE6OperatorMapping = (id) =>
  request.delete(`/admin/e6/operator-mappings/${id}`);

export const getE6Imports = (params) => request.get('/admin/e6/imports', { params });
export const getE6Import = (id) => request.get(`/admin/e6/imports/${id}`);
export const confirmE6Import = (id, data) =>
  request.post(`/admin/e6/imports/${id}/confirm`, data);
export const mergeE6Imports = (data) => request.post('/admin/e6/imports/merge', data);
export const rejectE6Import = (id, reason) =>
  request.post(`/admin/e6/imports/${id}/reject`, { reason });
export const revalidateE6Import = (id) =>
  request.post(`/admin/e6/imports/${id}/revalidate`);
