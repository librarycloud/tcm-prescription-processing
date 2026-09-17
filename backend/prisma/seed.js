import bcrypt from 'bcrypt';
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  const password = await bcrypt.hash('123456', 10);

  await Promise.all([
    prisma.store.upsert({
      where: { code: 'HQ' },
      update: { name: '总部', status: 1 },
      create: { name: '总部', code: 'HQ', status: 1 }
    }),
    prisma.store.upsert({
      where: { code: 'SUZHOU' },
      update: { name: '苏州店', status: 1 },
      create: { name: '苏州店', code: 'SUZHOU', status: 1 }
    }),
    prisma.store.upsert({
      where: { code: 'SHANGHAI' },
      update: { name: '上海店', status: 1 },
      create: { name: '上海店', code: 'SHANGHAI', status: 1 }
    })
  ]);

  await prisma.user.upsert({
    where: { phone: '13800000000' },
    update: {
      username: null,
      password,
      role: 0,
      storeId: null,
      status: 1,
      nickname: '默认管理员'
    },
    create: {
      username: null,
      password,
      phone: '13800000000',
      role: 0,
      storeId: null,
      status: 1,
      nickname: '默认管理员'
    }
  });

  console.log('Seed completed. Admin phone: 13800000000 password: 123456');
}

main()
  .catch((error) => {
    console.error(error);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
