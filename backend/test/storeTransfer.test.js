import assert from "node:assert/strict";
import test from "node:test";
import {
  TRANSFER_OUTBOUND_STATUS,
  TRANSFER_STATUS,
} from "../src/constants/storeTransfer.js";
import {
  assertCanManageTransfer,
  assertCanConfirmTransferReturn,
  assertCanConfirmTransferOutbound,
  assertCanSubmitTransferReturn,
  transferScope,
} from "../src/services/permissionService.js";
import { calculateTransferStatus } from "../src/services/storeTransferService.js";

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
