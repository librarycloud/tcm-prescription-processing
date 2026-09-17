import { PICKUP_METHOD_NAMES, TEMPLATE_SOURCES } from '../constants/notification.js';
import { AppError } from '../utils/appError.js';
import { formatPickupCode } from '../utils/format.js';

export function parseVariableMapping(value) {
  if (Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(value || '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function validateVariableMapping(value) {
  if (!Array.isArray(value) || value.length === 0 || value.length > 10) {
    throw new AppError('模板参数必须包含 1-10 项', 400);
  }
  return value.map((item) => {
    const key = String(item?.key || '').trim();
    const source = String(item?.source || '').trim();
    if (!/^[A-Za-z][A-Za-z0-9_]{0,31}$/.test(key)) {
      throw new AppError('模板参数名格式不正确', 400);
    }
    if (!TEMPLATE_SOURCES.includes(source)) {
      throw new AppError('模板参数来源不正确', 400);
    }
    return { key, source };
  });
}

function formatDate(value) {
  if (!value) return '';
  return new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

export function buildBusinessValues(packageData) {
  return {
    receiverName: String(packageData.receiverName || ''),
    receiverPhone: String(packageData.receiverPhone || ''),
    pickupCode: formatPickupCode(packageData.pickupCode),
    itemName: String(packageData.itemName || ''),
    itemInfo: String(packageData.itemInfo || ''),
    pickupMethod: PICKUP_METHOD_NAMES[Number(packageData.pickupMethod)] || '',
    storeName: String(packageData.store?.name || ''),
    storeAddress: String(packageData.store?.address || ''),
    storePhone: String(packageData.store?.phone || ''),
    expressTrackingNo: String(packageData.expressTrackingNo || ''),
    expressAddress: String(packageData.expressAddress || ''),
    createdAt: formatDate(packageData.createdAt)
  };
}

export function resolveTemplate(template, packageData) {
  const mapping = parseVariableMapping(template.variableMapping);
  const businessValues = buildBusinessValues(packageData);
  const values = mapping.map((item) => businessValues[item.source] || '');
  const keyedValues = Object.fromEntries(
    mapping.map((item) => [item.key, businessValues[item.source] || ''])
  );
  const preview = String(template.contentPreview || '').replace(
    /\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}/g,
    (_matched, source) => businessValues[source] ?? ''
  );
  return { mapping, businessValues, values, keyedValues, preview };
}
