export const productDifferenceRepository = Object.freeze({
  findProducts: (prisma, args) => prisma.product.findMany(args),
  findProduct: (prisma, args) => prisma.product.findFirst(args),
  countProducts: (prisma, args) => prisma.product.count(args),
  createProduct: (prisma, args) => prisma.product.create(args),
  updateProduct: (prisma, args) => prisma.product.update(args),
  findLogs: (prisma, args) => prisma.productsDiffLog.findMany(args),
  findLog: (prisma, args) => prisma.productsDiffLog.findFirst(args),
  countLogs: (prisma, args) => prisma.productsDiffLog.count(args),
  createLog: (prisma, args) => prisma.productsDiffLog.create(args),
});
