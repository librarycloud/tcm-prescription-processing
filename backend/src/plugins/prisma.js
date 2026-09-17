import fp from 'fastify-plugin';
import { PrismaMariaDb } from '@prisma/adapter-mariadb';
import { PrismaClient } from '@prisma/client';

async function prismaPlugin(fastify) {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    throw new Error('DATABASE_URL 未配置，无法连接数据库');
  }

  const adapter = new PrismaMariaDb(databaseUrl);
  const prisma = new PrismaClient({
    adapter,
    log: ['error', 'warn']
  });

  const requiredDelegates = ['product', 'productsDiffLog'];
  const missingDelegates = requiredDelegates.filter(
    (name) => typeof prisma[name]?.findMany !== 'function'
  );
  if (missingDelegates.length) {
    throw new Error(
      `Prisma Client 未包含最新数据模型（${missingDelegates.join('、')}），请运行 npm run prisma:generate 后重启后端`
    );
  }

  await prisma.$connect();
  fastify.decorate('prisma', prisma);

  fastify.addHook('onClose', async () => {
    await prisma.$disconnect();
  });
}

export default fp(prismaPlugin);
