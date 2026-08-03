import request from './request';

export const getStoreTransfers = (params) => request.get('/admin/store-transfers', { params });
export const getStoreTransferStats = (params) =>
  request.get('/admin/store-transfers/stats', { params });
export const getTransferStores = () => request.get('/admin/store-transfers/stores');
export const getStoreTransfer = (id) => request.get(`/admin/store-transfers/${id}`);
export const createStoreTransfer = (data) => request.post('/admin/store-transfers', data);
export const confirmStoreTransferOutbound = (id) =>
  request.post(`/admin/store-transfers/${id}/confirm-outbound`);
export const updateStoreTransfer = (id, data) => request.put(`/admin/store-transfers/${id}`, data);
export const updateExpectedReturnDate = (id, data) =>
  request.put(`/admin/store-transfers/${id}/expected-return-date`, data);
export const addStoreTransferReturns = (id, data) =>
  request.post(`/admin/store-transfers/${id}/returns`, data);
export const confirmStoreTransferReturn = (id, returnId) =>
  request.post(`/admin/store-transfers/${id}/returns/${returnId}/confirm`);
export const cancelStoreTransfer = (id, data) =>
  request.post(`/admin/store-transfers/${id}/cancel`, data);
