import { ok } from "../utils/response.js";
import {
  confirmE6Import,
  deleteE6DoctorMapping,
  deleteE6OperatorMapping,
  getE6Import,
  getE6StoreConfig,
  listE6DoctorMappings,
  listE6OperatorMappings,
  listE6Imports,
  mergeE6Imports,
  receiveE6Prescription,
  rejectE6Import,
  revalidateE6Import,
  saveE6DoctorMapping,
  saveE6OperatorMapping,
  saveE6StoreConfig,
} from "../services/e6IntegrationService.js";

export async function receivePrescriptionController(request, reply) {
  return ok(
    reply,
    await receiveE6Prescription(
      request.server.prisma,
      request.body || {},
      request.headers["x-api-key"],
      { ip: request.ip, userAgent: request.headers["user-agent"] },
    ),
    "同步成功",
  );
}

export async function getStoreConfigController(request, reply) {
  return ok(
    reply,
    await getE6StoreConfig(
      request.server.prisma,
      request.user,
      request.params.storeId,
    ),
  );
}

export async function saveStoreConfigController(request, reply) {
  return ok(
    reply,
    await saveE6StoreConfig(
      request.server.prisma,
      request.user,
      request.params.storeId,
      request.body || {},
    ),
    "保存成功",
  );
}

export async function listDoctorMappingsController(request, reply) {
  return ok(
    reply,
    await listE6DoctorMappings(
      request.server.prisma,
      request.user,
      request.query || {},
    ),
  );
}

export async function listOperatorMappingsController(request, reply) {
  return ok(reply, await listE6OperatorMappings(request.server.prisma, request.user, request.query || {}));
}

export async function createOperatorMappingController(request, reply) {
  return ok(reply, await saveE6OperatorMapping(request.server.prisma, request.user, null, request.body || {}), "创建成功");
}

export async function updateOperatorMappingController(request, reply) {
  return ok(reply, await saveE6OperatorMapping(request.server.prisma, request.user, request.params.id, request.body || {}), "更新成功");
}

export async function deleteOperatorMappingController(request, reply) {
  return ok(reply, await deleteE6OperatorMapping(request.server.prisma, request.user, request.params.id), "删除成功");
}

export async function createDoctorMappingController(request, reply) {
  return ok(
    reply,
    await saveE6DoctorMapping(
      request.server.prisma,
      request.user,
      null,
      request.body || {},
    ),
    "创建成功",
  );
}

export async function updateDoctorMappingController(request, reply) {
  return ok(
    reply,
    await saveE6DoctorMapping(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body || {},
    ),
    "更新成功",
  );
}

export async function deleteDoctorMappingController(request, reply) {
  return ok(
    reply,
    await deleteE6DoctorMapping(
      request.server.prisma,
      request.user,
      request.params.id,
    ),
    "删除成功",
  );
}

export async function listImportsController(request, reply) {
  return ok(
    reply,
    await listE6Imports(
      request.server.prisma,
      request.user,
      request.query || {},
    ),
  );
}

export async function importDetailController(request, reply) {
  return ok(
    reply,
    await getE6Import(
      request.server.prisma,
      request.user,
      request.params.id,
    ),
  );
}

export async function confirmImportController(request, reply) {
  return ok(
    reply,
    await confirmE6Import(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body || {},
    ),
    "已生成处方并进入加工工作台",
  );
}

export async function mergeImportsController(request, reply) {
  return ok(reply, await mergeE6Imports(request.server.prisma, request.user, request.body || {}), "已合并生成处方");
}

export async function rejectImportController(request, reply) {
  return ok(
    reply,
    await rejectE6Import(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body || {},
    ),
    "已驳回",
  );
}

export async function revalidateImportController(request, reply) {
  return ok(
    reply,
    await revalidateE6Import(
      request.server.prisma,
      request.user,
      request.params.id,
    ),
    "校验完成",
  );
}
