import request from './request';

export const getProcessingEquipment = (params) =>
  request.get('/admin/processing-equipment', { params });
export const createProcessingEquipment = (data) =>
  request.post('/admin/processing-equipment', data);
export const updateProcessingEquipment = (id, data) =>
  request.put(`/admin/processing-equipment/${id}`, data);
export const deleteProcessingEquipment = (id) =>
  request.delete(`/admin/processing-equipment/${id}`);
