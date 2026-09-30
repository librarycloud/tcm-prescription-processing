export async function getSystemConfigs(prisma) {
  const configs = await prisma.systemConfig.findMany({
    orderBy: { id: 'asc' }
  });
  return configs.reduce((acc, curr) => {
    acc[curr.item] = curr.value;
    return acc;
  }, {});
}

export async function updateSystemConfigs(prisma, configData) {
  const updates = Object.entries(configData).map(([item, value]) => {
    return prisma.systemConfig.upsert({
      where: { item },
      update: { value: String(value) },
      create: { item, value: String(value), type: 'string' }
    });
  });
  await prisma.$transaction(updates);
  return getSystemConfigs(prisma);
}

export async function getSystemConfigValue(prisma, itemKey) {
  const conf = await prisma.systemConfig.findUnique({
    where: { item: itemKey }
  });
  return conf ? conf.value : null;
}
