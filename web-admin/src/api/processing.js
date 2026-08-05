import request from './request';
export const getProcessingPlans = (params) => request.get('/admin/processing-plans', { params });
export const getProcessingCalendar = (params) =>
  request.get('/admin/processing-plans/calendar', { params });
export const getProcessingWorkflow = (id) => request.get(`/admin/processing-plans/${id}/workflow`);
export const getProcessingPhoto = (planId, photoId) =>
  request.get(`/admin/processing-plans/${planId}/photos/${photoId}`, { responseType: 'blob' });
export const uploadProcessingPhoto = (planId, file) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post(`/admin/processing-plans/${planId}/dispensing-complete`, formData);
};
export const deleteProcessingPhoto = (planId, photoId) =>
  request.delete(`/admin/processing-plans/${planId}/photos/${photoId}`);
export const createProcessingPlan = (data) => request.post('/admin/processing-plans', data);
export const createProcessingPlanBatch = (data) =>
  request.post('/admin/processing-plans/batch', data);
export const updateProcessingPlan = (id, data) =>
  request.put(`/admin/processing-plans/${id}`, data);
export const transitionProcessingPlan = (id, status, data = {}) =>
  request.post(`/admin/processing-plans/${id}/transition`, { ...data, status });
export const generateProcessingPlanPackage = (id) =>
  request.post(`/admin/processing-plans/${id}/generate-package`);
export const delayProcessingPlan = (id, data) =>
  request.post(`/admin/processing-plans/${id}/delay`, data);
export const receiveProcessingNotice = (id, data) =>
  request.post(`/admin/processing-plans/${id}/receive-notice`, data);
export const reorderProcessingQueue = (data) => request.put('/admin/processing-plans/queue', data);
export const restoreProcessingQueue = (data) =>
  request.post('/admin/processing-plans/queue/restore', data);
export const deleteProcessingPlan = (id) => request.delete(`/admin/processing-plans/${id}`);
export const reorderPrescriptionPlans = (prescriptionId, ids) =>
  request.put(`/admin/prescriptions/${prescriptionId}/processing-plans/order`, { ids });
export const getDictionaries = (type, includeDisabled = false) =>
  request.get('/admin/dictionaries', {
    params: { type, includeDisabled: includeDisabled ? 1 : 0 }
  });
export const getDoctors = (includeDisabled = false) =>
  request.get('/admin/doctors', { params: { includeDisabled: includeDisabled ? 1 : 0 } });
export const saveDictionary = (id, data) =>
  id ? request.put(`/admin/dictionaries/${id}`, data) : request.post('/admin/dictionaries', data);
export const saveDoctor = (id, data) =>
  id ? request.put(`/admin/doctors/${id}`, data) : request.post('/admin/doctors', data);
export const deleteDictionary = (id) => request.delete(`/admin/dictionaries/${id}`);
export const deleteDoctor = (id) => request.delete(`/admin/doctors/${id}`);
