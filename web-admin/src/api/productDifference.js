import request from './request';

export const getProductStores = () => request.get('/admin/products/stores');
export const getProducts = (params) => request.get('/admin/products', { params });
export const createProduct = (data) => request.post('/admin/products', data);
export const updateProduct = (id, data) => request.put(`/admin/products/${id}`, data);
export const getProductDiffStats = (params) =>
  request.get('/admin/product-differences/stats', { params });
export const getProductDiffLogs = (params) =>
  request.get('/admin/product-differences/logs', { params });
export const registerProductDifference = (data) =>
  request.post('/admin/product-differences/register', data);
export const writeOffProductDifference = (data) =>
  request.post('/admin/product-differences/write-off', data);
export const reverseProductDiffLog = (id, data) =>
  request.post(`/admin/product-differences/logs/${id}/reverse`, data);

export const downloadProductImportTemplate = () =>
  request.get('/admin/products/import-template', { responseType: 'blob' });

function productImportForm(storeId, file, overwriteDifference) {
  const formData = new FormData();
  formData.append('storeId', storeId);
  formData.append('overwriteDifference', overwriteDifference ? '1' : '0');
  formData.append('file', file);
  return formData;
}

export const previewProductImport = (storeId, file, overwriteDifference) =>
  request.post(
    '/admin/products/import-preview',
    productImportForm(storeId, file, overwriteDifference)
  );

export const importProducts = (storeId, file, overwriteDifference) =>
  request.post('/admin/products/import', productImportForm(storeId, file, overwriteDifference));
