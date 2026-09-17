export const prescriptionRepository = Object.freeze({
  findFirst: (prisma, args) => prisma.prescription.findFirst(args),
  findUnique: (prisma, args) => prisma.prescription.findUnique(args),
  findMany: (prisma, args) => prisma.prescription.findMany(args),
  count: (prisma, args) => prisma.prescription.count(args),
  create: (prisma, args) => prisma.prescription.create(args),
  update: (prisma, args) => prisma.prescription.update(args),
});
