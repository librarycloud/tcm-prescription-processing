import { createUser, deleteUser, listUsers, lookupUsers, updateUser } from '../services/adminUserService.js';
import { ok } from '../utils/response.js';

export async function listUsersController(request, reply) {
  return ok(
    reply,
    await listUsers(
      request.server.prisma,
      request.server.ipLookup,
      request.query || {},
      request.user
    )
  );
}

export async function lookupUsersController(request, reply) {
  return ok(reply, await lookupUsers(request.server.prisma, request.query?.phone));
}

export async function updateUserController(request, reply) {
  const data = await updateUser(request.server.prisma, request.params.id, request.body || {}, request.user, request.server.authSessions);
  return ok(reply, data, '更新成功');
}

export async function createUserController(request, reply) {
  return ok(reply, await createUser(request.server.prisma, request.body || {}, request.user), '创建成功');
}

export async function deleteUserController(request, reply) {
  const data = await deleteUser(request.server.prisma, request.params.id, request.user, request.server.authSessions);
  return ok(reply, data, '删除成功');
}
