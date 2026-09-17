export const storeTransferRepository = Object.freeze({
  findFirst: (prisma, args) => prisma.storeTransfer.findFirst(args),
  findMany: (prisma, args) => prisma.storeTransfer.findMany(args),
  count: (prisma, args) => prisma.storeTransfer.count(args),
  create: (prisma, args) => prisma.storeTransfer.create(args),
  update: (prisma, args) => prisma.storeTransfer.update(args),
  createReturn: (prisma, args) => prisma.storeTransferReturn.create(args),
  updateReturn: (prisma, args) => prisma.storeTransferReturn.update(args),
});
