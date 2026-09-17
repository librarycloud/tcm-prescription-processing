export function formatDate(value) {
  if (!value) return '-';
  const date = new Date(value);
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function maskPhone(phone) {
  const value = String(phone || '');
  if (value.length !== 11) return value || '-';
  return `${value.slice(0, 3)}****${value.slice(7)}`;
}

export function statusText(status) {
  return Number(status) === 1 ? '已取' : '待取';
}

export function statusTheme(status) {
  return Number(status) === 1 ? 'success' : 'warning';
}

export const PICKUP_METHOD_OPTIONS = [
  { label: '自提', value: 0 },
  { label: '跑腿', value: 1 },
  { label: '快递', value: 2 }
];

export function pickupMethodText(method, fallback = '未选择') {
  if (method === undefined || method === null || method === '') return fallback;
  const option = PICKUP_METHOD_OPTIONS.find((item) => item.value === Number(method));
  return option ? option.label : fallback;
}

export function normalizeExpressTrackingNo(value) {
  const text = String(value || '').trim();
  const sfMatch = text.match(/SF\s*[-:]?\s*(\d{10,16})/i);
  if (sfMatch) return `SF${sfMatch[1]}`;
  const compact = text.replace(/\s+/g, '');
  const trackingMatch = compact.match(/[A-Z]{1,4}\d{8,20}/i);
  return (trackingMatch ? trackingMatch[0] : compact).toUpperCase().slice(0, 100);
}

export function normalizePickupCode(value) {
  return String(value || '').replace(/\D/g, '').slice(0, 6);
}

export function formatPickupCode(value) {
  const digits = normalizePickupCode(value);
  if (digits.length <= 3) return digits;
  return `${digits.slice(0, 3)}-${digits.slice(3)}`;
}
