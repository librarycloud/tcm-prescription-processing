import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import {
  ACTIVE_TRANSFER_STATUSES,
  TRANSFER_OUTBOUND_STATUS,
  TRANSFER_RETURN_STATUS,
  TRANSFER_STATUS,
  TRANSFER_STATUS_VALUES,
} from "../constants/storeTransfer.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import { toPositiveInt } from "../utils/validators.js";
import {
  assertCanManageTransfer,
  assertCanConfirmTransferOutbound,
  assertCanConfirmTransferReturn,
  assertCanSubmitTransferReturn,
  resolveTransferStoreIds,
  transferScope,
} from "./permissionService.js";
import { recordOperation } from "./operationLogService.js";
import { nextStoreTransferNo } from "./storeTransferNoService.js";
import { prescriptionBusinessDate } from "./prescriptionNoService.js";
import { storeTransferRepository } from "../repositories/storeTransferRepository.js";
import { publishTransferRobotEvent } from "./robotBusinessEventService.js";

const MAX_ITEMS = 50;
const RETURN_STATUS = TRANSFER_RETURN_STATUS;
const OUTBOUND_STATUS = TRANSFER_OUTBOUND_STATUS;

function text(value, max, label, required = false) {
  const result = String(value ?? "").trim();
  if (required && !result) throw new AppError(`请输入${label}`, 400);
  if (result.length > max)
    throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return result || null;
}

function dateOnly(value, label, required = true) {
  const source =
    value instanceof Date
      ? value.toISOString().slice(0, 10)
      : String(value || "");
  if (!source && !required) return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(source))
    throw new AppError(`${label}格式不正确`, 400);
  const date = new Date(`${source}T00:00:00.000Z`);
  if (
    Number.isNaN(date.getTime()) ||
    date.toISOString().slice(0, 10) !== source
  )
    throw new AppError(`${label}格式不正确`, 400);
  return date;
}

function numberQuantity(value, label = "数量") {
  const quantity = Number(value);
  if (!Number.isFinite(quantity) || quantity <= 0)
    throw new AppError(`${label}必须大于 0`, 400);
  const normalized = Math.round(quantity * 1000) / 1000;
  if (normalized > 999999999.999)
    throw new AppError(`${label}超出允许范围`, 400);
  return normalized;
}

function normalizeItems(items) {
  if (!Array.isArray(items) || !items.length)
    throw new AppError("请至少添加一项调拨明细", 400);
  if (items.length > MAX_ITEMS)
    throw new AppError(`一张调拨单最多包含 ${MAX_ITEMS} 项明细`, 400);
  return items.map((item) => ({
    itemName: text(item.itemName, 120, "物品名称", true),
    specification: text(item.specification, 120, "规格"),
    batchNo: text(item.batchNo, 100, "批号"),
    quantity: numberQuantity(item.quantity, "借调数量"),
    unit: text(item.unit, 20, "单位", true),
    remark: text(item.remark, 500, "明细备注"),
  }));
}

function detailInclude() {
  return {
    fromStore: { select: { id: true, name: true, code: true } },
    toStore: { select: { id: true, name: true, code: true } },
    creator: { select: { id: true, nickname: true, name: true, phone: true } },
    outboundConfirmer: {
      select: { id: true, nickname: true, name: true, phone: true },
    },
    items: {
      orderBy: { id: "asc" },
      include: {
        returns: {
          orderBy: [{ returnDate: "desc" }, { createdAt: "desc" }],
          include: {
            operator: {
              select: { id: true, nickname: true, name: true, phone: true },
            },
            confirmer: {
              select: { id: true, nickname: true, name: true, phone: true },
            },
          },
        },
      },
    },
  };
}

function dateKey(value) {
  return value instanceof Date
    ? value.toISOString().slice(0, 10)
    : String(value || "").slice(0, 10);
}

function actorPermissions(actor, transfer, hasReturns, hasPendingReturns) {
  const superAdmin = isSuperAdmin(actor);
  const actorStore = Number(actor?.storeId);
  const active = ![
    TRANSFER_STATUS.RETURNED,
    TRANSFER_STATUS.CANCELLED,
  ].includes(transfer.status);
  const outboundConfirmed =
    transfer.outboundStatus === OUTBOUND_STATUS.CONFIRMED;
  const managingStoreId = outboundConfirmed
    ? Number(transfer.fromStoreId)
    : Number(transfer.toStoreId);
  return {
    canUpdate:
      active &&
      !outboundConfirmed &&
      (superAdmin || managingStoreId === actorStore),
    canUpdateExpectedReturnDate:
      active && (superAdmin || managingStoreId === actorStore),
    canConfirmOutbound:
      active &&
      !outboundConfirmed &&
      (superAdmin || Number(transfer.fromStoreId) === actorStore),
    canSubmitReturn:
      active &&
      outboundConfirmed &&
      (superAdmin || Number(transfer.toStoreId) === actorStore),
    canConfirmReturn:
      active &&
      outboundConfirmed &&
      hasPendingReturns &&
      (superAdmin || Number(transfer.fromStoreId) === actorStore),
    canCancel:
      active && !hasReturns && (superAdmin || managingStoreId === actorStore),
  };
}

export function calculateTransferStatus(items) {
  const totals = items.reduce(
    (result, item) => {
      result.quantity += Number(item.quantity) || 0;
      result.returned += Number(item.returnedQuantity) || 0;
      return result;
    },
    { quantity: 0, returned: 0 },
  );
  if (totals.returned <= 0) return TRANSFER_STATUS.BORROWING;
  if (totals.returned >= totals.quantity) return TRANSFER_STATUS.RETURNED;
  return TRANSFER_STATUS.PART_RETURNED;
}

function withComputed(transfer, actor) {
  const returnRecords = [];
  const items = (transfer.items || []).map((item) => {
    const quantity = Number(item.quantity);
    const returns = (item.returns || []).map((record) => {
      const normalized = {
        ...record,
        quantity: Number(record.quantity),
        status: record.status ?? RETURN_STATUS.PENDING,
        itemName: item.itemName,
      };
      returnRecords.push(normalized);
      return normalized;
    });
    const returnedQuantity =
      Math.round(
        returns
          .filter((record) => record.status === RETURN_STATUS.CONFIRMED)
          .reduce((sum, record) => sum + record.quantity, 0) * 1000,
      ) / 1000;
    const pendingReturnQuantity =
      Math.round(
        returns
          .filter((record) => record.status === RETURN_STATUS.PENDING)
          .reduce((sum, record) => sum + record.quantity, 0) * 1000,
      ) / 1000;
    return {
      ...item,
      quantity,
      returns,
      returnedQuantity,
      pendingReturnQuantity,
      remainingQuantity: Math.max(
        Math.round((quantity - returnedQuantity) * 1000) / 1000,
        0,
      ),
      availableReturnQuantity: Math.max(
        Math.round(
          (quantity - returnedQuantity - pendingReturnQuantity) * 1000,
        ) / 1000,
        0,
      ),
    };
  });
  returnRecords.sort((left, right) =>
    String(right.createdAt).localeCompare(String(left.createdAt)),
  );
  const hasReturns = returnRecords.length > 0;
  const hasPendingReturns = returnRecords.some(
    (record) => record.status === RETURN_STATUS.PENDING,
  );
  const overdue =
    transfer.outboundStatus === OUTBOUND_STATUS.CONFIRMED &&
    ACTIVE_TRANSFER_STATUSES.includes(transfer.status) &&
    dateKey(transfer.expectedReturnDate) < prescriptionBusinessDate();
  return {
    ...transfer,
    items,
    returnRecords,
    overdue,
    permissions: actorPermissions(
      actor,
      transfer,
      hasReturns,
      hasPendingReturns,
    ),
  };
}

function scopedWhere(actor, query = {}) {
  const conditions = [transferScope(actor, query.storeId)];
  const where = {};
  if (
    query.status !== undefined &&
    query.status !== null &&
    query.status !== ""
  ) {
    const status = Number(query.status);
    if (!TRANSFER_STATUS_VALUES.includes(status))
      throw new AppError("调拨状态不正确", 400);
    where.status = status;
  }
  if (String(query.pending || "") === "1")
    Object.assign(where, {
      status: { in: ACTIVE_TRANSFER_STATUSES },
      outboundStatus: OUTBOUND_STATUS.CONFIRMED,
    });
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    conditions.push({
      OR: [
        { transferNo: { contains: keyword } },
        { fromStore: { name: { contains: keyword } } },
        { toStore: { name: { contains: keyword } } },
        { items: { some: { itemName: { contains: keyword } } } },
        { items: { some: { specification: { contains: keyword } } } },
        { items: { some: { batchNo: { contains: keyword } } } },
      ],
    });
  }
  const startDate = query.startDate
    ? dateOnly(query.startDate, "开始日期")
    : null;
  const endDate = query.endDate ? dateOnly(query.endDate, "结束日期") : null;
  if (startDate || endDate) {
    where.transferDate = {};
    if (startDate) where.transferDate.gte = startDate;
    if (endDate) where.transferDate.lte = endDate;
  }
  if (String(query.overdue || "") === "1") {
    where.status = { in: ACTIVE_TRANSFER_STATUSES };
    where.outboundStatus = OUTBOUND_STATUS.CONFIRMED;
    where.expectedReturnDate = {
      lt: dateOnly(prescriptionBusinessDate(), "当前日期"),
    };
  }
  where.AND = conditions;
  return where;
}

export async function listStoreTransfers(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 10), 100);
  const where = scopedWhere(actor, query);
  const [list, total] = await Promise.all([
    storeTransferRepository.findMany(prisma, {
      where,
      include: detailInclude(),
      orderBy: [{ transferDate: "desc" }, { createdAt: "desc" }],
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    storeTransferRepository.count(prisma, { where }),
  ]);
  return {
    list: list.map((item) => withComputed(item, actor)),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function getStoreTransfer(prisma, actor, idValue) {
  const transfer = await storeTransferRepository.findFirst(prisma, {
    where: { id: Number(idValue), AND: [transferScope(actor)] },
    include: detailInclude(),
  });
  if (!transfer) throw new AppError("调拨单不存在", 404);
  return withComputed(transfer, actor);
}

export async function listTransferStores(prisma) {
  return prisma.store.findMany({
    where: { deletedAt: null, status: RECORD_STATUS.ENABLED },
    select: { id: true, name: true, code: true },
    orderBy: [{ name: "asc" }, { id: "asc" }],
  });
}

export async function getStoreTransferStats(prisma, actor, query = {}) {
  const scope = transferScope(actor, query.storeId);
  const today = dateOnly(prescriptionBusinessDate(), "当前日期");
  const [borrowing, partReturned, overdue] = await Promise.all([
    storeTransferRepository.count(prisma, {
      where: {
        AND: [scope],
        status: TRANSFER_STATUS.BORROWING,
        outboundStatus: OUTBOUND_STATUS.CONFIRMED,
      },
    }),
    storeTransferRepository.count(prisma, {
      where: {
        AND: [scope],
        status: TRANSFER_STATUS.PART_RETURNED,
        outboundStatus: OUTBOUND_STATUS.CONFIRMED,
      },
    }),
    storeTransferRepository.count(prisma, {
      where: {
        AND: [scope],
        status: { in: ACTIVE_TRANSFER_STATUSES },
        outboundStatus: OUTBOUND_STATUS.CONFIRMED,
        expectedReturnDate: { lt: today },
      },
    }),
  ]);
  return {
    borrowing,
    partReturned,
    pending: borrowing + partReturned,
    overdue,
  };
}

export async function createStoreTransfer(prisma, actor, payload) {
  const { fromStoreId, toStoreId } = await resolveTransferStoreIds(
    prisma,
    actor,
    payload,
  );
  const transferDate = dateOnly(payload.transferDate, "调拨日期");
  const expectedReturnDate = dateOnly(
    payload.expectedReturnDate,
    "预计归还日期",
  );
  if (expectedReturnDate < transferDate)
    throw new AppError("预计归还日期不能早于调拨日期", 400);
  const items = normalizeItems(payload.items);
  const created = await prisma.$transaction(async (tx) => {
    const transferNo = await nextStoreTransferNo(tx);
    const created = await storeTransferRepository.create(tx, {
      data: {
        transferNo,
        fromStoreId,
        toStoreId,
        transferDate,
        expectedReturnDate,
        status: TRANSFER_STATUS.BORROWING,
        outboundStatus: OUTBOUND_STATUS.PENDING,
        remark: text(payload.remark, 500, "备注"),
        createdBy: Number(actor.id),
        items: { create: items },
      },
      include: detailInclude(),
    });
    await recordOperation(tx, actor, {
      module: "store-transfer",
      action: "create",
      targetId: created.id,
      storeId: fromStoreId,
      description: `创建调拨 ${transferNo}，${created.fromStore.name} → ${created.toStore.name}`,
    });
    return withComputed(created, actor);
  });
  await publishTransferRobotEvent(prisma, "TRANSFER_REQUESTED", created, actor);
  return created;
}

export async function updateStoreTransfer(prisma, actor, idValue, payload) {
  const current = await getStoreTransfer(prisma, actor, idValue);
  assertCanManageTransfer(actor, current);
  if (current.outboundStatus !== OUTBOUND_STATUS.PENDING)
    throw new AppError("已确认调出的调拨不能修改", 409);
  const data = { updatedBy: Number(actor.id) };
  if (payload.fromStoreId !== undefined || payload.toStoreId !== undefined) {
    const storeIds = await resolveTransferStoreIds(prisma, actor, {
      fromStoreId: payload.fromStoreId ?? current.fromStoreId,
      toStoreId: payload.toStoreId ?? current.toStoreId,
    });
    Object.assign(data, storeIds);
  }
  if (payload.transferDate !== undefined)
    data.transferDate = dateOnly(payload.transferDate, "调拨日期");
  if (payload.expectedReturnDate !== undefined)
    data.expectedReturnDate = dateOnly(
      payload.expectedReturnDate,
      "预计归还日期",
    );
  if (payload.remark !== undefined)
    data.remark = text(payload.remark, 500, "备注");
  if (payload.items !== undefined) {
    data.items = { deleteMany: {}, create: normalizeItems(payload.items) };
  }
  const nextTransferDate = data.transferDate || current.transferDate;
  const nextExpectedDate =
    data.expectedReturnDate || current.expectedReturnDate;
  if (nextExpectedDate < nextTransferDate)
    throw new AppError("预计归还日期不能早于调拨日期", 400);
  const updated = await storeTransferRepository.update(prisma, {
    where: { id: current.id },
    data,
    include: detailInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "store-transfer",
    action: "update",
    targetId: updated.id,
    storeId: updated.fromStoreId,
    description: `修改调拨 ${updated.transferNo}`,
  });
  return withComputed(updated, actor);
}

export async function updateExpectedReturnDate(
  prisma,
  actor,
  idValue,
  payload,
) {
  const current = await getStoreTransfer(prisma, actor, idValue);
  assertCanManageTransfer(actor, current);
  if (
    [TRANSFER_STATUS.RETURNED, TRANSFER_STATUS.CANCELLED].includes(
      current.status,
    )
  )
    throw new AppError("已调平或已取消的调拨不能修改预计归还日期", 409);
  const expectedReturnDate = dateOnly(
    payload.expectedReturnDate,
    "预计归还日期",
  );
  if (expectedReturnDate < current.transferDate)
    throw new AppError("预计归还日期不能早于调拨日期", 400);
  const updated = await storeTransferRepository.update(prisma, {
    where: { id: current.id },
    data: { expectedReturnDate, updatedBy: Number(actor.id) },
    include: detailInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "store-transfer",
    action: "update-expected-return-date",
    targetId: updated.id,
    storeId: updated.fromStoreId,
    description: `修改调拨 ${updated.transferNo} 预计归还日期：${dateKey(current.expectedReturnDate)} → ${dateKey(expectedReturnDate)}`,
  });
  return withComputed(updated, actor);
}

export async function confirmStoreTransferOutbound(prisma, actor, idValue) {
  const current = await getStoreTransfer(prisma, actor, idValue);
  assertCanConfirmTransferOutbound(actor, current);
  if (
    [TRANSFER_STATUS.RETURNED, TRANSFER_STATUS.CANCELLED].includes(
      current.status,
    )
  )
    throw new AppError("已调平或已取消的调拨不能确认调出", 409);
  if (current.outboundStatus === OUTBOUND_STATUS.CONFIRMED)
    throw new AppError("该调拨已经确认调出", 409);
  const updated = await storeTransferRepository.update(prisma, {
    where: { id: current.id },
    data: {
      outboundStatus: OUTBOUND_STATUS.CONFIRMED,
      outboundConfirmedAt: new Date(),
      outboundConfirmedBy: Number(actor.id),
      updatedBy: Number(actor.id),
    },
    include: detailInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "store-transfer",
    action: "confirm-outbound",
    targetId: updated.id,
    storeId: updated.fromStoreId,
    description: `确认调拨 ${updated.transferNo} 已调出`,
  });
  const result = withComputed(updated, actor);
  await publishTransferRobotEvent(
    prisma,
    "TRANSFER_OUTBOUND_CONFIRMED",
    result,
    actor,
  );
  return result;
}

export async function submitStoreTransferReturns(
  prisma,
  actor,
  idValue,
  payload,
) {
  const entries = Array.isArray(payload.items) ? payload.items : [];
  if (!entries.length) throw new AppError("请填写归还数量", 400);
  const ids = entries.map((entry) => Number(entry.transferItemId));
  if (
    ids.some((id) => !Number.isInteger(id) || id <= 0) ||
    new Set(ids).size !== ids.length
  )
    throw new AppError("归还明细不正确", 400);
  const returnDate = dateOnly(payload.returnDate, "归还日期");
  const remark = text(payload.remark, 500, "归还备注");

  const transactionResult = await prisma.$transaction(
    async (tx) => {
      const transfer = await storeTransferRepository.findFirst(tx, {
        where: { id: Number(idValue), AND: [transferScope(actor)] },
        include: detailInclude(),
      });
      if (!transfer) throw new AppError("调拨单不存在", 404);
      assertCanSubmitTransferReturn(actor, transfer);
      if (transfer.outboundStatus !== OUTBOUND_STATUS.CONFIRMED)
        throw new AppError("调出门店尚未确认调出，不能发起归还", 409);
      if (
        [TRANSFER_STATUS.RETURNED, TRANSFER_STATUS.CANCELLED].includes(
          transfer.status,
        )
      )
        throw new AppError("已调平或已取消的调拨不能继续归还", 409);
      if (returnDate < transfer.transferDate)
        throw new AppError("归还日期不能早于调拨日期", 400);

      const itemMap = new Map(transfer.items.map((item) => [item.id, item]));
      const createdReturnIds = [];
      for (const entry of entries) {
        const item = itemMap.get(Number(entry.transferItemId));
        if (!item) throw new AppError("归还明细不属于当前调拨单", 400);
        const quantity = numberQuantity(
          entry.quantity,
          `${item.itemName}归还数量`,
        );
        const submitted = item.returns.reduce(
          (sum, record) => sum + Number(record.quantity),
          0,
        );
        const remaining =
          Math.round((Number(item.quantity) - submitted) * 1000) / 1000;
        if (quantity > remaining)
          throw new AppError(
            `${item.itemName}归还数量不能超过剩余数量 ${remaining}${item.unit}`,
            409,
          );
        const createdReturn = await storeTransferRepository.createReturn(tx, {
          data: {
            transferItemId: item.id,
            quantity,
            returnDate,
            operatorId: Number(actor.id),
            status: RETURN_STATUS.PENDING,
            remark: text(entry.remark, 500, "归还备注") || remark,
          },
        });
        createdReturnIds.push(createdReturn.id);
      }

      // Pending return applications do not affect settlement until the outbound store confirms receipt.
      const nextStatus = transfer.status;
      await storeTransferRepository.update(tx, {
        where: { id: transfer.id },
        data: { status: nextStatus, updatedBy: Number(actor.id) },
      });
      await recordOperation(tx, actor, {
        module: "store-transfer",
        action: "submit-return",
        targetId: transfer.id,
        storeId: transfer.fromStoreId,
        description: `新增调拨 ${transfer.transferNo} 归还记录，共 ${entries.length} 项`,
      });
      if (nextStatus === TRANSFER_STATUS.RETURNED) {
        await recordOperation(tx, actor, {
          module: "store-transfer",
          action: "complete",
          targetId: transfer.id,
          storeId: transfer.fromStoreId,
          description: `调拨 ${transfer.transferNo} 已全部归还并调平`,
        });
      }
      const updated = await storeTransferRepository.findFirst(tx, {
        where: { id: transfer.id },
        include: detailInclude(),
      });
      return { transfer: withComputed(updated, actor), createdReturnIds };
    },
    { isolationLevel: "Serializable" },
  );
  await publishTransferRobotEvent(
    prisma,
    "TRANSFER_RETURN_REQUESTED",
    transactionResult.transfer,
    actor,
    transactionResult.createdReturnIds.join("-"),
  );
  return transactionResult.transfer;
}

export async function updateStoreTransferReturn(
  prisma,
  actor,
  idValue,
  returnIdValue,
  payload,
) {
  const returnId = Number(returnIdValue);
  if (!Number.isInteger(returnId) || returnId <= 0)
    throw new AppError("归还记录不正确", 400);
  const returnDate = dateOnly(payload.returnDate, "归还日期");

  return prisma.$transaction(
    async (tx) => {
      const transfer = await storeTransferRepository.findFirst(tx, {
        where: { id: Number(idValue), AND: [transferScope(actor)] },
        include: detailInclude(),
      });
      if (!transfer) throw new AppError("调拨单不存在", 404);
      assertCanSubmitTransferReturn(actor, transfer);
      if (transfer.outboundStatus !== OUTBOUND_STATUS.CONFIRMED)
        throw new AppError("调出门店尚未确认调出，不能修改归还记录", 409);
      if (
        [TRANSFER_STATUS.RETURNED, TRANSFER_STATUS.CANCELLED].includes(
          transfer.status,
        )
      )
        throw new AppError("已调平或已取消的调拨不能修改归还记录", 409);
      if (returnDate < transfer.transferDate)
        throw new AppError("归还日期不能早于调拨日期", 400);

      const item = transfer.items.find((candidate) =>
        candidate.returns.some((record) => record.id === returnId),
      );
      const returnRecord = item?.returns.find(
        (record) => record.id === returnId,
      );
      if (!returnRecord) throw new AppError("归还记录不属于当前调拨单", 404);
      if (returnRecord.status !== RETURN_STATUS.PENDING)
        throw new AppError("已确认的归还记录不能修改", 409);

      const quantity = numberQuantity(payload.quantity, `${item.itemName}归还数量`);
      const otherSubmitted = item.returns
        .filter((record) => record.id !== returnId)
        .reduce((sum, record) => sum + Number(record.quantity), 0);
      const available =
        Math.round((Number(item.quantity) - otherSubmitted) * 1000) / 1000;
      if (quantity > available)
        throw new AppError(
          `${item.itemName}归还数量不能超过可归还数量 ${available}${item.unit}`,
          409,
        );

      await storeTransferRepository.updateReturn(tx, {
        where: { id: returnId },
        data: {
          quantity,
          returnDate,
          remark: text(payload.remark, 500, "归还备注"),
        },
      });
      await storeTransferRepository.update(tx, {
        where: { id: transfer.id },
        data: { updatedBy: Number(actor.id) },
      });
      await recordOperation(tx, actor, {
        module: "store-transfer",
        action: "update-return",
        targetId: transfer.id,
        storeId: transfer.fromStoreId,
        description: `修改调拨 ${transfer.transferNo} 归还记录：${item.itemName} ${quantity}${item.unit}`,
      });
      const updated = await storeTransferRepository.findFirst(tx, {
        where: { id: transfer.id },
        include: detailInclude(),
      });
      return withComputed(updated, actor);
    },
    { isolationLevel: "Serializable" },
  );
}

export async function confirmStoreTransferReturn(
  prisma,
  actor,
  idValue,
  returnIdValue,
) {
  const returnId = Number(returnIdValue);
  if (!Number.isInteger(returnId) || returnId <= 0)
    throw new AppError("归还记录不正确", 400);
  const result = await prisma.$transaction(
    async (tx) => {
      const transfer = await storeTransferRepository.findFirst(tx, {
        where: { id: Number(idValue), AND: [transferScope(actor)] },
        include: detailInclude(),
      });
      if (!transfer) throw new AppError("调拨单不存在", 404);
      assertCanConfirmTransferReturn(actor, transfer);
      if (transfer.outboundStatus !== OUTBOUND_STATUS.CONFIRMED)
        throw new AppError("调出门店尚未确认调出，不能确认归还", 409);
      if (
        [TRANSFER_STATUS.RETURNED, TRANSFER_STATUS.CANCELLED].includes(
          transfer.status,
        )
      )
        throw new AppError("已调平或已取消的调拨不能确认归还", 409);

      const item = transfer.items.find((candidate) =>
        candidate.returns.some((record) => record.id === returnId),
      );
      const returnRecord = item?.returns.find(
        (record) => record.id === returnId,
      );
      if (!returnRecord) throw new AppError("归还记录不属于当前调拨单", 404);
      if (returnRecord.status !== RETURN_STATUS.PENDING)
        throw new AppError("该归还记录已经确认", 409);

      await storeTransferRepository.updateReturn(tx, {
        where: { id: returnId },
        data: {
          status: RETURN_STATUS.CONFIRMED,
          confirmedAt: new Date(),
          confirmedBy: Number(actor.id),
        },
      });
      const computedItems = transfer.items.map((currentItem) => ({
        quantity: Number(currentItem.quantity),
        returnedQuantity:
          currentItem.returns
            .filter((record) => record.status === RETURN_STATUS.CONFIRMED)
            .reduce((sum, record) => sum + Number(record.quantity), 0) +
          (currentItem.id === item.id ? Number(returnRecord.quantity) : 0),
      }));
      const nextStatus = calculateTransferStatus(computedItems);
      await storeTransferRepository.update(tx, {
        where: { id: transfer.id },
        data: { status: nextStatus, updatedBy: Number(actor.id) },
      });
      await recordOperation(tx, actor, {
        module: "store-transfer",
        action: "confirm-return",
        targetId: transfer.id,
        storeId: transfer.fromStoreId,
        description: `确认调拨 ${transfer.transferNo} 归还收货：${item.itemName} ${returnRecord.quantity}${item.unit}`,
      });
      if (nextStatus === TRANSFER_STATUS.RETURNED) {
        await recordOperation(tx, actor, {
          module: "store-transfer",
          action: "complete",
          targetId: transfer.id,
          storeId: transfer.fromStoreId,
          description: `调拨 ${transfer.transferNo} 已全部确认收货并调平`,
        });
      }
      const updated = await storeTransferRepository.findFirst(tx, {
        where: { id: transfer.id },
        include: detailInclude(),
      });
      return withComputed(updated, actor);
    },
    { isolationLevel: "Serializable" },
  );
  await publishTransferRobotEvent(
    prisma,
    "TRANSFER_RETURN_CONFIRMED",
    result,
    actor,
    returnId,
  );
  return result;
}

export async function cancelStoreTransfer(prisma, actor, idValue, payload) {
  const current = await getStoreTransfer(prisma, actor, idValue);
  assertCanManageTransfer(actor, current);
  if (current.status === TRANSFER_STATUS.CANCELLED)
    throw new AppError("调拨单已经取消", 409);
  if (current.status === TRANSFER_STATUS.RETURNED)
    throw new AppError("已调平的调拨不能取消", 409);
  if (current.returnRecords.length)
    throw new AppError("已有归还记录的调拨不能取消", 409);
  const cancelReason = text(payload.reason, 500, "取消原因", true);
  const updated = await storeTransferRepository.update(prisma, {
    where: { id: current.id },
    data: {
      status: TRANSFER_STATUS.CANCELLED,
      cancelledAt: new Date(),
      cancelledBy: Number(actor.id),
      cancelReason,
      updatedBy: Number(actor.id),
    },
    include: detailInclude(),
  });
  await recordOperation(prisma, actor, {
    module: "store-transfer",
    action: "cancel",
    targetId: updated.id,
    storeId: updated.fromStoreId,
    description: `取消调拨 ${updated.transferNo}：${cancelReason}`,
  });
  const result = withComputed(updated, actor);
  await publishTransferRobotEvent(prisma, "TRANSFER_CANCELLED", result, actor);
  return result;
}
