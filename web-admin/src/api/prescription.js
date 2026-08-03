import request from './request';
export const getPrescriptions = (params) => request.get('/admin/prescriptions', { params });
export const getPrescription = (id) => request.get(`/admin/prescriptions/${id}`);
export const createPrescription = (data) => request.post('/admin/prescriptions', data);
export const updatePrescription = (id, data) => request.put(`/admin/prescriptions/${id}`, data);
export const deletePrescription = (id) => request.delete(`/admin/prescriptions/${id}`);
