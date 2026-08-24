import { businessScope } from "./permissionService.js";
import { toPositiveInt } from "../utils/validators.js";
import ExcelJS from "exceljs";
import { AppError } from "../utils/appError.js";

const BARCODE_HEADERS = ["商品编号", "条形码"];
const BARCODE_MAX_ROWS = 10000;

function cellText(cell) {
  const value = cell?.value;
  if (value == null) return "";
  if (typeof value === "object") {
    if (value.result != null) return value.result;
    if (value.text != null) return value.text;
    if (Array.isArray(value.richText)) return value.richText.map((item) => item.text).join("");
  }
  return value;
}

function trimmedCell(cell) {
  return String(cellText(cell) ?? "").replace(/^\uFEFF/, "").trim();
}

async function loadBarcodeSheet(buffer) {
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(buffer);
  } catch {
    throw new AppError("Excel 文件无法读取，请使用系统下载的 .xlsx 模板", 400);
  }
  const sheet = workbook.worksheets[0];
  if (!sheet) throw new AppError("Excel 文件没有工作表", 400);
  const headers = BARCODE_HEADERS.map((_, index) => trimmedCell(sheet.getRow(1).getCell(index + 1)));
  if (BARCODE_HEADERS.some((header, index) => headers[index] !== header)) {
    throw new AppError(`Excel 表头必须依次为：${BARCODE_HEADERS.join("、")}`, 400);
  }
  return sheet;
}

function parseBarcodeRows(sheet) {
  if (sheet.actualRowCount - 1 > BARCODE_MAX_ROWS)
    throw new AppError(`一次最多导入 ${BARCODE_MAX_ROWS} 行条形码`, 400);
  const seen = new Map();
  const rows = [];
  for (let rowNumber = 2; rowNumber <= sheet.actualRowCount; rowNumber += 1) {
    const row = sheet.getRow(rowNumber);
    const productCode = trimmedCell(row.getCell(1));
    const barcode = trimmedCell(row.getCell(2));
    if (!productCode && !barcode) continue;
    const errors = [];
    if (!productCode) errors.push("商品编号不能为空");
    if (!barcode) errors.push("条形码不能为空");
    if (productCode.length > 64) errors.push("商品编号不能超过 64 个字符");
    if (barcode.length > 64) errors.push("条形码不能超过 64 个字符");
    if (productCode && seen.has(productCode)) errors.push(`商品编号与第 ${seen.get(productCode)} 行重复`);
    else if (productCode) seen.set(productCode, rowNumber);
    rows.push({ rowNumber, productCode, barcode, errors });
  }
  if (!rows.length) throw new AppError("Excel 中没有可导入的条形码数据", 400);
  return rows;
}

export async function e6PharmacyBarcodeTemplate() {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("条形码");
  sheet.addRow(BARCODE_HEADERS);
  sheet.columns = [
    { key: "productCode", width: 20 },
    { key: "barcode", width: 24 },
  ];
  sheet.getColumn(2).numFmt = "@";
  const header = sheet.getRow(1);
  header.font = { bold: true, color: { argb: "FFFFFFFF" } };
  header.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FF166534" } };
  header.alignment = { horizontal: "center", vertical: "middle" };
  sheet.views = [{ state: "frozen", ySplit: 1 }];
  sheet.autoFilter = { from: "A1", to: "B1" };
  const instructions = workbook.addWorksheet("填写说明");
  instructions.addRows([
    ["字段", "说明"],
    ["商品编号", "必填，按 E6 商品编号匹配"],
    ["条形码", "必填，只补充服务器中为空的条形码；已有条形码会跳过，不会覆盖"],
  ]);
  instructions.columns = [{ width: 18 }, { width: 88 }];
  instructions.getRow(1).font = { bold: true };
  return { buffer: await workbook.xlsx.writeBuffer(), filename: "E6药店条形码模板.xlsx" };
}

export async function importE6PharmacyBarcodes(prisma, buffer) {
  const rows = parseBarcodeRows(await loadBarcodeSheet(buffer));
  const validRows = rows.filter((row) => !row.errors.length);
  const codes = [...new Set(validRows.map((row) => row.productCode))];
  const products = await prisma.e6PharmacyProduct.findMany({ where: { productCode: { in: codes } } });
  const productMap = new Map(products.map((product) => [product.productCode, product]));
  const result = { total: rows.length, updated: 0, skippedExisting: 0, notFound: 0, invalid: rows.filter((row) => row.errors.length).length };
  const details = [];
  await prisma.$transaction(async (tx) => {
    for (const row of validRows) {
      const product = productMap.get(row.productCode);
      if (!product) {
        result.notFound += 1;
        details.push({ rowNumber: row.rowNumber, productCode: row.productCode, status: "notFound" });
        continue;
      }
      if (String(product.barcode || "").trim()) {
        result.skippedExisting += 1;
        details.push({ rowNumber: row.rowNumber, productCode: row.productCode, status: "skippedExisting" });
        continue;
      }
      const updated = await tx.e6PharmacyProduct.updateMany({
        where: { id: product.id, OR: [{ barcode: null }, { barcode: "" }] },
        data: { barcode: row.barcode },
      });
      if (updated.count) {
        result.updated += 1;
        details.push({ rowNumber: row.rowNumber, productCode: row.productCode, status: "updated" });
      } else {
        result.skippedExisting += 1;
        details.push({ rowNumber: row.rowNumber, productCode: row.productCode, status: "skippedExisting" });
      }
    }
  });
  return { ...result, details };
}

function normalizeBatch(batch) {
  return {
    id: batch.id,
    batchNo: batch.batchNo || "-",
    locationName: batch.locationName || "-",
    productionDate: batch.productionDate,
    expiryDate: batch.expiryDate,
    inboundDate: batch.inboundDate,
    quantity: Number(batch.quantity || 0),
    amount: Number(batch.amount || 0),
    receivedAt: batch.receivedAt,
    updatedAt: batch.updatedAt,
    store: batch.store,
  };
}
function normalizeProduct(product) {
  const inventories = (product.inventories || []).map(normalizeBatch);
  const stores = [...new Map(
    (product.inventories || [])
      .map((item) => item.store)
      .filter(Boolean)
      .map((store) => [store.id, store]),
  ).values()];
  return {
    id: product.id,
    productCode: product.productCode,
    name: product.name,
    category: product.category,
    categoryCode: product.categoryCode,
    barcode: product.barcode,
    specification: product.specification,
    dosageForm: product.dosageForm,
    manufacturer: product.manufacturer,
    categoryAttribute: product.categoryAttribute,
    unit: product.unit,
    e6CreatedAt: product.e6CreatedAt,
    e6ModifiedAt: product.e6ModifiedAt,
    lastInventorySeenAt: product.lastInventorySeenAt,
    totalQuantity: inventories.reduce((sum, item) => sum + item.quantity, 0),
    batchCount: inventories.length,
    inventories,
    store: product.store || stores[0] || null,
    stores,
  };
}

export async function listE6PharmacyProducts(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const scope = businessScope(actor, query.storeId);
  const keyword = String(query.keyword || "").trim();
  const expiryWithinMonths = query.expiryWithinMonths === undefined || query.expiryWithinMonths === ""
    ? null
    : toPositiveInt(query.expiryWithinMonths, 0);
  const expiryBefore = expiryWithinMonths
    ? (() => {
      const date = new Date();
      date.setHours(0, 0, 0, 0);
      date.setMonth(date.getMonth() + expiryWithinMonths);
      return date;
    })()
    : null;
  const inventoryWhere = {
    quantity: { gt: 0 },
    ...(scope.storeId ? { storeId: scope.storeId } : {}),
    ...(expiryBefore ? { expiryDate: { lt: expiryBefore } } : {}),
  };
  const where = {
    inventories: { some: inventoryWhere },
  };

  if (keyword) {
    where.OR = [
      { productCode: { contains: keyword } },
      { name: { contains: keyword } },
      { barcode: { contains: keyword } },
    ];
  }

  const [list, total] = await Promise.all([
    prisma.e6PharmacyProduct.findMany({
      where,
      include: {
        inventories: {
          where: inventoryWhere,
          include: { store: { select: { id: true, name: true, code: true } } },
          orderBy: [{ expiryDate: "asc" }, { batchNo: "asc" }],
        },
      },
      orderBy: [{ name: "asc" }, { productCode: "asc" }],
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.e6PharmacyProduct.count({ where }),
  ]);

  return {
    list: list.map(normalizeProduct),
    pagination: {
      page,
      pageSize,
      total,
      pages: Math.ceil(total / pageSize),
    },
  };
}
