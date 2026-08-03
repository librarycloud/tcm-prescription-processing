import fp from 'fastify-plugin';
import { PrismaClient } from '@prisma/client';

async function prismaPlugin(fastify) {
  const prisma = new PrismaClient({
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
