import { ok } from "../utils/response.js";
import {
  addInitialCount,
  createGoodsCheck,
  deleteGoodsCheck,
  exportGoodsCheck,
  finishGoodsCheck,
  getGoodsCheck,
  listGoodsCheckCandidates,
  listGoodsCheckItems,
  listGoodsChecks,
  recountGoodsCheckItem,
  reviewGoodsCheckItems,
  reviewGoodsCheckItem,
  updateGoodsCheckLocation,
  updateGoodsCheck,
} from "../services/ydGoodsCheckService.js";

export async function listGoodsChecksController(request, reply) {
  return ok(reply, await listGoodsChecks(request.server.prisma, request.user, request.query || {}));
}

export async function createGoodsCheckController(request, reply) {
  return ok(reply, await createGoodsCheck(request.server.prisma, request.user, request.body || {}), "盘点单已创建");
}

export async function updateGoodsCheckController(request, reply) {
  return ok(reply, await updateGoodsCheck(request.server.prisma, request.user, request.params.id, request.body || {}), "盘点单已更新");
}

export async function deleteGoodsCheckController(request, reply) {
  return ok(reply, await deleteGoodsCheck(request.server.prisma, request.user, request.params.id), "盘点单及盘点记录已删除");
}

export async function goodsCheckDetailController(request, reply) {
  return ok(reply, await getGoodsCheck(request.server.prisma, request.user, request.params.id));
}

export async function goodsCheckItemsController(request, reply) {
  return ok(reply, await listGoodsCheckItems(request.server.prisma, request.user, request.params.id, request.query || {}));
}

export async function goodsCheckCandidatesController(request, reply) {
  return ok(reply, await listGoodsCheckCandidates(request.server.prisma, request.user, request.params.id, request.query || {}));
}

export async function addInitialCountController(request, reply) {
  return ok(reply, await addInitialCount(request.server.prisma, request.user, request.params.id, request.body || {}), "初盘已保存");
}

export async function recountGoodsCheckItemController(request, reply) {
  return ok(reply, await recountGoodsCheckItem(request.server.prisma, request.user, request.params.itemId, request.body || {}), "复盘已保存");
}

export async function updateGoodsCheckLocationController(request, reply) {
  return ok(reply, await updateGoodsCheckLocation(request.server.prisma, request.user, request.params.itemId, request.body || {}), "货位已保存");
}

export async function reviewGoodsCheckItemController(request, reply) {
  return ok(reply, await reviewGoodsCheckItem(request.server.prisma, request.user, request.params.itemId, request.body || {}), "复核已保存");
}

export async function reviewGoodsCheckItemsController(request, reply) {
  const body = request.body || {};
  return ok(reply, await reviewGoodsCheckItems(request.server.prisma, request.user, body.itemIds, body), "批量复核已保存");
}

export async function finishGoodsCheckController(request, reply) {
  return ok(reply, await finishGoodsCheck(request.server.prisma, request.user, request.params.id), "盘点单已完成");
}

export async function exportGoodsCheckController(request, reply) {
  const result = await exportGoodsCheck(request.server.prisma, request.user, request.params.id, request.query?.type || "all");
  return reply
    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    .header("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(result.filename)}`)
    .send(result.buffer);
}
