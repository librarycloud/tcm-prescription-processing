import { ok } from "../utils/response.js";
import {
  cancelStoreTransfer,
  confirmStoreTransferOutbound,
  confirmStoreTransferReturn,
  createStoreTransfer,
  getStoreTransfer,
  getStoreTransferStats,
  listStoreTransfers,
  listTransferStores,
  submitStoreTransferReturns,
  updateExpectedReturnDate,
  updateStoreTransfer,
  updateStoreTransferReturn,
} from "../services/storeTransferService.js";

export async function listController(request, reply) {
  return ok(reply, await listStoreTransfers(request.server.prisma, request.user, request.query || {}));
}

export async function statsController(request, reply) {
  return ok(reply, await getStoreTransferStats(request.server.prisma, request.user, request.query || {}));
}

export async function storesController(request, reply) {
  return ok(reply, await listTransferStores(request.server.prisma));
}

export async function detailController(request, reply) {
  return ok(reply, await getStoreTransfer(request.server.prisma, request.user, request.params.id));
}

export async function createController(request, reply) {
  return ok(reply, await createStoreTransfer(request.server.prisma, request.user, request.body || {}), "创建成功");
}

export async function updateController(request, reply) {
  return ok(reply, await updateStoreTransfer(request.server.prisma, request.user, request.params.id, request.body || {}), "更新成功");
}

export async function updateExpectedReturnDateController(request, reply) {
  return ok(reply, await updateExpectedReturnDate(request.server.prisma, request.user, request.params.id, request.body || {}), "预计归还日期已更新");
}

export async function addReturnsController(request, reply) {
  return ok(reply, await submitStoreTransferReturns(request.server.prisma, request.user, request.params.id, request.body || {}), "归还申请已提交，等待调出门店确认收货");
}

export async function updateReturnController(request, reply) {
  return ok(
    reply,
    await updateStoreTransferReturn(
      request.server.prisma,
      request.user,
      request.params.id,
      request.params.returnId,
      request.body || {},
    ),
    "归还记录已更新",
  );
}

export async function confirmOutboundController(request, reply) {
  return ok(reply, await confirmStoreTransferOutbound(request.server.prisma, request.user, request.params.id), "已确认调出");
}

export async function confirmReturnController(request, reply) {
  return ok(reply, await confirmStoreTransferReturn(request.server.prisma, request.user, request.params.id, request.params.returnId), "已确认收货");
}

export async function cancelController(request, reply) {
  return ok(reply, await cancelStoreTransfer(request.server.prisma, request.user, request.params.id, request.body || {}), "调拨已取消");
}
