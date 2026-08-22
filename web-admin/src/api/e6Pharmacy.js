import request from './request';

export const getE6PharmacyProducts = (params) =>
request.get('/admin/e6-pharmacy/products', { params });
