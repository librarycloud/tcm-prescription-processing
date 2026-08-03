export const processingPlanRepository = Object.freeze({
  findFirst: (prisma, args) => prisma.processingPlan.findFirst(args),
  findUnique: (prisma, args) => prisma.processingPlan.findUnique(args),
  findMany: (prisma, args) => prisma.processingPlan.findMany(args),
  count: (prisma, args) => prisma.processingPlan.count(args),
  create: (prisma, args) => prisma.processingPlan.create(args),
  update: (prisma, args) => prisma.processingPlan.update(args),
  updateMany: (prisma, args) => prisma.processingPlan.updateMany(args),
});
