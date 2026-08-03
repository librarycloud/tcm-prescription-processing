import {
  createPackage,
  deletePackage,
  getPackageByPickupCode,
  getPackageDetail,
  getStats,
  listPackages,
  updatePackage,
  verifyPackage
} from '../services/adminPackageService.js';
import { ok } from '../utils/response.js';

export async function statsController(request, reply) {
  return ok(reply, await getStats(request.server.prisma, request.user, request.query || {}));
}

export async function listController(request, reply) {
  return ok(reply, await listPackages(request.server.prisma, request.user, request.query || {}));
}

export async function detailController(request, reply) {
  return ok(reply, await getPackageDetail(request.server.prisma, request.user, request.params.id));
}

export async function pickupCodeDetailController(request, reply) {
  return ok(
    reply,
    await getPackageByPickupCode(request.server.prisma, request.user, request.params.pickupCode)
  );
}

export async function createController(request, reply) {
  const data = await createPackage(request.server.prisma, request.user, request.body || {});
  return ok(reply, data, '新增成功');
}

export async function updateController(request, reply) {
  const data = await updatePackage(
    request.server.prisma,
    request.user,
    request.params.id,
    request.body || {}
  );
  return ok(reply, data, '更新成功');
}

export async function deleteController(request, reply) {
  const data = await deletePackage(request.server.prisma, request.user, request.params.id);
  return ok(reply, data, '删除成功');
}

export async function verifyController(request, reply) {
  const data = await verifyPackage(request.server.prisma, request.user, request.body || {});
  return ok(reply, data, '核销成功');
}
