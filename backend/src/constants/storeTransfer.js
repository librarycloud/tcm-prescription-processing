export const TRANSFER_STATUS = Object.freeze({
  BORROWING: 0,
  PART_RETURNED: 1,
  RETURNED: 2,
  CANCELLED: 3,
});

export const TRANSFER_OUTBOUND_STATUS = Object.freeze({
  PENDING: 0,
  CONFIRMED: 1,
});

export const TRANSFER_RETURN_STATUS = Object.freeze({
  PENDING: 0,
  CONFIRMED: 1,
});

export const ACTIVE_TRANSFER_STATUSES = Object.freeze([
  TRANSFER_STATUS.BORROWING,
  TRANSFER_STATUS.PART_RETURNED,
]);

export const TRANSFER_STATUS_VALUES = Object.freeze(
  Object.values(TRANSFER_STATUS),
);
