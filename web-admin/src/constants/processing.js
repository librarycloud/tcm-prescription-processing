export const PROCESSING_STATUS = Object.freeze({
  WAITING: 0,
  PROCESSING: 1,
  FINISHED: 2,
  READY_PICKUP: 3,
  PICKED: 4,
  CANCELLED: 5
});

export const SCHEDULE_TYPES = Object.freeze({ DATE: 1, NOTICE: 2 });
export const PRIORITY = Object.freeze({ NORMAL: 0, URGENT: 1 });
export const PROCESS_TYPE_CODES = Object.freeze({ DECOCTION: 'DECOCTION' });
export const PAYMENT_STATUS = Object.freeze({ UNPAID: 0, PAID: 1 });
export const NOTIFY_STATUS = Object.freeze({ PENDING: 0, NOTIFIED: 1 });

export const PROCESSING_STATUS_OPTIONS = Object.freeze([
  { value: PROCESSING_STATUS.WAITING, label: '待加工' },
  { value: PROCESSING_STATUS.PROCESSING, label: '加工中' },
  { value: PROCESSING_STATUS.FINISHED, label: '加工完成' },
  { value: PROCESSING_STATUS.READY_PICKUP, label: '待领取' },
  { value: PROCESSING_STATUS.PICKED, label: '已领取' },
  { value: PROCESSING_STATUS.CANCELLED, label: '已取消' }
]);

export const PROCESSING_STATUS_TAG = Object.freeze({
  [PROCESSING_STATUS.WAITING]: 'info',
  [PROCESSING_STATUS.PROCESSING]: 'primary',
  [PROCESSING_STATUS.FINISHED]: 'success',
  [PROCESSING_STATUS.READY_PICKUP]: 'warning',
  [PROCESSING_STATUS.PICKED]: 'success',
  [PROCESSING_STATUS.CANCELLED]: 'danger'
});
