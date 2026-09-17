import request from './request';

export const getGoodsChecks = (params) => request.get('/admin/yd-goods-check', { params });
export const createGoodsCheck = (data) => request.post('/admin/yd-goods-check', data);
export const updateGoodsCheck = (id, data) => request.put(`/admin/yd-goods-check/${id}`, data);
export const deleteGoodsCheck = (id) => request.delete(`/admin/yd-goods-check/${id}`);
export const getGoodsCheck = (id) => request.get(`/admin/yd-goods-check/${id}`);
export const getGoodsCheckItems = (id, params) => request.get(`/admin/yd-goods-check/${id}/items`, { params });
export const getGoodsCheckCandidates = (id, params) => request.get(`/admin/yd-goods-check/${id}/candidates`, { params });
export const addInitialCount = (id, data) => request.post(`/admin/yd-goods-check/${id}/items`, data);
export const recountGoodsCheckItem = (itemId, data) => request.put(`/admin/yd-goods-check/items/${itemId}/recount`, data);
export const updateGoodsCheckLocation = (itemId, data) => request.put(`/admin/yd-goods-check/items/${itemId}/location`, data);
export const reviewGoodsCheckItem = (itemId, data) => request.post(`/admin/yd-goods-check/items/${itemId}/review`, data);
export const reviewGoodsCheckItems = (data) => request.post('/admin/yd-goods-check/items/review-batch', data);
export const finishGoodsCheck = (id) => request.post(`/admin/yd-goods-check/${id}/finish`);
export const exportGoodsCheck = (id, type) => request.get(`/admin/yd-goods-check/${id}/export`, { params: { type }, responseType: 'blob' });
