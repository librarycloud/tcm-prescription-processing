import request from './request';

export function getStats(params) {
  return request.get('/admin/stats', { params });
}

export function getAdminPackages(params) {
  return request.get('/admin/packages', { params });
}

export function getAdminPackageDetail(id) {
  return request.get(`/admin/packages/${id}`);
}

export function getAdminPackageByPickupCode(pickupCode) {
  return request.get(`/admin/packages/by-code/${encodeURIComponent(pickupCode)}`);
}

export function createPackage(data) {
  return request.post('/admin/packages', data);
}

export function updatePackage(id, data) {
  return request.put(`/admin/packages/${id}`, data);
}

export function deletePackage(id) {
  return request.delete(`/admin/packages/${id}`);
}

export function verifyPackage(pickupCode, pickupMethod, expressTrackingNo = '') {
  return request.post('/admin/packages/verify', { pickupCode, pickupMethod, expressTrackingNo });
}
