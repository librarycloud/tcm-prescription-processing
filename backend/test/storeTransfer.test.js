import assert from "node:assert/strict";
import test from "node:test";
import {
  TRANSFER_OUTBOUND_STATUS,
  TRANSFER_RETURN_STATUS,
  TRANSFER_STATUS,
} from "../src/constants/storeTransfer.js";
import {
  assertCanManageTransfer,
  assertCanConfirmTransferReturn,
  assertCanConfirmTransferOutbound,
  assertCanSubmitTransferReturn,
  transferScope,
} from "../src/services/permissionService.js";
import {
  calculateTransferStatus,
  updateStoreTransferReturn,
} from "../src/services/storeTransferService.js";

function transferPrisma(returnStatus = TRANSFER_RETURN_STATUS.PENDING) {
  const state = {
    returnRecord: {
      id: 31,
      transferItemId: 21,
      quantity: 4,
      returnDate: new Date("2026-08-10T00:00:00.000Z"),
      operatorId: 8,
      status: returnStatus,
      remark: "原备注",
      createdAt: new Date("2026-08-10T01:00:00.000Z"),
      operator: { id: 8, name: "调入管理员" },
      confirmer: null,
    },
  };
  const transfer = () => ({
    id: 11,
    transferNo: "DB202608100001",
    fromStoreId: 1,
    toStoreId: 2,
    transferDate: new Date("2026-08-01T00:00:00.000Z"),
    expectedReturnDate: new Date("2026-08-20T00:00:00.000Z"),
    status: TRANSFER_STATUS.BORROWING,
    outboundStatus: TRANSFER_OUTBOUND_STATUS.CONFIRMED,
    fromStore: { id: 1, name: "调出门店" },
    toStore: { id: 2, name: "调入门店" },
    creator: { id: 8, name: "调入管理员" },
    items: [
      {
        id: 21,
        itemName: "测试物品",
        quantity: 10,
        unit: "盒",
        returns: [
          state.returnRecord,
          {
            id: 32,
            transferItemId: 21,
            quantity: 3,
            status: TRANSFER_RETURN_STATUS.PENDING,
            createdAt: new Date("2026-08-09T01:00:00.000Z"),
          },
          {
            id: 33,
            transferItemId: 21,
            quantity: 2,
            status: TRANSFER_RETURN_STATUS.CONFIRMED,
            createdAt: new Date("2026-08-08T01:00:00.000Z"),
          },
        ],
      },
    ],
  });
  const prisma = {
    $transaction: async (callback) => callback(prisma),
    storeTransfer: {
      findFirst: async () => transfer(),
      update: async () => transfer(),
    },
    storeTransferReturn: {
      update: async ({ data }) => {
        Object.assign(state.returnRecord, data);
        return state.returnRecord;
      },
    },
    operationLog: { create: async ({ data }) => data },
  };
  return { prisma, state };
}

test("store transfer status is derived from immutable return history totals", () => {
  assert.equal(
    calculateTransferStatus([{ quantity: 100, returnedQuantity: 0 }]),
    TRANSFER_STATUS.BORROWING,
  );
  assert.equal(
    calculateTransferStatus([{ quantity: 100, returnedQuantity: 40 }]),
    TRANSFER_STATUS.PART_RETURNED,
  );
  assert.equal(
    calculateTransferStatus([
      { quantity: 100, returnedQuantity: 100 },
      { quantity: 20, returnedQuantity: 20 },
    ]),
    TRANSFER_STATUS.RETURNED,
  );
});

test("store admins can only see transfers involving their own store", () => {
  assert.deepEqual(transferScope({ role: 2, storeId: 8 }), {
    OR: [{ fromStoreId: 8 }, { toStoreId: 8 }],
  });
  assert.deepEqual(transferScope({ role: 0 }), {});
});

test("inbound store requests, outbound store confirms dispatch and receipt", () => {
  const transfer = {
    fromStoreId: 1,
    toStoreId: 2,
    outboundStatus: TRANSFER_OUTBOUND_STATUS.CONFIRMED,
  };
  assert.doesNotThrow(() =>
    assertCanManageTransfer({ role: 2, storeId: 1 }, transfer),
  );
  assert.throws(
    () => assertCanManageTransfer({ role: 2, storeId: 2 }, transfer),
    /责任门店/,
  );
  assert.doesNotThrow(() =>
    assertCanSubmitTransferReturn({ role: 2, storeId: 2 }, transfer),
  );
  assert.throws(
    () => assertCanSubmitTransferReturn({ role: 2, storeId: 1 }, transfer),
    /调入门店/,
  );
  assert.doesNotThrow(() =>
    assertCanConfirmTransferReturn({ role: 2, storeId: 1 }, transfer),
  );
  assert.throws(
    () => assertCanConfirmTransferReturn({ role: 2, storeId: 2 }, transfer),
    /调出门店/,
  );
  assert.doesNotThrow(() => assertCanManageTransfer({ role: 0 }, transfer));
  assert.doesNotThrow(() =>
    assertCanSubmitTransferReturn({ role: 0 }, transfer),
  );
  assert.doesNotThrow(() =>
    assertCanConfirmTransferReturn({ role: 0 }, transfer),
  );
  const pendingTransfer = {
    fromStoreId: 1,
    toStoreId: 2,
    outboundStatus: TRANSFER_OUTBOUND_STATUS.PENDING,
  };
  assert.doesNotThrow(() =>
    assertCanConfirmTransferOutbound({ role: 2, storeId: 1 }, pendingTransfer),
  );
  assert.throws(
    () =>
      assertCanConfirmTransferOutbound(
        { role: 2, storeId: 2 },
        pendingTransfer,
      ),
    /调出门店/,
  );
  assert.doesNotThrow(() =>
    assertCanManageTransfer({ role: 2, storeId: 2 }, pendingTransfer),
  );
  assert.throws(
    () => assertCanManageTransfer({ role: 2, storeId: 1 }, pendingTransfer),
    /责任门店/,
  );
});

test("inbound store can update its pending return record without double-counting it", async () => {
  const { prisma, state } = transferPrisma();
  await updateStoreTransferReturn(
    prisma,
    { id: 8, role: 2, storeId: 2 },
    11,
    31,
    { quantity: 5, returnDate: "2026-08-12", remark: "修改后" },
  );
  assert.equal(state.returnRecord.quantity, 5);
  assert.equal(state.returnRecord.remark, "修改后");
  assert.equal(state.returnRecord.returnDate.toISOString().slice(0, 10), "2026-08-12");
});

test("updating a pending return rejects quantities beyond the true availability", async () => {
  const { prisma } = transferPrisma();
  await assert.rejects(
    updateStoreTransferReturn(
      prisma,
      { id: 8, role: 2, storeId: 2 },
      11,
      31,
      { quantity: 5.001, returnDate: "2026-08-12" },
    ),
    /不能超过可归还数量 5盒/,
  );
});

test("confirmed return records cannot be updated", async () => {
  const { prisma } = transferPrisma(TRANSFER_RETURN_STATUS.CONFIRMED);
  await assert.rejects(
    updateStoreTransferReturn(
      prisma,
      { id: 8, role: 2, storeId: 2 },
      11,
      31,
      { quantity: 4, returnDate: "2026-08-12" },
    ),
    /已确认的归还记录不能修改/,
  );
});
