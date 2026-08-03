import dotenv from 'dotenv';

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

export const config = {
  port: Number(process.env.PORT || 3000),
  host: process.env.HOST || '0.0.0.0',
  jwtSecret: resolveJwtSecret(),
  wxAppId: process.env.WX_APPID || '',
  wxSecret: process.env.WX_SECRET || '',
  trustProxy: parseTrustProxy(process.env.TRUST_PROXY),
  ipDatabasePath: process.env.IPDB_PATH || '',
  settingsEncryptionKey: process.env.SETTINGS_ENCRYPTION_KEY || '',
  nodeEnv
};
