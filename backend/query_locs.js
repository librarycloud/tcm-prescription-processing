const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();
async function main() {
  const locs = await prisma.e6PharmacyLocation.findMany({
    orderBy: { code: 'asc' }
  });
  console.log(JSON.stringify(locs, null, 2));
}
main().finally(() => prisma.$disconnect());
