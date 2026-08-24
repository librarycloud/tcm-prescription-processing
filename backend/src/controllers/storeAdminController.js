import {
  createStoreAdmin,
  deleteStoreAdmin,
  listStoreAdmins,
  updateStoreAdmin
} from '../services/storeAdminService.js';
import { ok } from '../utils/response.js';

export async function listStoreAdminsController(request, reply) {
  return ok(reply, await listStoreAdmins(request.server.prisma, request.query || {}, request.user));
}

export async function createStoreAdminController(request, reply) {
  return ok(reply, await createStoreAdmin(request.server.prisma, request.body || {}, request.user, request.server.authSessions), '创建成功');
}

export async function updateStoreAdminController(request, reply) {
  return ok(
    reply,
    await updateStoreAdmin(request.server.prisma, request.params.id, request.body || {}, request.user, request.server.authSessions),
    '更新成功'
  );
}

export async function deleteStoreAdminController(request, reply) {
  return ok(
    reply,
    await deleteStoreAdmin(request.server.prisma, request.params.id, request.user, request.server.authSessions),
    '删除成功'
  );
}
