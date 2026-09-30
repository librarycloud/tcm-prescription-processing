import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();
async function main() {
  const configs = await prisma.systemConfig.findMany();
  console.log(configs.filter(c => c.item.includes('captcha')));
}
main().catch(console.error).finally(() => prisma.$disconnect());
