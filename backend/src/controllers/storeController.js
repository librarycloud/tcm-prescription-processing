import { createStore, deleteStore, getStore, listStores, updateStore } from '../services/storeService.js';
import { ok } from '../utils/response.js';

export async function listStoresController(request, reply) {
  return ok(reply, await listStores(request.server.prisma, request.query || {}));
}

export async function getStoreController(request, reply) {
  return ok(reply, await getStore(request.server.prisma, request.params.id));
}

export async function createStoreController(request, reply) {
  return ok(reply, await createStore(request.server.prisma, request.body || {}, request.user), '新增成功');
}

export async function updateStoreController(request, reply) {
  return ok(
    reply,
    await updateStore(request.server.prisma, request.params.id, request.body || {}, request.user),
    '更新成功'
  );
}

export async function deleteStoreController(request, reply) {
  return ok(reply, await deleteStore(request.server.prisma, request.params.id, request.user), '删除成功');
}
