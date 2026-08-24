import {
  getCurrentUser,
  getMyPackageDetail,
  listMyPackages,
  updateCurrentUser
} from '../services/userPackageService.js';
import { ok } from '../utils/response.js';

export async function listController(request, reply) {
  return ok(reply, await listMyPackages(request.server.prisma, request.user));
}

export async function detailController(request, reply) {
  return ok(reply, await getMyPackageDetail(request.server.prisma, request.user, request.params.id));
}

export async function meController(request, reply) {
  return ok(reply, await getCurrentUser(request.server.prisma, request.user));
}

export async function updateMeController(request, reply) {
  const data = await updateCurrentUser(
    request.server.prisma,
    request.server.jwt,
    request.server.authSessions,
    request.user,
    request.body || {}
  );
  return ok(reply, data, '修改成功');
}
