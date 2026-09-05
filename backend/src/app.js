import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import jwt from '@fastify/jwt';
import rateLimit from '@fastify/rate-limit';
import multipart from '@fastify/multipart';
import prismaPlugin from './plugins/prisma.js';
import authSessionsPlugin from './plugins/authSessions.js';
import { config } from './config.js';
import { AppError } from './utils/appError.js';
import { fail, ok } from './utils/response.js';
import authRoutes from './routes/authRoutes.js';
import adminRoutes from './routes/adminRoutes.js';
import userRoutes from './routes/userRoutes.js';
import storeRoutes from './routes/storeRoutes.js';
import e6IntegrationRoutes from './routes/e6IntegrationRoutes.js';
import appRoutes from './routes/appRoutes.js';
import { initializeIpLookup } from './utils/ipLookup.js';
import { startRobotDeliveryWorker } from './services/robotNotificationService.js';

export async function buildApp() {
  const fastify = Fastify({
    trustProxy: config.trustProxy,
    logger: {
      level: config.nodeEnv === 'production' ? 'info' : 'debug',
      transport: config.nodeEnv === 'production' ? undefined : { target: 'pino-pretty' }
    }
  });

  await fastify.register(cors, { origin: true });
  await fastify.register(helmet);
  await fastify.register(rateLimit, {
    global: false,
    max: 200,
    timeWindow: '1 minute'
  });
  fastify.addHook('onRequest', fastify.rateLimit());
  await fastify.register(multipart, { limits: { fileSize: 5 * 1024 * 1024, files: 1 } });
  await fastify.register(jwt, { secret: config.jwtSecret });
  await fastify.register(prismaPlugin);
  await fastify.register(authSessionsPlugin);
  const ipLookup = initializeIpLookup(config.ipDatabasePath);
  fastify.decorate('ipLookup', ipLookup);
  fastify.log.info(
    { path: ipLookup.databasePath, bytes: ipLookup.databaseSize },
    'QQWry IP database loaded into memory'
  );

  fastify.get('/health', async (_request, reply) => ok(reply, { status: 'ok' }));
  await fastify.register(appRoutes, { prefix: '/app' });
  await fastify.register(authRoutes, { prefix: '/auth' });
  await fastify.register(adminRoutes, { prefix: '/admin' });
  await fastify.register(userRoutes, { prefix: '/user' });
  await fastify.register(storeRoutes);
  await fastify.register(e6IntegrationRoutes, { prefix: '/integrations/e6/v1' });
  startRobotDeliveryWorker(fastify);

  fastify.setErrorHandler((error, request, reply) => {
    request.log.error(error);
    if (error instanceof AppError) {
      return fail(reply, error.message, error.statusCode, error.data);
    }
    if (error.validation) {
      return fail(reply, '请求参数错误', 400);
    }
    return fail(reply, '服务器错误', 500);
  });

  fastify.setNotFoundHandler((_request, reply) => {
    return fail(reply, '接口不存在', 404);
  });

  return fastify;
}

const app = await buildApp();

try {
  await app.listen({ port: config.port, host: config.host });
} catch (error) {
  app.log.error(error);
  process.exit(1);
}
