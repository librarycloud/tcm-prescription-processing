import dotenv from 'dotenv';
import path from 'node:path';

dotenv.config();

const nodeEnv = process.env.NODE_ENV || 'development';
const developmentJwtSecret = 'dev-secret-change-me';

function resolveJwtSecret() {
  const secret = String(process.env.JWT_SECRET || '').trim();
  if (nodeEnv === 'production' && (!secret || secret === developmentJwtSecret)) {
    throw new Error('JWT_SECRET must be configured with a non-default value in production');
  }
  return secret || developmentJwtSecret;
}

function parseTrustProxy(value) {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return String(value || '127.0.0.1,::1')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

const jwtSecret = resolveJwtSecret();

export const config = {
  port: Number(process.env.PORT || 3000),
  host: process.env.HOST || '0.0.0.0',
  jwtSecret,
  pickupQrSecret: String(process.env.PICKUP_QR_SECRET || '').trim() || jwtSecret,
  wxAppId: process.env.WX_APPID || '',
  wxSecret: process.env.WX_SECRET || '',
  trustProxy: parseTrustProxy(process.env.TRUST_PROXY),
  ipDatabasePath: process.env.IPDB_PATH || '',
  uploadDir: path.resolve(process.cwd(), process.env.UPLOAD_DIR || 'uploads'),
  settingsEncryptionKey: process.env.SETTINGS_ENCRYPTION_KEY || '',
  redisUrl: process.env.REDIS_URL || '',
  githubRepository: process.env.GITHUB_REPOSITORY || '',
  githubToken: process.env.GITHUB_TOKEN || '',
  appDownloadBaseUrl: (process.env.APP_DOWNLOAD_BASE_URL || '').trim().replace(/\/+$/, ''),
  nodeEnv
};
