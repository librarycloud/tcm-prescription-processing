import { ok } from '../utils/response.js';
import {
  createPrintTemplate,
  deletePrintTemplate,
  getPrintTemplateSettings,
  updatePrintTemplate
} from '../services/printTemplateService.js';

export async function listPrintTemplatesController(request, reply) {
  return ok(reply, await getPrintTemplateSettings(request.server.prisma, request.user, request.query || {}));
}

export async function createPrintTemplateController(request, reply) {
  return ok(reply, await createPrintTemplate(request.server.prisma, request.user, request.body || {}), '打印模板已创建');
}

export async function updatePrintTemplateController(request, reply) {
  return ok(reply, await updatePrintTemplate(request.server.prisma, request.user, request.params.id, request.body || {}), '打印模板已保存');
}

export async function deletePrintTemplateController(request, reply) {
  return ok(reply, await deletePrintTemplate(request.server.prisma, request.user, request.params.id), '打印模板已删除');
}
