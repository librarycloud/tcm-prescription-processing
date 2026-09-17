export const PACKAGE_STATUS = {
  PENDING: 0,
  PICKED: 1
};

export const PICKUP_METHOD_OPTIONS = [
  { label: '自提', value: 0 },
  { label: '跑腿', value: 1 },
  { label: '快递', value: 2 }
];

export function pickupMethodText(method, fallback = '未选择') {
  if (method === undefined || method === null || method === '') return fallback;
  return PICKUP_METHOD_OPTIONS.find((item) => item.value === Number(method))?.label || fallback;
}

export function pickupMethodTagType(method) {
  if (method === undefined || method === null || method === '') return 'info';
  const types = { 0: 'primary', 1: 'warning', 2: 'success' };
  return types[Number(method)] || 'info';
}

export function normalizeStatus(status) {
  return Number(status) === PACKAGE_STATUS.PICKED ? PACKAGE_STATUS.PICKED : PACKAGE_STATUS.PENDING;
}

export function statusText(status) {
  return normalizeStatus(status) === PACKAGE_STATUS.PICKED ? '已取' : '待取';
}

export function statusTagType(status) {
  return normalizeStatus(status) === PACKAGE_STATUS.PICKED ? 'success' : 'warning';
}

export function isPicked(status) {
  return normalizeStatus(status) === PACKAGE_STATUS.PICKED;
}

export function formatPickupCode(value) {
  const digits = normalizePickupCode(value);
  if (digits.length <= 3) return digits;
  return `${digits.slice(0, 3)}-${digits.slice(3)}`;
}

export function normalizePickupCode(value) {
  return String(value || '').replace(/\D/g, '').slice(0, 6);
}
