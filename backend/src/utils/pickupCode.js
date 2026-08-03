import { randomInt } from 'node:crypto';

export function generatePickupCode() {
  return String(randomInt(0, 1_000_000)).padStart(6, '0');
}

export async function generateUniquePickupCode(prisma) {
  for (let i = 0; i < 30; i += 1) {
    const pickupCode = generatePickupCode();
    const [packageExists, planExists] = await Promise.all([
      prisma.package?.findUnique
        ? prisma.package.findUnique({ where: { pickupCode }, select: { id: true } })
        : Promise.resolve(null),
      prisma.processingPlan?.findUnique
        ? prisma.processingPlan.findUnique({ where: { pickupCode }, select: { id: true } })
        : Promise.resolve(null),
    ]);
    if (!packageExists && !planExists) return pickupCode;
  }
  throw new Error('生成取货码失败，请重试');
}
