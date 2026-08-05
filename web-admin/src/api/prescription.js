import request from './request';
export const getPrescriptions = (params) => request.get('/admin/prescriptions', { params });
export const getPrescription = (id) => request.get(`/admin/prescriptions/${id}`);
export const createPrescription = (data) => request.post('/admin/prescriptions', data);
export const updatePrescription = (id, data) => request.put(`/admin/prescriptions/${id}`, data);
export const deletePrescription = (id) => request.delete(`/admin/prescriptions/${id}`);

export const uploadPrescriptionAttachment = (id, file) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post(`/admin/prescriptions/${id}/attachment`, formData);
};

export const getPrescriptionAttachment = (id) =>
  request.get(`/admin/prescriptions/${id}/attachment`, { responseType: 'blob' });

export const deletePrescriptionAttachment = (id) =>
  request.delete(`/admin/prescriptions/${id}/attachment`);
