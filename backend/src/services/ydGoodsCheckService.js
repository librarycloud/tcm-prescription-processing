import ExcelJS from "exceljs";
import { AppError } from "../utils/appError.js";
import { isStoreStaff } from "../constants/roles.js";
import { businessScope, resolveBusinessStoreId } from "./permissionService.js";
import { toPositiveInt } from "../utils/validators.js";

export const CHECK_STATUS = Object.freeze({
  UNCOUNTED: 0,
  MATCH_PENDING_REVIEW: 1,
  PENDING_RECOUNT: 2,
  RECOUNT_MATCH_PENDING_REVIEW: 3,
  PENDING_ADJUSTMENT: 4,
  NEW_BATCH: 5,
  CONFIRMED: 6,
});

function quantity(value) {
  const result = Number(value);
  if (!Number.isFinite(result) || result < 0) throw new AppError("盘点数量必须是大于等于0的数字", 400);
  return result;
}

function text(value) {
  return String(value ?? "").trim();
}

function normalizeCategoryCodes(value) {
  const values = Array.isArray(value) ? value : (value === undefined || value === null || value === "" ? [] : [value]);
  return [...new Set(values.map(text).filter(Boolean))].slice(0, 200);
}

function productFilter(categoryCodes, keyword = "") {
  const filter = {};
  if (categoryCodes.length) filter.categoryCode = { in: categoryCodes };
  if (keyword) filter.OR = [
    { productCode: { contains: keyword } },
    { name: { contains: keyword } },
    { barcode: { contains: keyword } },
  ];
  return filter;
}

function itemKey(productId, batchNo, locationName) {
  return `${productId}|${text(batchNo)}|${text(locationName)}`;
}

function currentSystem(item) {
  return item.recountQty !== null && item.recountQty !== undefined
    ? Number(item.recountSystemQty || 0)
    : Number(item.systemQty || 0);
}

function effectiveCount(item) {
  return item.recountQty !== null && item.recountQty !== undefined
    ? Number(item.recountQty)
    : item.firstCountQty === null || item.firstCountQty === undefined
      ? null
      : Number(item.firstCountQty);
}

function normalizeItem(item, counted = true) {
  const countedQty = effectiveCount(item);
  const systemQty = currentSystem(item);
  const difference = countedQty === null ? null : countedQty - systemQty;
  const needsAdjustment = countedQty !== null && (
    difference !== 0 || item.locationStatus === 1 || (Number(item.systemQty) === 0 && countedQty > 0)
  );
  return {
    id: item.id ?? null,
    checkId: item.checkId,
    storeId: item.storeId,
    productId: item.productId,
    product: item.product || null,
    price: item.price === null || item.price === undefined
      ? (item.product?.retailPrice === null || item.product?.retailPrice === undefined ? null : Number(item.product.retailPrice))
      : Number(item.price),
    batchNo: item.batchNo || "",
    systemLocationName: item.systemLocationName || "",
    countLocationName: item.countLocationName || null,
    systemQty: Number(item.systemQty || 0),
    firstCountQty: item.firstCountQty === null || item.firstCountQty === undefined ? null : Number(item.firstCountQty),
    firstCountedAt: item.firstCountedAt,
    firstCountedBy: item.firstCountedBy,
    recountQty: item.recountQty === null || item.recountQty === undefined ? null : Number(item.recountQty),
    recountSystemQty: item.recountSystemQty === null || item.recountSystemQty === undefined ? null : Number(item.recountSystemQty),
    recountedAt: item.recountedAt,
    recountedBy: item.recountedBy,
    locationStatus: Number(item.locationStatus || 0),
    checkStatus: Number(item.checkStatus || 0),
    reviewStatus: Number(item.reviewStatus || 0),
    reviewedBy: item.reviewedBy,
    reviewedAt: item.reviewedAt,
    remark: item.remark || "",
    counted,
    effectiveCount: countedQty,
    difference,
    needsAdjustment,
  };
}

const productInclude = {
  select: {
    id: true, productCode: true, name: true, barcode: true,
    specification: true, dosageForm: true, manufacturer: true, categoryAttribute: true, unit: true, retailPrice: true,
  },
};

function addInventoryPrices(rows) {
  return rows.map((row) => ({
    ...row,
    price: row.product?.retailPrice === null || row.product?.retailPrice === undefined
      ? null
      : Number(row.product.retailPrice),
  }));
}

async function getCheck(prisma, actor, id) {
  const checkId = toPositiveInt(id, 0);
  if (!checkId) throw new AppError("盘点单号不正确", 400);
  const check = await prisma.ydGoodsCheck.findFirst({
    where: { id: checkId, ...businessScope(actor) },
    include: { store: { select: { id: true, name: true, code: true } } },
  });
  if (!check) throw new AppError("盘点单不存在或无权访问", 404);
  return check;
}

export async function listGoodsChecks(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const where = { ...businessScope(actor, query.storeId) };
  if (query.status !== undefined && query.status !== "") where.status = Number(query.status);
  const [checks, total] = await Promise.all([
    prisma.ydGoodsCheck.findMany({
      where, include: { store: { select: { id: true, name: true, code: true } }, items: true },
      orderBy: { createdAt: "desc" }, skip: (page - 1) * pageSize, take: pageSize,
    }),
    prisma.ydGoodsCheck.count({ where }),
  ]);
  return {
    list: checks.map((check) => {
      const items = check.items || [];
      const summary = { total: items.length, counted: 0, pendingRecount: 0, adjustment: 0, confirmed: 0 };
      items.forEach((item) => {
        if (item.firstCountQty !== null) summary.counted += 1;
        if (Number(item.checkStatus) === CHECK_STATUS.PENDING_RECOUNT) summary.pendingRecount += 1;
        if (normalizeItem(item).needsAdjustment) summary.adjustment += 1;
        if (Number(item.reviewStatus) === 1) summary.confirmed += 1;
      });
      const { items: _items, ...rest } = check;
      return { ...rest, summary };
    }),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function createGoodsCheck(prisma, actor, payload = {}) {
  const storeId = await resolveBusinessStoreId(prisma, actor, payload.storeId);
  const checkName = text(payload.checkName);
  if (!checkName) throw new AppError("盘点名称不能为空", 400);
  const checkType = Number(payload.checkType || 1);
  if (!Number.isInteger(checkType) || checkType < 1 || checkType > 3) throw new AppError("盘点类型不正确", 400);
  const categoryCodes = normalizeCategoryCodes(payload.categoryCodes);
  return prisma.ydGoodsCheck.create({
    data: { storeId, checkName, checkType, categoryCodes: categoryCodes.length ? categoryCodes : null, status: 0, createdBy: Number(actor.id) },
    include: { store: { select: { id: true, name: true, code: true } } },
  });
}

export async function getGoodsCheck(prisma, actor, id) {
  const check = await getCheck(prisma, actor, id);
  const items = await prisma.ydGoodsCheckItem.findMany({
    where: { checkId: check.id }, include: { product: productInclude }, orderBy: [{ productId: "asc" }, { batchNo: "asc" }, { systemLocationName: "asc" }],
  });
  const pricedItems = addInventoryPrices(items);
  return { ...check, items: pricedItems.map(normalizeItem) };
}

async function inventorySnapshot(prisma, check, productId, batchNo, locationName) {
  const where = { storeId: check.storeId, productId, batchNo };
  if (locationName !== undefined && locationName !== null) where.locationName = text(locationName);
  const rows = await prisma.e6PharmacyInventoryBatch.findMany({ where, orderBy: { id: "asc" } });
  if (!rows.length) return { quantity: 0, locationName: text(locationName) };
  if (rows.length === 1) return { quantity: Number(rows[0].quantity || 0), locationName: rows[0].locationName || "" };
  return { quantity: rows.reduce((sum, row) => sum + Number(row.quantity || 0), 0), locationName: "" };
}

function checkStatusForInitial(systemQty, countedQty) {
  if (systemQty === 0 && countedQty > 0) return CHECK_STATUS.NEW_BATCH;
  return countedQty === systemQty ? CHECK_STATUS.MATCH_PENDING_REVIEW : CHECK_STATUS.PENDING_RECOUNT;
}

export async function addInitialCount(prisma, actor, checkId, payload = {}) {
  const check = await getCheck(prisma, actor, checkId);
  if (Number(check.status) === 2) throw new AppError("盘点单已完成", 400);
  const productId = toPositiveInt(payload.productId, 0);
  if (!productId) throw new AppError("请选择商品", 400);
  const product = await prisma.e6PharmacyProduct.findUnique({ where: { id: productId } });
  if (!product) throw new AppError("商品不存在或不属于该门店", 404);
  const categoryCodes = normalizeCategoryCodes(check.categoryCodes);
  if (categoryCodes.length && !categoryCodes.includes(text(product.categoryCode))) throw new AppError("该商品不在本盘点计划的分类范围内", 400);
  const batchNo = text(payload.batchNo);
  const requestedLocation = payload.locationName === undefined ? undefined : text(payload.locationName);
  const snapshot = await inventorySnapshot(prisma, check, productId, batchNo, requestedLocation);
  const systemLocationName = snapshot.locationName;
  const existing = await prisma.ydGoodsCheckItem.findFirst({ where: { checkId: check.id, productId, batchNo, systemLocationName } });
  if (existing?.firstCountQty !== null && existing?.firstCountQty !== undefined) throw new AppError("该批次已经完成初盘", 409);
  const firstCountQty = quantity(payload.firstCountQty ?? payload.countQty);
  const systemQty = payload.systemQty === undefined || payload.systemQty === null || payload.systemQty === ""
    ? snapshot.quantity
    : quantity(payload.systemQty);
  const countLocationName = requestedLocation !== undefined && requestedLocation !== "" ? requestedLocation : null;
  const data = {
    checkId: check.id, storeId: check.storeId, productId, batchNo, systemLocationName,
    countLocationName, systemQty, firstCountQty,
    firstCountedAt: new Date(), firstCountedBy: Number(actor.id),
    locationStatus: countLocationName && countLocationName !== systemLocationName ? 1 : 0, checkStatus: checkStatusForInitial(snapshot.quantity, firstCountQty), reviewStatus: 0,
  };
  const item = existing
    ? await prisma.ydGoodsCheckItem.update({ where: { id: existing.id }, data, include: { product: productInclude } })
    : await prisma.ydGoodsCheckItem.create({ data, include: { product: productInclude } });
  await prisma.ydGoodsCheck.update({ where: { id: check.id }, data: { status: 1, startedAt: check.startedAt || new Date() } });
  return normalizeItem(item);
}

export async function recountGoodsCheckItem(prisma, actor, itemId, payload = {}) {
  const id = toPositiveInt(itemId, 0);
  const item = await prisma.ydGoodsCheckItem.findUnique({ where: { id }, include: { check: true } });
  if (!item) throw new AppError("盘点明细不存在", 404);
  await getCheck(prisma, actor, item.checkId);
  const recountQty = quantity(payload.recountQty ?? payload.countQty);
  const snapshot = await inventorySnapshot(prisma, item.check, item.productId, item.batchNo, item.systemLocationName);
  const status = recountQty === snapshot.quantity ? CHECK_STATUS.RECOUNT_MATCH_PENDING_REVIEW : CHECK_STATUS.PENDING_ADJUSTMENT;
  const updated = await prisma.ydGoodsCheckItem.update({
    where: { id }, data: { recountQty, recountSystemQty: snapshot.quantity, recountedAt: new Date(), recountedBy: Number(actor.id), checkStatus: status, reviewStatus: 0 }, include: { product: productInclude },
  });
  return normalizeItem(updated);
}

export async function updateGoodsCheckLocation(prisma, actor, itemId, payload = {}) {
  const id = toPositiveInt(itemId, 0);
  const item = await prisma.ydGoodsCheckItem.findUnique({ where: { id }, include: { check: true } });
  if (!item) throw new AppError("盘点明细不存在", 404);
  await getCheck(prisma, actor, item.checkId);
  const location = text(payload.countLocationName ?? payload.locationName);
  const changed = Boolean(location) && location !== text(item.systemLocationName);
  const updated = await prisma.ydGoodsCheckItem.update({ where: { id }, data: { countLocationName: changed ? location : null, locationStatus: changed ? 1 : 0 }, include: { product: productInclude } });
  return normalizeItem(updated);
}

export async function reviewGoodsCheckItem(prisma, actor, itemId, payload = {}) {
  const id = toPositiveInt(itemId, 0);
  const item = await prisma.ydGoodsCheckItem.findUnique({ where: { id } });
  if (!item) throw new AppError("盘点明细不存在", 404);
  await getCheck(prisma, actor, item.checkId);
  if ((item.firstCountedBy && Number(item.firstCountedBy) === Number(actor.id)) || (item.recountedBy && Number(item.recountedBy) === Number(actor.id))) throw new AppError("需要第二名盘点员确认", 400);
  const approved = payload.approved === false || Number(payload.reviewStatus) === 2 ? false : true;
  const updated = await prisma.ydGoodsCheckItem.update({ where: { id }, data: { reviewStatus: approved ? 1 : 2, reviewedBy: Number(actor.id), reviewedAt: new Date(), checkStatus: approved ? CHECK_STATUS.CONFIRMED : item.checkStatus }, include: { product: productInclude } });
  return normalizeItem(updated);
}

export async function reviewGoodsCheckItems(prisma, actor, itemIds, payload = {}) {
  const ids = [...new Set((Array.isArray(itemIds) ? itemIds : []).map((value) => toPositiveInt(value, 0)).filter(Boolean))];
  if (!ids.length) throw new AppError("请选择需要复核的盘点记录", 400);
  const items = await prisma.ydGoodsCheckItem.findMany({ where: { id: { in: ids } } });
  if (items.length !== ids.length) throw new AppError("部分盘点记录不存在", 404);
  const checkId = toPositiveInt(payload.checkId, 0);
  if (checkId && items.some((item) => Number(item.checkId) !== checkId)) throw new AppError("盘点记录不属于当前盘点单", 400);
  for (const item of items) {
    await getCheck(prisma, actor, item.checkId);
    if (item.firstCountQty === null || item.firstCountQty === undefined) throw new AppError("只能复核已完成盘点的记录", 400);
    if (Number(item.reviewStatus) === 1) throw new AppError("部分记录已经确认，请刷新后重试", 409);
    if ((item.firstCountedBy && Number(item.firstCountedBy) === Number(actor.id)) || (item.recountedBy && Number(item.recountedBy) === Number(actor.id))) throw new AppError("需要第二名盘点员确认", 400);
  }
  const approved = !(payload.approved === false || Number(payload.reviewStatus) === 2);
  const result = await prisma.ydGoodsCheckItem.updateMany({
    where: { id: { in: ids }, reviewStatus: { not: 1 } },
    data: { reviewStatus: approved ? 1 : 2, reviewedBy: Number(actor.id), reviewedAt: new Date(), ...(approved ? { checkStatus: CHECK_STATUS.CONFIRMED } : {}) },
  });
  return { count: result.count, approved };
}

export async function listGoodsCheckItems(prisma, actor, checkId, query = {}) {
  const check = await getCheck(prisma, actor, checkId);
  const where = { checkId: check.id };
  const keyword = text(query.keyword);
  if (keyword) where.product = { OR: [{ productCode: { contains: keyword } }, { name: { contains: keyword } }, { barcode: { contains: keyword } }] };
  if (query.batchNo) where.batchNo = { contains: text(query.batchNo) };
  if (query.locationName) where.OR = [{ systemLocationName: { contains: text(query.locationName) } }, { countLocationName: { contains: text(query.locationName) } }];
  const checkStatus = query.checkStatus ?? query.check_status;
  const missingRequested = String(checkStatus ?? "") === "missing" || text(query.status) === "missing";
  if (checkStatus !== undefined && checkStatus !== "" && !missingRequested) where.checkStatus = Number(checkStatus);
  const locationStatus = query.locationStatus ?? query.location_status;
  if (locationStatus !== undefined && locationStatus !== "") where.locationStatus = Number(locationStatus);
  const rows = await prisma.ydGoodsCheckItem.findMany({ where, include: { product: productInclude }, orderBy: [{ productId: "asc" }, { batchNo: "asc" }, { systemLocationName: "asc" }] });
  const pricedRows = missingRequested ? rows : addInventoryPrices(rows);
  let list = pricedRows.map(normalizeItem);
  const status = missingRequested ? "missing" : text(query.status);
  if (status === "recount") list = list.filter((row) => row.checkStatus === CHECK_STATUS.PENDING_RECOUNT);
  if (status === "adjustment") list = list.filter((row) => row.needsAdjustment);
  if (status === "unreviewed") list = list.filter((row) => row.reviewStatus === 0 && row.firstCountQty !== null);
  if (status === "new") list = list.filter((row) => row.checkStatus === CHECK_STATUS.NEW_BATCH);
  if (status === "missing") {
    const missing = await missingCandidates(prisma, check, rows, keyword);
    const page = toPositiveInt(query.page, 1); const pageSize = Math.min(toPositiveInt(query.pageSize, 50), 200);
    return { list: missing.slice((page - 1) * pageSize, page * pageSize), pagination: { page, pageSize, total: missing.length, pages: Math.ceil(missing.length / pageSize) } };
  }
  const page = toPositiveInt(query.page, 1); const pageSize = Math.min(toPositiveInt(query.pageSize, 50), 200); const total = list.length;
  return { list: list.slice((page - 1) * pageSize, page * pageSize), pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) } };
}

async function missingCandidates(prisma, check, rows, keyword) {
  const categoryCodes = normalizeCategoryCodes(check.categoryCodes);
  const productWhere = productFilter(categoryCodes, keyword);
  const inventories = await prisma.e6PharmacyInventoryBatch.findMany({ where: { storeId: check.storeId, quantity: { gt: 0 }, ...(Object.keys(productWhere).length ? { product: productWhere } : {}) }, include: { product: productInclude }, orderBy: [{ productId: "asc" }, { batchNo: "asc" }] });
  const counted = new Set(rows.map((row) => itemKey(row.productId, row.batchNo, row.systemLocationName)));
  return inventories.filter((row) => !counted.has(itemKey(row.productId, row.batchNo, row.locationName))).map((row) => normalizeItem({ checkId: check.id, storeId: check.storeId, productId: row.productId, product: row.product, batchNo: row.batchNo, systemLocationName: row.locationName, systemQty: row.quantity, firstCountQty: null, checkStatus: 0 } , false));
}

export async function listGoodsCheckCandidates(prisma, actor, checkId, query = {}) {
  const check = await getCheck(prisma, actor, checkId);
  const keyword = text(query.keyword);
  const myCounted = isStoreStaff(actor) && (query.myCounted === true || text(query.myCounted) === "1");
  const countedOnly = query.countedOnly === true || text(query.countedOnly) === "1";
  const categoryCodes = normalizeCategoryCodes(check.categoryCodes);
  const productWhere = productFilter(categoryCodes, keyword);
  const inventories = await prisma.e6PharmacyInventoryBatch.findMany({ where: { storeId: check.storeId, quantity: { gt: 0 }, ...(Object.keys(productWhere).length ? { product: productWhere } : {}) }, include: { product: productInclude }, orderBy: [{ productId: "asc" }, { batchNo: "asc" }, { locationName: "asc" }] });
  const rows = await prisma.ydGoodsCheckItem.findMany({
    where: { checkId: check.id },
    select: {
      id: true, productId: true, batchNo: true, systemLocationName: true,
      countLocationName: true, systemQty: true, firstCountQty: true,
      recountQty: true, recountSystemQty: true, checkStatus: true, reviewStatus: true,
      firstCountedBy: true, recountedBy: true,
      product: { select: productInclude.select },
    },
  });
  const visibleRows = myCounted
    ? rows.filter((row) => Number(row.firstCountedBy) === Number(actor.id) || Number(row.recountedBy) === Number(actor.id))
    : rows;
  const itemsByKey = new Map(visibleRows.map((row) => [itemKey(row.productId, row.batchNo, row.systemLocationName), row]));
  const result = inventories.flatMap((row) => {
    const item = itemsByKey.get(itemKey(row.productId, row.batchNo, row.locationName));
    if ((myCounted || countedOnly) && !item) return [];
    return {
      ...row,
      quantity: Number(row.quantity || 0),
      counted: Boolean(item && item.firstCountQty !== null && item.firstCountQty !== undefined),
      checkItemId: item?.id || null,
      countLocationName: item?.countLocationName || null,
      systemQty: item ? Number(item.systemQty || 0) : Number(row.quantity || 0),
      firstCountQty: item?.firstCountQty === null || item?.firstCountQty === undefined ? null : Number(item.firstCountQty),
      recountQty: item?.recountQty === null || item?.recountQty === undefined ? null : Number(item.recountQty),
      recountSystemQty: item?.recountSystemQty === null || item?.recountSystemQty === undefined ? null : Number(item.recountSystemQty),
      checkStatus: item ? Number(item.checkStatus || 0) : CHECK_STATUS.UNCOUNTED,
      reviewStatus: item ? Number(item.reviewStatus || 0) : 0,
    };
  });
  if (myCounted || countedOnly) {
    const inventoryKeys = new Set(inventories.map((row) => itemKey(row.productId, row.batchNo, row.locationName)));
    visibleRows
      .filter((row) => !inventoryKeys.has(itemKey(row.productId, row.batchNo, row.systemLocationName)))
      .forEach((row) => {
        result.push({
          productId: row.productId,
          product: row.product,
          batchNo: row.batchNo,
          locationName: row.systemLocationName,
          quantity: Number(row.systemQty || 0),
          counted: row.firstCountQty !== null && row.firstCountQty !== undefined,
          checkItemId: row.id,
          countLocationName: row.countLocationName || null,
          systemQty: Number(row.systemQty || 0),
          firstCountQty: row.firstCountQty === null || row.firstCountQty === undefined ? null : Number(row.firstCountQty),
          recountQty: row.recountQty === null || row.recountQty === undefined ? null : Number(row.recountQty),
          recountSystemQty: row.recountSystemQty === null || row.recountSystemQty === undefined ? null : Number(row.recountSystemQty),
          checkStatus: Number(row.checkStatus || 0),
          reviewStatus: Number(row.reviewStatus || 0),
        });
      });
  }
  if (keyword) {
    const products = await prisma.e6PharmacyProduct.findMany({
      where: productFilter(categoryCodes, keyword),
      select: productInclude.select,
      orderBy: { name: "asc" },
      take: 100,
    });
    products.forEach((product) => {
      result.push({ productId: product.id, product, batchNo: "", locationName: "", quantity: 0, counted: false, manualBatch: true });
    });
  }
  return result;
}

export async function finishGoodsCheck(prisma, actor, checkId) {
  const check = await getCheck(prisma, actor, checkId);
  await prisma.ydGoodsCheck.update({ where: { id: check.id }, data: { status: 2, finishedAt: new Date() } });
  return { ...check, status: 2, finishedAt: new Date() };
}

export async function exportGoodsCheck(prisma, actor, checkId, type = "all") {
  const check = await getCheck(prisma, actor, checkId);
  const rows = await prisma.ydGoodsCheckItem.findMany({ where: { checkId: check.id }, include: { product: productInclude }, orderBy: [{ productId: "asc" }, { batchNo: "asc" }] });
  const pricedRows = addInventoryPrices(rows);
  let list = pricedRows.map(normalizeItem);
  if (type === "recount") list = list.filter((row) => row.checkStatus === CHECK_STATUS.PENDING_RECOUNT);
  if (type === "adjustment") list = list.filter((row) => row.needsAdjustment);
  const userIds = [...new Set(list.flatMap((row) => [row.firstCountedBy, row.recountedBy, row.reviewedBy]).filter(Boolean))];
  const users = userIds.length
    ? await prisma.admin.findMany({ where: { id: { in: userIds } }, select: { id: true, name: true, nickname: true, phone: true } })
    : [];
  const userNames = new Map(users.map((user) => [user.id, user.name || user.nickname || user.phone || "-"]));
  const checkStatusText = (row) => row.needsAdjustment ? "需调整库存" : ({
    [CHECK_STATUS.UNCOUNTED]: "未盘",
    [CHECK_STATUS.MATCH_PENDING_REVIEW]: "待复核",
    [CHECK_STATUS.PENDING_RECOUNT]: "待复盘",
    [CHECK_STATUS.RECOUNT_MATCH_PENDING_REVIEW]: "复盘待复核",
    [CHECK_STATUS.PENDING_ADJUSTMENT]: "需调整库存",
    [CHECK_STATUS.NEW_BATCH]: "新增批号",
    [CHECK_STATUS.CONFIRMED]: "已确认",
  }[row.checkStatus] || "-");
  const reviewStatusText = (value) => ({ 0: "未复核", 1: "已确认", 2: "未通过" }[Number(value)] || "-");
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("盘点记录");
  sheet.columns = ["商品编号", "商品名称", "条形码", "规格", "单位", "价格", "批号", "系统货位", "盘点货位", "初盘系统数量", "初盘数量", "初盘人", "复盘系统数量", "复盘数量", "复盘人", "差异", "状态", "复核状态", "复核人"].map((header) => ({ header, width: 16 }));
  list.forEach((row) => sheet.addRow([row.product?.productCode || "", row.product?.name || "", row.product?.barcode || "", row.product?.specification || "", row.product?.unit || "", row.price, row.batchNo, row.systemLocationName, row.countLocationName || "", row.systemQty, row.firstCountQty, userNames.get(row.firstCountedBy) || "", row.recountSystemQty, row.recountQty, userNames.get(row.recountedBy) || "", row.difference, checkStatusText(row), reviewStatusText(row.reviewStatus), userNames.get(row.reviewedBy) || ""]));
  const priceColumn = sheet.getColumn(6);
  priceColumn.numFmt = '0.00';
  return { buffer: await workbook.xlsx.writeBuffer(), filename: `${check.checkName}-盘点${type === "adjustment" ? "需调整库存" : type === "recount" ? "待复盘" : "全部"}.xlsx` };
}
