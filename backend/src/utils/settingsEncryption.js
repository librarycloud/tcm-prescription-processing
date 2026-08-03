import { createCipheriv, createDecipheriv, createHash, randomBytes } from 'node:crypto';
import { config } from '../config.js';
import { AppError } from './appError.js';

function getKey() {
  if (!config.settingsEncryptionKey) {
    throw new AppError('服务器未配置 SETTINGS_ENCRYPTION_KEY', 500);
  }
  return createHash('sha256').update(config.settingsEncryptionKey).digest();
}

export function encryptSetting(value) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', getKey(), iv);
  const encrypted = Buffer.concat([cipher.update(String(value), 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return ['v1', iv.toString('base64'), tag.toString('base64'), encrypted.toString('base64')].join('.');
}

export function decryptSetting(value) {
  const [version, ivValue, tagValue, encryptedValue] = String(value || '').split('.');
  if (version !== 'v1' || !ivValue || !tagValue || !encryptedValue) {
    throw new AppError('加密配置数据格式不正确', 500);
  }
  try {
    const decipher = createDecipheriv('aes-256-gcm', getKey(), Buffer.from(ivValue, 'base64'));
    decipher.setAuthTag(Buffer.from(tagValue, 'base64'));
    return Buffer.concat([
      decipher.update(Buffer.from(encryptedValue, 'base64')),
      decipher.final()
    ]).toString('utf8');
  } catch {
    throw new AppError('加密配置解密失败，请检查 SETTINGS_ENCRYPTION_KEY', 500);
  }
}
