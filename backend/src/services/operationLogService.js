import { isSuperAdmin } from "../constants/roles.js";

function parseDate(value, endOfDay = false) {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  if (endOfDay) date.setDate(date.getDate() + 1);
  return date;
}

function comparableValue(value) {
  if (value instanceof Date) return value.toISOString();
  if (value === undefined || value === null || value === "") return null;
  if (typeof value === "object") return JSON.stringify(value);
  return value;
}

function displayValue(value, field) {
  if (field.format) return field.format(value);
  const comparable = comparableValue(value);
  if (comparable === null) return "空";
  const mapped = field.values?.[String(comparable)];
  const text = String(mapped ?? comparable);
  return text.length > 80 ? `${text.slice(0, 80)}...` : text;
}

export function describeChanges(before, after, fields, fallback = "未检测到字段变化") {
  const changes = [];
  for (const entry of fields) {
    const field = typeof entry === "string" ? { key: entry, label: entry } : entry;
    const getValue = field.get || ((record) => record?.[field.key]);
    const previous = getValue(before);
    const next = getValue(after);
    if (comparableValue(previous) === comparableValue(next)) continue;
    changes.push(
      field.sensitive
        ? `${field.label}：已修改`
        : `${field.label}：${displayValue(previous, field)} → ${displayValue(next, field)}`,
    );
  }
  return changes.join("；") || fallback;
}

export async function recordOperation(prisma, actor, data = {}) {
  return prisma.operationLog.create({
    data: {
      actorId: actor?.id ? Number(actor.id) : null,
      actorType: actor?.accountType === "admin" ? "admin" : actor?.accountType === "user" ? "user" : null,
      actorRole: actor?.role == null ? null : Number(actor.role),
      actorName: actor?.nickname || actor?.phone || null,
      storeId:
        data.storeId == null
          ? actor?.storeId
            ? Number(actor.storeId)
            : null
          : Number(data.storeId),
      module: String(data.module || "system").slice(0, 50),
      action: String(data.action || "unknown").slice(0, 50),
      targetId: data.targetId == null ? null : Number(data.targetId),
      description: String(data.description || "").slice(0, 500),
      ip: data.ip || actor?.ip || null,
      userAgent: data.userAgent || actor?.userAgent || null,
    },
  });
}

function uniqueIds(values) {
  return [...new Set(values.filter((value) => Number.isInteger(value) && value > 0))];
}

function formatUserName(user) {
  return user?.name || user?.nickname || user?.phone || null;
}

function formatTargetLabel(log, targets) {
  const id = Number(log.targetId);
  if (!Number.isInteger(id) || id <= 0) return null;

  switch (log.module) {
    case "package": {
      const item = targets.packages.get(id);
      return item
        ? `包裹「${item.pickupCode} ${item.receiverName} ${item.itemName}」`
        : "包裹";
    }
    case "processing": {
      const item = targets.processingPlans.get(id);
      if (!item) return "加工计划";
      const prescription = item.prescription;
      const prescriptionLabel = prescription?.prescriptionNo || prescription?.customerName || "处方";
      const processType = item.processType?.name || "加工";
      return `加工计划「${prescriptionLabel} 第${item.batchNo}批 ${processType} ${item.totalDose}剂」`;
    }
    case "prescription": {
      const item = targets.prescriptions.get(id);
      return item
        ? `处方「${item.prescriptionNo || item.customerName}」`
        : "处方";
    }
    case "doctor": {
      const item = targets.doctors.get(id);
      return item ? `医生「${item.name}」` : "医生";
    }
    case "dictionary": {
      const item = targets.dictionaries.get(id);
      return item
        ? `字典「${item.type} / ${item.name}」`
        : "字典";
    }
    case "store": {
      const item = targets.stores.get(id);
      return item ? `门店「${item.name}」` : "门店";
    }
    case "user": {
      const item = targets.users.get(id);
      return item ? `用户「${formatUserName(item)}」` : "用户";
    }
    case "store-admin": {
      const item = targets.admins.get(id);
      return item ? `管理员「${formatUserName(item)}」` : "管理员";
    }
    case "store-transfer": {
      const item = targets.storeTransfers.get(id);
      return item
        ? `调拨单「${item.transferNo}」`
        : "调拨单";
    }
    case "product": {
      const item = targets.products.get(id);
      return item ? `商品「${item.productCode} ${item.name}」` : "商品";
    }
    case "product-difference": {
      const item = targets.productDiffLogs.get(id);
      return item ? `库存差异流水「${item.operationNo}」` : "库存差异流水";
    }
    case "e6-integration": {
      if (log.action?.startsWith("doctor_mapping")) return "E6医师映射";
      if (log.action?.startsWith("config_")) return "E6门店配置";
      const item = targets.e6Imports.get(id);
      return item ? `E6订单「${item.externalOrderNo}」` : "E6对接记录";
    }
    default:
      return "业务记录";
  }
}

async function enrichOperationLogs(prisma, logs) {
  if (!logs.length) return logs;
  const idsFor = (module) =>
    uniqueIds(
      logs
        .filter((log) => log.module === module)
        .map((log) => Number(log.targetId)),
    );
  const actorIds = uniqueIds(logs.map((log) => Number(log.actorId)));
  const userIds = uniqueIds([...actorIds, ...idsFor("user")]);
  const adminIds = uniqueIds([...actorIds, ...idsFor("store-admin")]);
  const [users, admins, packages, processingPlans, prescriptions, doctors, dictionaries, stores, storeTransfers, products, productDiffLogs, e6Imports] =
    await Promise.all([
      userIds.length
        ? prisma.user.findMany({
            where: { id: { in: userIds } },
            select: { id: true, name: true, nickname: true, phone: true },
          })
        : [],
      adminIds.length && prisma.admin?.findMany
        ? prisma.admin.findMany({
            where: { id: { in: adminIds } },
            select: { id: true, name: true, nickname: true, phone: true },
          })
        : [],
      idsFor("package").length
        ? prisma.package.findMany({
            where: { id: { in: idsFor("package") } },
            select: {
              id: true,
              pickupCode: true,
              itemName: true,
              receiverName: true,
            },
          })
        : [],
      idsFor("processing").length
        ? prisma.processingPlan.findMany({
            where: { id: { in: idsFor("processing") } },
            select: {
              id: true,
              batchNo: true,
              totalDose: true,
              processType: { select: { name: true } },
              prescription: {
                select: { prescriptionNo: true, customerName: true },
              },
            },
          })
        : [],
      idsFor("prescription").length
        ? prisma.prescription.findMany({
            where: { id: { in: idsFor("prescription") } },
            select: { id: true, prescriptionNo: true, customerName: true },
          })
        : [],
      idsFor("doctor").length
        ? prisma.doctor.findMany({
            where: { id: { in: idsFor("doctor") } },
            select: { id: true, name: true },
          })
        : [],
      idsFor("dictionary").length
        ? prisma.dictionary.findMany({
            where: { id: { in: idsFor("dictionary") } },
            select: { id: true, type: true, name: true },
          })
        : [],
      idsFor("store").length
        ? prisma.store.findMany({
            where: { id: { in: idsFor("store") } },
            select: { id: true, name: true },
          })
        : [],
      idsFor("store-transfer").length
        ? prisma.storeTransfer.findMany({
            where: { id: { in: idsFor("store-transfer") } },
            select: { id: true, transferNo: true },
          })
        : [],
      idsFor("product").length
        ? prisma.product.findMany({
            where: { id: { in: idsFor("product") } },
            select: { id: true, productCode: true, name: true },
          })
        : [],
      idsFor("product-difference").length
        ? prisma.productsDiffLog.findMany({
            where: { id: { in: idsFor("product-difference") } },
            select: { id: true, operationNo: true },
          })
        : [],
      idsFor("e6-integration").length
        ? prisma.e6Import.findMany({
            where: { id: { in: idsFor("e6-integration") } },
            select: { id: true, externalOrderNo: true },
          })
        : [],
    ]);
  const targets = {
    users: new Map(users.map((item) => [item.id, item])),
    admins: new Map(admins.map((item) => [item.id, item])),
    packages: new Map(packages.map((item) => [item.id, item])),
    processingPlans: new Map(processingPlans.map((item) => [item.id, item])),
    prescriptions: new Map(prescriptions.map((item) => [item.id, item])),
    doctors: new Map(doctors.map((item) => [item.id, item])),
    dictionaries: new Map(dictionaries.map((item) => [item.id, item])),
    stores: new Map(stores.map((item) => [item.id, item])),
    storeTransfers: new Map(storeTransfers.map((item) => [item.id, item])),
    products: new Map(products.map((item) => [item.id, item])),
    productDiffLogs: new Map(productDiffLogs.map((item) => [item.id, item])),
    e6Imports: new Map(e6Imports.map((item) => [item.id, item])),
  };

  return logs.map((log) => {
    const targetLabel = formatTargetLabel(log, targets);
    const actor = log.actorType === "admin"
      ? targets.admins.get(Number(log.actorId))
      : targets.users.get(Number(log.actorId));
    const actorName = formatUserName(actor) || log.actorName;
    return {
      ...log,
      actorName,
      targetLabel,
      description: targetLabel ? `${targetLabel}：${log.description}` : log.description,
    };
  });
}

export async function listOperationLogs(prisma, actor, query = {}) {
  const page = Math.max(Number(query.page) || 1, 1);
  const pageSize = Math.min(Math.max(Number(query.pageSize) || 20, 1), 100);
  const where = isSuperAdmin(actor) ? {} : { storeId: Number(actor.storeId) };
  const storeId = Number(query.storeId);
  const actorId = Number(query.actorId);
  if (Number.isInteger(storeId) && storeId > 0 && isSuperAdmin(actor))
    where.storeId = storeId;
  if (Number.isInteger(actorId) && actorId > 0) where.actorId = actorId;
  if (query.module) where.module = String(query.module).trim();
  if (query.action) where.action = String(query.action).trim();
  const startDate = parseDate(query.startDate);
  const endDate = parseDate(query.endDate, true);
  if (startDate || endDate) {
    where.createdAt = {};
    if (startDate) where.createdAt.gte = startDate;
    if (endDate) where.createdAt.lt = endDate;
  }
  const [list, total] = await Promise.all([
    prisma.operationLog.findMany({
      where,
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
      include: { store: { select: { id: true, name: true, code: true } } },
    }),
    prisma.operationLog.count({ where }),
  ]);
  return {
    list: await enrichOperationLogs(prisma, list),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}
