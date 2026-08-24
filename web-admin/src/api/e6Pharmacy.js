import request from './request';

export const getE6PharmacyProducts = (params) =>
  request.get('/admin/e6-pharmacy/products', { params });

export const downloadE6PharmacyBarcodeTemplate = () =>
  request.get('/admin/e6-pharmacy/barcode-template', { responseType: 'blob' });

export const importE6PharmacyBarcodes = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/admin/e6-pharmacy/barcode-import', formData);
};
