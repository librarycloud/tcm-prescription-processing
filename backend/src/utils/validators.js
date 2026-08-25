import { AppError } from './appError.js';

export function required(value, fieldName) {
  if (value === undefined || value === null || String(value).trim() === '') {
    throw new AppError(`${fieldName}不能为空`, 400);
  }
}

export function validatePhone(phone, fieldName = '手机号') {
  required(phone, fieldName);
  if (!/^1[3-9]\d{9}$/.test(String(phone))) {
    throw new AppError(`${fieldName}格式不正确`, 400);
  }
}

export function validateUsername(username, fieldName = '用户名', { allowLegacyPhone = false } = {}) {
  required(username, fieldName);
  const value = String(username).trim();
  if (value.length < 2 || value.length > 64) {
    throw new AppError(`${fieldName}长度必须为 2-64 位`, 400);
  }
  if (!/^[A-Za-z0-9]+$/.test(value)) {
    throw new AppError(`${fieldName}只能包含英文和数字`, 400);
  }
  if (!/[A-Za-z]/.test(value) && !allowLegacyPhone) {
    throw new AppError(`${fieldName}不能是纯数字`, 400);
  }
  return value;
}

export function normalizeOptionalUsername(username, fieldName = '用户名') {
  const value = String(username ?? '').trim();
  if (!value) return null;
  return validateUsername(value, fieldName);
}

export function normalizeOptionalPhone(phone, fieldName = '手机号') {
  const normalized = String(phone ?? '').trim();
  if (!normalized) return null;
  validatePhone(normalized, fieldName);
  return normalized;
}

const EMAIL_LOCAL_ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.!#$%&'*+-/=?^_`{|}~";
const EMAIL_DOMAIN_ALLOWED = 'abcdefghijklmnopqrstuvwxyz0123456789-';

export function isValidEmail(value) {
  const email = String(value ?? '').trim().toLowerCase();
  if (!email || email.length > 254) return false;

  const atIndex = email.indexOf('@');
  if (atIndex <= 0 || atIndex !== email.lastIndexOf('@') || atIndex === email.length - 1) {
    return false;
  }

  const local = email.slice(0, atIndex);
  const domain = email.slice(atIndex + 1);
  if (local.length > 64 || local.startsWith('.') || local.endsWith('.') || local.includes('..')) {
    return false;
  }
  for (const character of local) {
    if (!EMAIL_LOCAL_ALLOWED.includes(character)) return false;
  }

  const labels = domain.split('.');
  if (labels.length < 2 || labels.some((label) => !label || label.length > 63)) return false;
  for (const label of labels) {
    if (label.startsWith('-') || label.endsWith('-')) return false;
    for (const character of label) {
      if (!EMAIL_DOMAIN_ALLOWED.includes(character)) return false;
    }
  }
  return true;
}

export function toPositiveInt(value, defaultValue = 1) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed) || parsed <= 0) return defaultValue;
  return parsed;
}
