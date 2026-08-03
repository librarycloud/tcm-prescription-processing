import { ok } from '../utils/response.js';
import { createPrescription, deletePrescription, getPrescription, listPrescriptions, updatePrescription } from '../services/prescriptionService.js';

export async function listController(request, reply) { return ok(reply, await listPrescriptions(request.server.prisma, request.user, request.query || {})); }
export async function detailController(request, reply) { return ok(reply, await getPrescription(request.server.prisma, request.user, request.params.id)); }
export async function createController(request, reply) { return ok(reply, await createPrescription(request.server.prisma, request.user, request.body || {}), '创建成功'); }
export async function updateController(request, reply) { return ok(reply, await updatePrescription(request.server.prisma, request.user, request.params.id, request.body || {}), '更新成功'); }
export async function deleteController(request, reply) { return ok(reply, await deletePrescription(request.server.prisma, request.user, request.params.id), '删除成功'); }
