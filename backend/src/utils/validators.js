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

export function normalizeOptionalPhone(phone, fieldName = '手机号') {
  const normalized = String(phone ?? '').trim();
  if (!normalized) return null;
  validatePhone(normalized, fieldName);
  return normalized;
}

export function toPositiveInt(value, defaultValue = 1) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed) || parsed <= 0) return defaultValue;
  return parsed;
}
