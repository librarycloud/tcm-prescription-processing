import { randomUUID } from "node:crypto";
import { AppError } from "../utils/appError.js";
import { isSuperAdmin } from "../constants/roles.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import {
  PRODUCT_DIFF_OPERATION,
  PRODUCT_DIFF_OPERATION_VALUES,
  REGISTER_OPERATION_VALUES,
  registerQuantitySign,
} from "../constants/productDifference.js";
import { businessScope, resolveBusinessStoreId } from "./permissionService.js";
import { recordOperation } from "./operationLogService.js";
import { productDifferenceRepository as repository } from "../repositories/productDifferenceRepository.js";
import { toPositiveInt } from "../utils/validators.js";

const MAX_REGISTER_ITEMS = 100;

function text(value, max, label, required = false) {
  const result = String(value ?? "").trim();
  if (required && !result) throw new AppError(`请输入${label}`, 400);
  if (result.length > max)
    throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return result || null;
}

function dateOnly(value, label = "业务日期") {
  const source =
    value instanceof Date
      ? value.toISOString().slice(0, 10)
      : String(value || "");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(source))
    throw new AppError(`${label}格式不正确`, 400);
  const date = new Date(`${source}T00:00:00.000Z`);
  if (
    Number.isNaN(date.getTime()) ||
    date.toISOString().slice(0, 10) !== source
  )
    throw new AppError(`${label}格式不正确`, 400);
  return date;
}

function quantity(value, label = "数量", allowSigned = false) {
  const number = Number(value);
  if (!Number.isFinite(number) || (allowSigned ? number === 0 : number <= 0))
    throw new AppError(
      `${label}${allowSigned ? "不能为 0" : "必须大于 0"}`,
      400,
    );
  const normalized = Math.round(number * 1000) / 1000;
  if (Math.abs(normalized) > 999999999.999)
    throw new AppError(`${label}超出允许范围`, 400);
  return normalized;
}

function money(value) {
  const number = Number(value ?? 0);
  if (!Number.isFinite(number) || number < 0 || number > 999999999999.99)
    throw new AppError("零售价格式不正确", 400);
  return Math.round(number * 100) / 100;
}

function operationNo(prefix = "PD") {
  const stamp = new Date().toISOString().slice(2, 10).replaceAll("-", "");
  const suffix = parseInt(randomUUID().slice(0, 8), 16)
    .toString(36)
    .slice(-4)
    .toUpperCase()
    .padStart(4, "0");
  return `${prefix}${stamp}${suffix}`;
}

function normalizeProduct(product) {
  return {
    ...product,
    retailPrice: Number(product.retailPrice),
    diffQuantity: Number(product.diffQuantity),
  };
}

function normalizeLog(log) {
  return {
    ...log,
    changeQuantity: Number(log.changeQuantity),
    balanceAfter: Number(log.balanceAfter),
    product: log.product ? normalizeProduct(log.product) : undefined,
    relatedLog: log.relatedLog
      ? {
          ...log.relatedLog,
          changeQuantity: Number(log.relatedLog.changeQuantity),
          balanceAfter: Number(log.relatedLog.balanceAfter),
        }
      : undefined,
  };
}

function productWhere(actor, query = {}) {
  const where = { ...businessScope(actor, query.storeId), deletedAt: null };
  if (String(query.status) === "0" || String(query.status) === "1")
    where.status = Number(query.status);
  else if (String(query.includeDisabled || "") !== "1")
    where.status = RECORD_STATUS.ENABLED;
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { productCode: { contains: keyword } },
      { name: { contains: keyword } },
      { specification: { contains: keyword } },
    ];
  }
  if (String(query.onlyDifference || "") === "1")
    where.diffQuantity = { not: 0 };
  if (query.direction === "MORE") where.diffQuantity = { gt: 0 };
  if (query.direction === "LESS") where.diffQuantity = { lt: 0 };
  return where;
}

export async function listProducts(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 200);
  const where = productWhere(actor, query);
  const [list, total] = await Promise.all([
    repository.findProducts(prisma, {
      where,
      include: { store: { select: { id: true, name: true, code: true } } },
      orderBy: [{ name: "asc" }, { productCode: "asc" }],
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    repository.countProducts(prisma, { where }),
  ]);
  return {
    list: list.map(normalizeProduct),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function listProductStores(prisma, actor) {
  const where = { deletedAt: null, status: RECORD_STATUS.ENABLED };
  if (!isSuperAdmin(actor)) where.id = Number(actor.storeId);
  return prisma.store.findMany({
    where,
    select: { id: true, name: true, code: true },
    orderBy: [{ name: "asc" }, { id: "asc" }],
  });
}

export async function createProduct(prisma, actor, payload) {
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  const productCode = text(payload.productCode, 64, "商品编号", true);
  const duplicate = await repository.findProduct(prisma, {
    where: { storeId, productCode },
    select: { id: true, deletedAt: true },
  });
  if (duplicate?.deletedAt)
    throw new AppError(
      `商品编号 ${productCode} 已删除，请先恢复原商品后再使用`,
      409,
    );
  if (duplicate) throw new AppError("当前门店已存在相同商品编号", 409);
  const product = await repository.createProduct(prisma, {
    data: {
      storeId,
      productCode,
      name: text(payload.name, 120, "商品名称", true),
      specification: text(payload.specification, 120, "规格"),
      unit: text(payload.unit, 20, "单位", true),
      retailPrice: money(payload.retailPrice),
      status: payload.status === 0 ? 0 : RECORD_STATUS.ENABLED,
      createdBy: Number(actor.id),
    },
    include: { store: { select: { id: true, name: true, code: true } } },
  });
  await recordOperation(prisma, actor, {
    module: "product",
    action: "create",
    targetId: product.id,
    storeId,
    description: `新增商品 ${productCode} ${product.name}`,
  });
  return normalizeProduct(product);
}

export async function updateProduct(prisma, actor, idValue, payload) {
  const scope = businessScope(actor, payload.storeId);
  const current = await repository.findProduct(prisma, {
    where: { id: Number(idValue), ...scope, deletedAt: null },
  });
  if (!current) throw new AppError("商品不存在", 404);
  const productCode = text(payload.productCode, 64, "商品编号", true);
  const duplicate = await repository.findProduct(prisma, {
    where: { storeId: current.storeId, productCode, NOT: { id: current.id } },
    select: { id: true, deletedAt: true },
  });
  if (duplicate?.deletedAt)
    throw new AppError(
      `商品编号 ${productCode} 已删除，请先恢复原商品后再使用`,
      409,
    );
  if (duplicate) throw new AppError("当前门店已存在相同商品编号", 409);
  const updated = await repository.updateProduct(prisma, {
    where: { id: current.id },
    data: {
      productCode,
      name: text(payload.name, 120, "商品名称", true),
      specification: text(payload.specification, 120, "规格"),
      unit: text(payload.unit, 20, "单位", true),
      retailPrice: money(payload.retailPrice),
      status: payload.status === 0 ? 0 : RECORD_STATUS.ENABLED,
      updatedBy: Number(actor.id),
    },
    include: { store: { select: { id: true, name: true, code: true } } },
  });
  await recordOperation(prisma, actor, {
    module: "product",
    action: "update",
    targetId: current.id,
    storeId: current.storeId,
    description: `修改商品 ${updated.productCode} ${updated.name}`,
  });
  return normalizeProduct(updated);
}

async function findScopedProducts(prisma, actor, storeId, productIds) {
  const uniqueIds = [...new Set(productIds.map(Number))];
  if (uniqueIds.some((id) => !Number.isInteger(id) || id <= 0))
    throw new AppError("商品参数不正确", 400);
  const products = await repository.findProducts(prisma, {
    where: {
      id: { in: uniqueIds },
      storeId,
      deletedAt: null,
      status: RECORD_STATUS.ENABLED,
      ...businessScope(actor, storeId),
    },
  });
  if (products.length !== uniqueIds.length)
    throw new AppError("商品不存在、已停用或不属于当前门店", 400);
  return new Map(products.map((product) => [product.id, product]));
}

export function signedRegisterQuantity(operationType, value) {
  const sign = registerQuantitySign(operationType);
  if (!sign) throw new AppError("差异类型不正确", 400);
  return Math.round(quantity(value) * sign * 1000) / 1000;
}

export async function registerProductDifference(prisma, actor, payload) {
  if (!REGISTER_OPERATION_VALUES.includes(payload.operationType))
    throw new AppError("差异类型不正确", 400);
  const entries = Array.isArray(payload.items) ? payload.items : [];
  if (!entries.length) throw new AppError("请至少添加一个商品", 400);
  if (entries.length > MAX_REGISTER_ITEMS)
    throw new AppError(`一次最多登记 ${MAX_REGISTER_ITEMS} 个商品`, 400);
  const productIds = entries.map((item) => Number(item.productId));
  if (new Set(productIds).size !== productIds.length)
    throw new AppError("同一次登记不能重复选择商品", 400);
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  const businessDate = dateOnly(payload.businessDate);
  const products = await findScopedProducts(prisma, actor, storeId, productIds);
  const normalized = entries.map((entry) => ({
    product: products.get(Number(entry.productId)),
    changeQuantity: signedRegisterQuantity(
      payload.operationType,
      entry.quantity,
    ),
    batchNote: text(entry.batchNote, 120, "批号/备注"),
    remark: text(entry.remark, 500, "明细备注"),
  }));
  const number = operationNo();
  const logs = await prisma.$transaction(
    async (tx) => {
      const created = [];
      for (const entry of normalized) {
        const updated = await repository.updateProduct(tx, {
          where: { id: entry.product.id },
          data: {
            diffQuantity: { increment: entry.changeQuantity },
            updatedBy: Number(actor.id),
          },
        });
        created.push(
          await repository.createLog(tx, {
            data: {
              operationNo: number,
              storeId,
              productId: entry.product.id,
              operationType: payload.operationType,
              changeQuantity: entry.changeQuantity,
              balanceAfter: updated.diffQuantity,
              businessDate,
              batchNote: entry.batchNote,
              borrowerName: text(payload.borrowerName, 100, "借货人/经办人"),
              supplierName: text(payload.supplierName, 120, "供货商"),
              remark: entry.remark || text(payload.remark, 500, "备注"),
              createdBy: Number(actor.id),
            },
            include: { product: true },
          }),
        );
      }
      await recordOperation(tx, actor, {
        module: "product-difference",
        action: "register",
        targetId: created[0]?.id,
        storeId,
        description: `登记库存差异 ${number}，共 ${created.length} 项`,
      });
      return created.map(normalizeLog);
    },
    { isolationLevel: "Serializable" },
  );
  return { operationNo: number, logs };
}

export async function writeOffProductDifference(prisma, actor, payload) {
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  const amount = quantity(payload.quantity, "销账数量");
  const businessDate = dateOnly(payload.businessDate, "销账日期");
  const number = operationNo("PX");
  return prisma.$transaction(
    async (tx) => {
      const product = await repository.findProduct(tx, {
        where: {
          id: Number(payload.productId),
          storeId,
          deletedAt: null,
          ...businessScope(actor, storeId),
        },
      });
      if (!product) throw new AppError("商品不存在", 404);
      const current = Number(product.diffQuantity);
      if (current === 0) throw new AppError("当前商品没有待销账差异", 409);
      if (amount > Math.abs(current))
        throw new AppError(
          `销账数量不能超过当前差异 ${Math.abs(current)}`,
          409,
        );
      const changeQuantity = current > 0 ? -amount : amount;
      const operationType =
        current > 0
          ? PRODUCT_DIFF_OPERATION.WRITE_OFF_RECEIPT
          : PRODUCT_DIFF_OPERATION.WRITE_OFF_SHIPMENT;
      const updated = await repository.updateProduct(tx, {
        where: { id: product.id },
        data: {
          diffQuantity: { increment: changeQuantity },
          updatedBy: Number(actor.id),
        },
      });
      const log = await repository.createLog(tx, {
        data: {
          operationNo: number,
          storeId,
          productId: product.id,
          operationType,
          changeQuantity,
          balanceAfter: updated.diffQuantity,
          businessDate,
          borrowerName: text(payload.borrowerName, 100, "经办人"),
          systemDocumentNo: text(payload.systemDocumentNo, 100, "系统单据号"),
          remark: text(payload.remark, 500, "备注"),
          createdBy: Number(actor.id),
        },
        include: { product: true },
      });
      await recordOperation(tx, actor, {
        module: "product-difference",
        action: "write-off",
        targetId: log.id,
        storeId,
        description: `库存差异销账 ${number}：${product.name} ${amount}${product.unit}`,
      });
      return normalizeLog(log);
    },
    { isolationLevel: "Serializable" },
  );
}

function logWhere(actor, query = {}) {
  const where = { ...businessScope(actor, query.storeId) };
  if (query.operationType) {
    if (!PRODUCT_DIFF_OPERATION_VALUES.includes(query.operationType))
      throw new AppError("操作类型不正确", 400);
    where.operationType = query.operationType;
  }
  const productId = Number(query.productId);
  if (Number.isInteger(productId) && productId > 0) where.productId = productId;
  if (query.keyword) {
    const keyword = String(query.keyword).trim();
    where.OR = [
      { operationNo: { contains: keyword } },
      { supplierName: { contains: keyword } },
      { borrowerName: { contains: keyword } },
      { systemDocumentNo: { contains: keyword } },
      { product: { productCode: { contains: keyword } } },
      { product: { name: { contains: keyword } } },
    ];
  }
  const startDate = query.startDate
    ? dateOnly(query.startDate, "开始日期")
    : null;
  const endDate = query.endDate ? dateOnly(query.endDate, "结束日期") : null;
  if (startDate || endDate) {
    where.businessDate = {};
    if (startDate) where.businessDate.gte = startDate;
    if (endDate) where.businessDate.lte = endDate;
  }
  return where;
}

export async function listProductDiffLogs(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const where = logWhere(actor, query);
  const include = {
    store: { select: { id: true, name: true, code: true } },
    product: true,
    creator: { select: { id: true, name: true, nickname: true, phone: true } },
    relatedLog: {
      select: {
        id: true,
        operationNo: true,
        changeQuantity: true,
        balanceAfter: true,
      },
    },
    childLogs: {
      where: { operationType: PRODUCT_DIFF_OPERATION.REVERSAL },
      select: { id: true },
    },
  };
  const [list, total] = await Promise.all([
    repository.findLogs(prisma, {
      where,
      include,
      orderBy: [{ businessDate: "desc" }, { createdAt: "desc" }],
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    repository.countLogs(prisma, { where }),
  ]);
  return {
    list: list.map(normalizeLog),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function getProductDifferenceStats(prisma, actor, query = {}) {
  const scope = { ...businessScope(actor, query.storeId), deletedAt: null };
  const [more, less] = await Promise.all([
    repository.countProducts(prisma, {
      where: { ...scope, diffQuantity: { gt: 0 } },
    }),
    repository.countProducts(prisma, {
      where: { ...scope, diffQuantity: { lt: 0 } },
    }),
  ]);
  return { more, less, total: more + less };
}

export async function reverseProductDiffLog(prisma, actor, idValue, payload) {
  const reason = text(payload.reason, 500, "冲销原因", true);
  const number = operationNo("PC");
  return prisma.$transaction(
    async (tx) => {
      const current = await repository.findLog(tx, {
        where: {
          id: Number(idValue),
          ...businessScope(actor, payload.storeId),
        },
        include: {
          childLogs: {
            where: { operationType: PRODUCT_DIFF_OPERATION.REVERSAL },
            select: { id: true },
          },
          product: true,
        },
      });
      if (!current) throw new AppError("差异流水不存在", 404);
      if (current.operationType === PRODUCT_DIFF_OPERATION.REVERSAL)
        throw new AppError("冲销流水不能再次冲销", 409);
      if (current.childLogs.length) throw new AppError("该流水已经冲销", 409);
      const changeQuantity = -Number(current.changeQuantity);
      const updated = await repository.updateProduct(tx, {
        where: { id: current.productId },
        data: {
          diffQuantity: { increment: changeQuantity },
          updatedBy: Number(actor.id),
        },
      });
      const log = await repository.createLog(tx, {
        data: {
          operationNo: number,
          storeId: current.storeId,
          productId: current.productId,
          operationType: PRODUCT_DIFF_OPERATION.REVERSAL,
          changeQuantity,
          balanceAfter: updated.diffQuantity,
          businessDate: dateOnly(payload.businessDate, "冲销日期"),
          relatedLogId: current.id,
          remark: reason,
          createdBy: Number(actor.id),
        },
        include: { product: true, relatedLog: true },
      });
      await recordOperation(tx, actor, {
        module: "product-difference",
        action: "reverse",
        targetId: log.id,
        storeId: current.storeId,
        description: `冲销差异流水 ${current.operationNo}：${reason}`,
      });
      return normalizeLog(log);
    },
    { isolationLevel: "Serializable" },
  );
}

export const productDifferenceInternals = Object.freeze({
  dateOnly,
  money,
  quantity,
  operationNo,
});
