import { businessScope } from "./permissionService.js";
import { toPositiveInt } from "../utils/validators.js";
import ExcelJS from "exceljs";
import { Prisma } from "@prisma/client";
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
    retailPrice: Number(product.retailPrice || 0),
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

const SUPPORTED_SORT_FIELDS = new Set([
  "retailPrice",
  "batchCount",
  "totalQuantity",
  "productCode",
  "categoryName",
  "category",
  "categoryCode",
  "name",
  "e6ModifiedAt",
]);

function buildE6PharmacySqlWhere({ scope, keyword, categoryCode, expiryBefore, includeZero }) {
  const filters = [];
  if (scope.storeId) {
    filters.push(Prisma.sql`i.store_id = ${scope.storeId}`);
  }
  if (!includeZero) {
    filters.push(Prisma.sql`i.quantity > 0`);
  }
  if (expiryBefore) {
    filters.push(Prisma.sql`i.expiry_date < ${expiryBefore}`);
  }
  if (categoryCode) {
    filters.push(Prisma.sql`p.category_code = ${categoryCode}`);
  }
  if (keyword) {
    const escaped = keyword.replace(/[%_\\]/g, "\\$&");
    const value = `%${escaped}%`;
    filters.push(Prisma.sql`(p.product_code LIKE ${value} OR p.name LIKE ${value} OR p.barcode LIKE ${value})`);
  }
  return filters.length ? Prisma.join(filters, " AND ") : Prisma.sql`1=1`;
}

export async function listE6PharmacyProducts(prisma, actor, query = {}) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const scope = businessScope(actor, query.storeId);
  const keyword = String(query.keyword || "").trim();
  const categoryCode = String(query.categoryCode || "").trim();
  const expiryWithinMonths = query.expiryWithinMonths === undefined || query.expiryWithinMonths === ""
    ? null
    : toPositiveInt(query.expiryWithinMonths, 0);
  const expiryBefore = expiryWithinMonths
    ? (() => {
      const date = new Date();
      const day = date.getDate();
      date.setHours(0, 0, 0, 0);
      // Avoid Aug 31 + 3 months overflowing into December; clamp to the target month's last day.
      date.setDate(1);
      date.setMonth(date.getMonth() + expiryWithinMonths);
      date.setDate(Math.min(day, new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()));
      return date;
    })()
    : null;
  const includeZero = Boolean(keyword || query.includeZero === true || query.includeZero === "true");
  const storeFilter = scope.storeId ? { storeId: scope.storeId } : {};
  const inventoryWhere = {
    ...(includeZero ? {} : { quantity: { gt: 0 } }),
    ...storeFilter,
    ...(expiryBefore ? { expiryDate: { lt: expiryBefore } } : {}),
  };
  const where = {
    inventories: { some: inventoryWhere },
    ...(categoryCode ? { categoryCode } : {}),
  };

  if (keyword) {
    where.OR = [
      { productCode: { contains: keyword } },
      { name: { contains: keyword } },
      { barcode: { contains: keyword } },
    ];
  }

  const sortByRaw = String(query.sortBy || "").trim();
  const sortBy = SUPPORTED_SORT_FIELDS.has(sortByRaw) ? sortByRaw : null;
  const sortOrder = String(query.sortOrder || "").toLowerCase() === "desc" ? "desc" : "asc";

  const categoryMappingQuery = prisma.e6PharmacyCategoryMapping?.findMany
    ? prisma.e6PharmacyCategoryMapping.findMany({ orderBy: [{ categoryCode: "asc" }] })
    : Promise.resolve([]);

  const isAggregateOrJoinedSort = sortBy && ["batchCount", "totalQuantity", "categoryName", "category", "categoryCode"].includes(sortBy);

  let list = [];
  let total = 0;
  let categoryMappings = [];

  if (isAggregateOrJoinedSort) {
    if (typeof prisma.$queryRaw === "function") {
      const offset = (page - 1) * pageSize;
      const whereSql = buildE6PharmacySqlWhere({ scope, keyword, categoryCode, expiryBefore, includeZero });
      let orderSql;
      if (sortBy === "batchCount") {
        orderSql = sortOrder === "desc"
          ? Prisma.sql`COUNT(i.id) DESC, p.product_code ASC, p.id ASC`
          : Prisma.sql`COUNT(i.id) ASC, p.product_code ASC, p.id ASC`;
      } else if (sortBy === "totalQuantity") {
        orderSql = sortOrder === "desc"
          ? Prisma.sql`COALESCE(SUM(i.quantity), 0) DESC, p.product_code ASC, p.id ASC`
          : Prisma.sql`COALESCE(SUM(i.quantity), 0) ASC, p.product_code ASC, p.id ASC`;
      } else {
        orderSql = sortOrder === "desc"
          ? Prisma.sql`COALESCE(m.category_name, p.category, '') DESC, p.product_code ASC, p.id ASC`
          : Prisma.sql`COALESCE(m.category_name, p.category, '') ASC, p.product_code ASC, p.id ASC`;
      }

      let idRows;
      [total, categoryMappings, idRows] = await Promise.all([
        prisma.e6PharmacyProduct.count({ where }),
        categoryMappingQuery,
        prisma.$queryRaw(Prisma.sql`
          SELECT p.id
          FROM e6_pharmacy_products p
          INNER JOIN e6_pharmacy_inventory_batches i ON i.product_id = p.id
          LEFT JOIN e6_pharmacy_category_mappings m ON m.category_code = p.category_code
          WHERE ${whereSql}
          GROUP BY p.id, m.category_name, p.category, p.product_code
          ORDER BY ${orderSql}
          LIMIT ${pageSize} OFFSET ${offset}
        `),
      ]);

      const pageIds = (idRows || []).map((row) => Number(row.id));
      let rawProducts = [];
      if (pageIds.length > 0) {
        rawProducts = await prisma.e6PharmacyProduct.findMany({
          where: { id: { in: pageIds } },
          include: {
            inventories: {
              where: inventoryWhere,
              include: { store: { select: { id: true, name: true, code: true } } },
              orderBy: [{ expiryDate: "asc" }, { batchNo: "asc" }],
            },
          },
        });
      }
      const productMap = new Map(rawProducts.map((p) => [p.id, p]));
      list = pageIds.map((id) => productMap.get(id)).filter(Boolean);
    } else {
      let allProducts;
      [allProducts, total, categoryMappings] = await Promise.all([
        prisma.e6PharmacyProduct.findMany({
          where,
          include: {
            inventories: {
              where: inventoryWhere,
              include: { store: { select: { id: true, name: true, code: true } } },
              orderBy: [{ expiryDate: "asc" }, { batchNo: "asc" }],
            },
          },
        }),
        prisma.e6PharmacyProduct.count({ where }),
        categoryMappingQuery,
      ]);

      const tempCategoryMap = new Map(categoryMappings.map((item) => [item.categoryCode, item.categoryName]));
      const sorted = [...allProducts].sort((a, b) => {
        if (sortBy === "batchCount") {
          const countA = a.inventories?.length || 0;
          const countB = b.inventories?.length || 0;
          const diff = countA - countB;
          if (diff !== 0) return sortOrder === "desc" ? -diff : diff;
        } else if (sortBy === "totalQuantity") {
          const qtyA = (a.inventories || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0);
          const qtyB = (b.inventories || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0);
          const diff = qtyA - qtyB;
          if (diff !== 0) return sortOrder === "desc" ? -diff : diff;
        } else {
          const catA = tempCategoryMap.get(a.categoryCode) || a.category || "";
          const catB = tempCategoryMap.get(b.categoryCode) || b.category || "";
          const cmp = String(catA).localeCompare(String(catB), "zh-CN");
          if (cmp !== 0) return sortOrder === "desc" ? -cmp : cmp;
        }
        return String(a.productCode || "").localeCompare(String(b.productCode || ""));
      });
      list = sorted.slice((page - 1) * pageSize, page * pageSize);
    }
  } else {
    let orderBy;
    if (sortBy === "retailPrice") {
      orderBy = [{ retailPrice: sortOrder }, { productCode: "asc" }, { id: "asc" }];
    } else if (sortBy === "productCode") {
      orderBy = [{ productCode: sortOrder }, { id: "asc" }];
    } else if (sortBy === "name") {
      orderBy = [{ name: sortOrder }, { productCode: "asc" }, { id: "asc" }];
    } else if (sortBy === "e6ModifiedAt") {
      orderBy = [{ e6ModifiedAt: sortOrder }, { productCode: "asc" }, { id: "asc" }];
    } else {
      orderBy = [{ name: "asc" }, { productCode: "asc" }];
    }

    [list, total, categoryMappings] = await Promise.all([
      prisma.e6PharmacyProduct.findMany({
        where,
        include: {
          inventories: {
            where: inventoryWhere,
            include: { store: { select: { id: true, name: true, code: true } } },
            orderBy: [{ expiryDate: "asc" }, { batchNo: "asc" }],
          },
        },
        orderBy,
        skip: (page - 1) * pageSize,
        take: pageSize,
      }),
      prisma.e6PharmacyProduct.count({ where }),
      categoryMappingQuery,
    ]);
  }

  const categoryMap = new Map(categoryMappings.map((item) => [item.categoryCode, item.categoryName]));

  return {
    list: list.map((product) => ({
      ...normalizeProduct(product),
      categoryName: categoryMap.get(product.categoryCode) || product.category || "-",
    })),
    pagination: {
      page,
      pageSize,
      total,
      pages: Math.ceil(total / pageSize),
    },
  };
}
