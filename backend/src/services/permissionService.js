import { AppError } from "../utils/appError.js";
import { isManager, isStoreMember, isSuperAdmin } from "../constants/roles.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import { TRANSFER_OUTBOUND_STATUS } from "../constants/storeTransfer.js";

export function assertManager(actor) {
  if (!isManager(actor)) throw new AppError("无管理员权限", 403);
}

export function assertStoreMember(actor) {
  if (!isSuperAdmin(actor) && !isStoreMember(actor)) {
    throw new AppError("无门店业务权限", 403);
  }
}

export function businessScope(actor, requestedStoreId) {
  assertStoreMember(actor);
  if (isSuperAdmin(actor)) {
    if (
      requestedStoreId !== undefined &&
      requestedStoreId !== null &&
      requestedStoreId !== ""
    ) {
      const storeId = Number(requestedStoreId);
      if (!Number.isInteger(storeId) || storeId <= 0)
        throw new AppError("门店参数不正确", 400);
      return { storeId };
    }
    return {};
  }
  const storeId = Number(actor.storeId);
  if (!Number.isInteger(storeId) || storeId <= 0)
    throw new AppError("门店账号未绑定门店", 403);
  return { storeId };
}

export async function resolveBusinessStoreId(prisma, actor, requestedStoreId) {
  assertStoreMember(actor);
  const storeId = Number(
    isSuperAdmin(actor) ? requestedStoreId : actor.storeId,
  );
  if (!Number.isInteger(storeId) || storeId <= 0)
    throw new AppError("请选择所属门店", 400);
  const store = await prisma.store.findUnique({ where: { id: storeId } });
  if (!store || store.deletedAt) throw new AppError("门店不存在", 404);
  if (store.status !== RECORD_STATUS.ENABLED)
    throw new AppError("所属门店已停用，不能新增业务数据", 400);
  return storeId;
}

export async function assertBusinessStore(prisma, actor, storeId) {
  const scope = businessScope(actor, storeId);
  const resolved = Number(storeId || scope.storeId);
  if (!resolved) throw new AppError("门店参数不正确", 400);
  const store = await prisma.store.findUnique({ where: { id: resolved } });
  if (!store || store.deletedAt) throw new AppError("门店不存在", 404);
  if (!isSuperAdmin(actor) && resolved !== Number(actor.storeId))
    throw new AppError("无权操作该门店数据", 403);
  return store;
}

function actorStoreId(actor) {
  const storeId = Number(actor?.storeId);
  if (!Number.isInteger(storeId) || storeId <= 0)
    throw new AppError("门店账号未绑定门店", 403);
  return storeId;
}

export function transferScope(actor, requestedStoreId) {
  assertManager(actor);
  if (isSuperAdmin(actor)) {
    if (
      requestedStoreId === undefined ||
      requestedStoreId === null ||
      requestedStoreId === ""
    )
      return {};
    const storeId = Number(requestedStoreId);
    if (!Number.isInteger(storeId) || storeId <= 0)
      throw new AppError("门店参数不正确", 400);
    return { OR: [{ fromStoreId: storeId }, { toStoreId: storeId }] };
  }
  const storeId = actorStoreId(actor);
  return { OR: [{ fromStoreId: storeId }, { toStoreId: storeId }] };
}

export async function resolveTransferStoreIds(prisma, actor, payload) {
  assertManager(actor);
  const fromStoreId = Number(payload.fromStoreId);
  const toStoreId = isSuperAdmin(actor)
    ? Number(payload.toStoreId)
    : actorStoreId(actor);
  if (!Number.isInteger(fromStoreId) || fromStoreId <= 0)
    throw new AppError("请选择调出门店", 400);
  if (!Number.isInteger(toStoreId) || toStoreId <= 0)
    throw new AppError("请选择调入门店", 400);
  if (fromStoreId === toStoreId)
    throw new AppError("调出门店和调入门店不能相同", 400);
  const stores = await prisma.store.findMany({
    where: { id: { in: [fromStoreId, toStoreId] }, deletedAt: null },
    select: { id: true, status: true },
  });
  if (stores.length !== 2) throw new AppError("调出或调入门店不存在", 404);
  if (stores.some((store) => store.status !== RECORD_STATUS.ENABLED))
    throw new AppError("停用门店不能参与调拨", 400);
  return { fromStoreId, toStoreId };
}

export function assertCanManageTransfer(actor, transfer) {
  assertManager(actor);
  if (isSuperAdmin(actor)) return;
  const managerStoreId = actorStoreId(actor);
  const managerStore =
    transfer.outboundStatus === TRANSFER_OUTBOUND_STATUS.PENDING
      ? transfer.toStoreId
      : transfer.fromStoreId;
  if (Number(managerStore) !== managerStoreId)
    throw new AppError("只有当前流程责任门店管理员可以修改或取消该调拨", 403);
}

export function assertCanConfirmTransferOutbound(actor, transfer) {
  assertManager(actor);
  if (
    !isSuperAdmin(actor) &&
    Number(transfer.fromStoreId) !== actorStoreId(actor)
  )
    throw new AppError("只有调出门店管理员可以确认调出", 403);
}

export function assertCanSubmitTransferReturn(actor, transfer) {
  assertManager(actor);
  if (
    !isSuperAdmin(actor) &&
    Number(transfer.toStoreId) !== actorStoreId(actor)
  )
    throw new AppError("只有调入门店管理员可以发起归还", 403);
}

export function assertCanConfirmTransferReturn(actor, transfer) {
  assertManager(actor);
  if (
    !isSuperAdmin(actor) &&
    Number(transfer.fromStoreId) !== actorStoreId(actor)
  )
    throw new AppError("只有调出门店管理员可以确认收货", 403);
}
