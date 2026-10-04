import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();
async function main() {
  const configs = await prisma.robotEventConfig.findMany({
    where: { robot: { platform: 'app_push' } },
    include: { robot: true }
  });
  console.log(JSON.stringify(configs, null, 2));
}
main().catch(console.error).finally(() => prisma.$disconnect());
