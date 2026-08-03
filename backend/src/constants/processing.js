export const DICTIONARY_TYPES = Object.freeze({
  PRESCRIPTION_SOURCE: "PrescriptionSource",
  PROCESS_TYPE: "ProcessType",
  NOTIFY_TYPE: "NotifyType",
});

export const SCHEDULE_TYPES = Object.freeze({ DATE: 1, NOTICE: 2 });
export const PROCESS_TYPE_CODES = Object.freeze({ DECOCTION: "DECOCTION" });

export const PLAN_STATUS = Object.freeze({
  WAITING: 0,
  PROCESSING: 1,
  FINISHED: 2,
  READY_PICKUP: 3,
  PICKED: 4,
  CANCELLED: 5,
});

export const PRIORITY = Object.freeze({ NORMAL: 0, URGENT: 1 });
export const NOTIFY_STATUS = Object.freeze({ PENDING: 0, NOTIFIED: 1 });
export const NOTIFY_TYPE = Object.freeze({ NONE: "NONE" });
export const PAYMENT_STATUS = Object.freeze({ UNPAID: 0, PAID: 1 });

export const PLAN_TRANSITIONS = Object.freeze({
  [PLAN_STATUS.WAITING]: [PLAN_STATUS.PROCESSING, PLAN_STATUS.CANCELLED],
  [PLAN_STATUS.PROCESSING]: [PLAN_STATUS.FINISHED, PLAN_STATUS.CANCELLED],
  [PLAN_STATUS.FINISHED]: [PLAN_STATUS.READY_PICKUP],
  [PLAN_STATUS.READY_PICKUP]: [PLAN_STATUS.PICKED],
  [PLAN_STATUS.PICKED]: [],
  [PLAN_STATUS.CANCELLED]: [],
});

export const PRESCRIPTION_STATUS = Object.freeze({
  ACTIVE: 0,
  COMPLETED: 1,
  CANCELLED: 2,
});

export function canTransition(from, to) {
  return PLAN_TRANSITIONS[from]?.includes(to) === true;
}
