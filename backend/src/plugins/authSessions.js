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

function unavailable(error) {
  return new AppError('登录会话服务暂不可用，请稍后重试', 503);
}

function createSessionStore(redis) {
  return {
    async create({ accountType, accountId, jti }) {
      const sessionsKey = accountKey(accountType, accountId);
      const expiresAt = Math.floor(Date.now() / 1000) + SESSION_TTL_SECONDS;
      try {
        await redis
          .multi()
          .zRemRangeByScore(sessionsKey, 0, Math.floor(Date.now() / 1000))
          .set(sessionKey(accountType, accountId, jti), '1', {
            expiration: { type: 'EX', value: SESSION_TTL_SECONDS }
          })
          .zAdd(sessionsKey, { score: expiresAt, value: jti })
          .expire(sessionsKey, SESSION_TTL_SECONDS)
          .exec();
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
