import { ok } from "../utils/response.js";
import {
  createProcessingEquipment,
  deleteProcessingEquipment,
  listProcessingEquipment,
  updateProcessingEquipment,
} from "../services/processingEquipmentService.js";

export async function listController(request, reply) {
  return ok(reply, await listProcessingEquipment(request.server.prisma, request.user, request.query || {}));
}

export async function createController(request, reply) {
  return ok(reply, await createProcessingEquipment(request.server.prisma, request.user, request.body || {}), "设备已新增");
}

export async function updateController(request, reply) {
  return ok(reply, await updateProcessingEquipment(request.server.prisma, request.user, request.params.id, request.body || {}), "设备已更新");
}

export async function deleteController(request, reply) {
  return ok(reply, await deleteProcessingEquipment(request.server.prisma, request.user, request.params.id), "设备已删除");
}
