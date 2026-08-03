import request from './request';

export function getPrintTemplates(params = {}) {
  return request.get('/admin/print-templates', { params });
}

export function createPrintTemplate(data) {
  return request.post('/admin/print-templates', data);
}

export function updatePrintTemplate(id, data) {
  return request.put(`/admin/print-templates/${id}`, data);
}

export function deletePrintTemplate(id) {
  return request.delete(`/admin/print-templates/${id}`);
}
