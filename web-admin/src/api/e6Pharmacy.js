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

export const getE6PharmacyCategoryMappings = (includeDisabled = false) =>
  request.get('/admin/e6-pharmacy/category-mappings', {
    params: includeDisabled ? { includeDisabled: '1' } : undefined
  });

export const saveE6PharmacyCategoryMapping = (id, data) =>
  id
    ? request.put(`/admin/e6-pharmacy/category-mappings/${id}`, data)
    : request.post('/admin/e6-pharmacy/category-mappings', data);

export const deleteE6PharmacyCategoryMapping = (id) =>
  request.delete(`/admin/e6-pharmacy/category-mappings/${id}`);
