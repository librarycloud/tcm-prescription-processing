import fp from 'fastify-plugin';
import { createClient } from 'redis';
import { config } from '../config.js';
import { AppError } from '../utils/appError.js';

const SESSION_TTL_SECONDS = 7 * 24 * 60 * 60;

function accountKey(accountType, accountId) {
  return `auth:sessions:${accountType}:${accountId}`;
}

function sessionKey(accountType, accountId, jti) {
  return `auth:session:${accountType}:${accountId}:${jti}`;
}

function metaKey(accountType, accountId, jti) {
  return `auth:session-meta:${accountType}:${accountId}:${jti}`;
}

function unavailable(error) {
  return new AppError('登录会话服务暂不可用，请稍后重试', 503);
}

function createSessionStore(redis) {
  return {
    async create({ accountType, accountId, jti, metadata = {} }) {
      const sessionsKey = accountKey(accountType, accountId);
      const expiresAt = Math.floor(Date.now() / 1000) + SESSION_TTL_SECONDS;
      try {
        const pipeline = redis.multi()
          .zRemRangeByScore(sessionsKey, 0, Math.floor(Date.now() / 1000))
          .set(sessionKey(accountType, accountId, jti), '1', {
            expiration: { type: 'EX', value: SESSION_TTL_SECONDS }
          })
          .zAdd(sessionsKey, { score: expiresAt, value: jti })
          .expire(sessionsKey, SESSION_TTL_SECONDS);

        if (Object.keys(metadata).length > 0) {
          const metaKeyStr = metaKey(accountType, accountId, jti);
          pipeline.hSet(metaKeyStr, {
            deviceName: metadata.deviceName || '',
            ip: metadata.ip || '',
            loginAt: String(metadata.loginAt || Date.now()),
            lastActiveAt: String(Date.now())
          });
          pipeline.expire(metaKeyStr, SESSION_TTL_SECONDS);
        }
        await pipeline.exec();
      } catch (error) {
        throw unavailable(error);
      }
    },

    async touch({ accountType, accountId, jti }) {
      try {
        const metaKeyStr = metaKey(accountType, accountId, jti);
        if (await redis.exists(metaKeyStr)) {
          // Do this without throwing on failure so it's best-effort and fast
          redis.hSet(metaKeyStr, 'lastActiveAt', String(Date.now())).catch(() => {});
        }
      } catch (error) {}
    },

    async list({ accountType, accountId }) {
      const sessionsKey = accountKey(accountType, accountId);
      try {
        const jtisWithScores = await redis.zRangeWithScores(sessionsKey, 0, -1);
        const now = Math.floor(Date.now() / 1000);
        const result = [];
        for (const { value: jti, score: expiresAt } of jtisWithScores) {
          if (expiresAt < now) continue;
          
          const isActive = await redis.exists(sessionKey(accountType, accountId, jti));
          if (!isActive) continue;

          const meta = await redis.hGetAll(metaKey(accountType, accountId, jti));
          result.push({
            jti,
            expiresAt,
            deviceName: meta?.deviceName || '未知设备',
            ip: meta?.ip || '未知IP',
            loginAt: Number(meta?.loginAt) || 0,
            lastActiveAt: Number(meta?.lastActiveAt) || Number(meta?.loginAt) || 0
          });
        }
        return result.sort((a, b) => b.loginAt - a.loginAt);
      } catch (error) {
        throw unavailable(error);
      }
    },

    async has({ accountType, accountId, jti }) {
      try {
        return Boolean(await redis.exists(sessionKey(accountType, accountId, jti)));
      } catch (error) {
        throw unavailable(error);
      }
    },

    async revoke({ accountType, accountId, jti }) {
      const sessionsKey = accountKey(accountType, accountId);
      try {
        await redis
          .multi()
          .del(sessionKey(accountType, accountId, jti))
          .del(metaKey(accountType, accountId, jti))
          .zRem(sessionsKey, jti)
          .exec();
      } catch (error) {
        throw unavailable(error);
      }
    },

    async revokeAccount({ accountType, accountId }) {
      const sessionsKey = accountKey(accountType, accountId);
      try {
        const sessionIds = await redis.zRange(sessionsKey, 0, -1);
        const pipeline = redis.multi();
        for (const jti of sessionIds) {
          pipeline.del(sessionKey(accountType, accountId, jti));
          pipeline.del(metaKey(accountType, accountId, jti));
        }
        pipeline.del(sessionsKey);
        await pipeline.exec();
      } catch (error) {
        throw unavailable(error);
      }
    }
  };
}

async function authSessionsPlugin(fastify) {
  if (!config.redisUrl) {
    throw new Error('REDIS_URL 未配置，无法启用登录会话校验');
  }

  const redis = createClient({
    url: config.redisUrl,
    socket: { connectTimeout: 5000, reconnectStrategy: false }
  });
  redis.on('error', (error) => fastify.log.error({ error }, 'Redis connection error'));
  await redis.connect();

  fastify.decorate('authSessions', createSessionStore(redis));
  fastify.addHook('onClose', async () => {
    if (redis.isOpen) await redis.quit();
  });
}

export default fp(authSessionsPlugin);
