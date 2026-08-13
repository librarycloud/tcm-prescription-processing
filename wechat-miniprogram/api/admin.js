import { request, uploadFile } from '../utils/request';

export function getStats(params = {}) {
  return request({ url: '/admin/stats', data: params });
}

export function getStores(params) {
  return request({ url: '/stores', data: params });
}

export function getPackages(params) {
  return request({ url: '/admin/packages', data: params });
}

export function getPrescriptions(params) {
  return request({ url: '/admin/prescriptions', data: params });
}

export function getPrescriptionDetail(id) {
  return request({ url: `/admin/prescriptions/${id}` });
}

export function createPrescription(data) {
  return request({ url: '/admin/prescriptions', method: 'POST', data });
}

export function updatePrescription(id, data) {
  return request({ url: `/admin/prescriptions/${id}`, method: 'PUT', data });
}

export function deletePrescription(id) {
  return request({ url: `/admin/prescriptions/${id}`, method: 'DELETE' });
}

export function deletePrescriptionAttachment(id) {
  return request({
    url: `/admin/prescriptions/${id}/attachment`,
    method: 'DELETE'
  });
}

export function uploadPrescriptionAttachment(id, file) {
  return uploadFile({
    url: `/admin/prescriptions/${id}/attachment?originalName=${encodeURIComponent(file.originalName)}`,
    filePath: file.filePath,
    formData: { originalName: file.originalName }
  });
}

export function getProcessingPlans(params) {
  return request({ url: '/admin/processing-plans', data: params });
}

export function createProcessingPlan(data) {
  return request({ url: '/admin/processing-plans', method: 'POST', data });
}

export function createProcessingPlanBatch(data) {
  return request({
    url: '/admin/processing-plans/batch',
    method: 'POST',
    data
  });
}

export function updateProcessingPlan(id, data) {
  return request({ url: `/admin/processing-plans/${id}`, method: 'PUT', data });
}

export function deleteProcessingPlan(id) {
  return request({ url: `/admin/processing-plans/${id}`, method: 'DELETE' });
}

export function getDictionaries(type) {
  return request({ url: '/admin/dictionaries', data: { type } });
}

export function getDoctors() {
  return request({ url: '/admin/doctors' });
}

export function transitionProcessingPlan(id, status, data = {}) {
  return request({
    url: `/admin/processing-plans/${id}/transition`,
    method: 'POST',
    data: { ...data, status }
  });
}

export function generateProcessingPlanPackage(id) {
  return request({
    url: `/admin/processing-plans/${id}/generate-package`,
    method: 'POST'
  });
}

export function delayProcessingPlan(id, data) {
  return request({
    url: `/admin/processing-plans/${id}/delay`,
    method: 'POST',
    data
  });
}

export function receiveProcessingNotice(id, data) {
  return request({
    url: `/admin/processing-plans/${id}/receive-notice`,
    method: 'POST',
    data
  });
}

export function findProcessingPlanByScan(code) {
  return request({
    url: '/admin/processing-plans/by-scan',
    data: { code }
  });
}

export function getProcessingWorkflow(id) {
  return request({ url: `/admin/processing-plans/${id}/workflow` });
}

export function uploadDispensingPhoto(id, filePath) {
  return uploadFile({
    url: `/admin/processing-plans/${id}/dispensing-complete`,
    filePath
  });
}

export function deleteDispensingPhoto(id, photoId) {
  return request({
    url: `/admin/processing-plans/${id}/photos/${photoId}`,
    method: 'DELETE'
  });
}

export function startProcessingEquipmentUsage(id, data) {
  return request({
    url: `/admin/processing-plans/${id}/equipment-usages`,
    method: 'POST',
    data
  });
}

export function finishProcessingEquipmentUsage(id, usageId, data) {
  return request({
    url: `/admin/processing-plans/${id}/equipment-usages/${usageId}/finish`,
    method: 'POST',
    data
  });
}

export function startPackagingEquipmentUsage(id, usageId, data) {
  return request({
    url: `/admin/processing-plans/${id}/equipment-usages/${usageId}/start-packaging`,
    method: 'POST',
    data
  });
}

export function voidProcessingEquipmentUsage(id, usageId, data) {
  return request({
    url: `/admin/processing-plans/${id}/equipment-usages/${usageId}/void`,
    method: 'POST',
    data
  });
}

export function transferFaultyProcessingEquipment(id, usageId, data) {
  return request({
    url: `/admin/processing-plans/${id}/equipment-usages/${usageId}/fault-transfer`,
    method: 'POST',
    data
  });
}

export function matchAdminUsers(phone) {
  return request({
    url: `/admin/users/match?phone=${encodeURIComponent(phone)}`
  });
}

export function getPackageDetail(id) {
  return request({ url: `/admin/packages/${id}` });
}

export function getPackageByPickupCode(pickupCode) {
  return request({
    url: `/admin/packages/by-code/${encodeURIComponent(pickupCode)}`
  });
}

export function createPackage(data) {
  return request({ url: '/admin/packages', method: 'POST', data });
}

export function updatePackage(id, data) {
  return request({ url: `/admin/packages/${id}`, method: 'PUT', data });
}

export function verifyPackage(pickupCode, pickupMethod, expressTrackingNo = '') {
  return request({
    url: '/admin/packages/verify',
    method: 'POST',
    data: { pickupCode, pickupMethod, expressTrackingNo }
  });
}

export function getStoreTransfers(params = {}) {
  return request({ url: '/admin/store-transfers', data: params });
}

export function getStoreTransferStats(params = {}) {
  return request({ url: '/admin/store-transfers/stats', data: params });
}

export function getTransferStores() {
  return request({ url: '/admin/store-transfers/stores' });
}

export function getStoreTransfer(id) {
  return request({ url: `/admin/store-transfers/${id}` });
}

export function createStoreTransfer(data) {
  return request({ url: '/admin/store-transfers', method: 'POST', data });
}

export function updateStoreTransfer(id, data) {
  return request({ url: `/admin/store-transfers/${id}`, method: 'PUT', data });
}

export function updateExpectedReturnDate(id, data) {
  return request({
    url: `/admin/store-transfers/${id}/expected-return-date`,
    method: 'PUT',
    data
  });
}

export function addStoreTransferReturns(id, data) {
  return request({
    url: `/admin/store-transfers/${id}/returns`,
    method: 'POST',
    data
  });
}

export function updateStoreTransferReturn(id, returnId, data) {
  return request({
    url: `/admin/store-transfers/${id}/returns/${returnId}`,
    method: 'PUT',
    data
  });
}

export function confirmStoreTransferOutbound(id) {
  return request({
    url: `/admin/store-transfers/${id}/confirm-outbound`,
    method: 'POST'
  });
}

export function confirmStoreTransferReturn(id, returnId) {
  return request({
    url: `/admin/store-transfers/${id}/returns/${returnId}/confirm`,
    method: 'POST'
  });
}

export function cancelStoreTransfer(id, data) {
  return request({
    url: `/admin/store-transfers/${id}/cancel`,
    method: 'POST',
    data
  });
}

export function getHerbLocationStores() {
  return request({ url: '/admin/herb-locations/stores' });
}

export function getHerbLocations(storeId) {
  return request({
    url: '/admin/herb-locations',
    data: storeId ? { storeId } : {}
  });
}

export function saveHerbLocationAssignment(data) {
  return request({
    url: '/admin/herb-locations/assignments',
    method: 'POST',
    data
  });
}

export function updateHerbLocationAssignment(id, data) {
  return request({
    url: `/admin/herb-locations/assignments/${id}`,
    method: 'PUT',
    data
  });
}

export function updateHerb(id, data) {
  return request({
    url: `/admin/herb-locations/herbs/${id}`,
    method: 'PUT',
    data
  });
}

export function removeHerbLocationAssignment(id) {
  return request({
    url: `/admin/herb-locations/assignments/${id}`,
    method: 'DELETE'
  });
}

export function getProductDifferenceStats(params = {}) {
  return request({ url: '/admin/product-differences/stats', data: params });
}

export function getProductDifferenceLogs(params = {}) {
  return request({ url: '/admin/product-differences/logs', data: params });
}

export function getProducts(params = {}) {
  return request({ url: '/admin/products', data: params });
}

export function registerProductDifference(data) {
  return request({
    url: '/admin/product-differences/register',
    method: 'POST',
    data
  });
}

export function writeOffProductDifference(data) {
  return request({
    url: '/admin/product-differences/write-off',
    method: 'POST',
    data
  });
}

export function reverseProductDifference(id, data) {
  return request({
    url: `/admin/product-differences/logs/${id}/reverse`,
    method: 'POST',
    data
  });
}
