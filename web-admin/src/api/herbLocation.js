import request from './request';

export const getHerbLocationStores = () => request.get('/admin/herb-locations/stores');
export const getHerbLocations = (storeId) => request.get('/admin/herb-locations', { params: { storeId } });
export const getHerbLocationLayout = (storeId) => request.get('/admin/herb-locations/layout', { params: { storeId } });
export const saveHerbLocationAssignment = (data) => request.post('/admin/herb-locations/assignments', data);
export const updateHerbLocationAssignment = (id, data) => request.put(`/admin/herb-locations/assignments/${id}`, data);
export const removeHerbLocationAssignment = (id) => request.delete(`/admin/herb-locations/assignments/${id}`);
export const updateHerb = (id, data) => request.put(`/admin/herb-locations/herbs/${id}`, data);
export const updateHerbLocationLayout = (data) => request.put('/admin/herb-locations/layout', data);
export const downloadHerbLocationWorkbook = (storeId, template = false) =>
  request.get(`/admin/herb-locations/${template ? 'template' : 'export'}`, {
    params: { storeId },
    responseType: 'blob'
  });
export const downloadHerbLocationMoveTemplate = (storeId) =>
  request.get('/admin/herb-locations/move-template', {
    params: { storeId },
    responseType: 'blob'
  });
export const importHerbLocations = (storeId, file) => {
  const formData = new FormData();
  formData.append('storeId', storeId);
  formData.append('file', file);
  return request.post('/admin/herb-locations/import', formData);
};
export const importHerbLocationMoves = (storeId, file) => {
  const formData = new FormData();
  formData.append('storeId', storeId);
  formData.append('file', file);
  return request.post('/admin/herb-locations/move-import', formData);
};
