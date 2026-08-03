export const packageRepository = Object.freeze({
  findFirst: (prisma, args) => prisma.package.findFirst(args),
  findUnique: (prisma, args) => prisma.package.findUnique(args),
  findMany: (prisma, args) => prisma.package.findMany(args),
  count: (prisma, args) => prisma.package.count(args),
  create: (prisma, args) => prisma.package.create(args),
  update: (prisma, args) => prisma.package.update(args),
  updateMany: (prisma, args) => prisma.package.updateMany(args),
});
