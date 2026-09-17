import ExcelJS from "exceljs";
import { AppError } from "../utils/appError.js";
import { PRODUCT_DIFF_OPERATION } from "../constants/productDifference.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";
import { resolveBusinessStoreId } from "./permissionService.js";
import { recordOperation } from "./operationLogService.js";
import { productDifferenceRepository as repository } from "../repositories/productDifferenceRepository.js";
import { productDifferenceInternals } from "./productDifferenceService.js";

const HEADERS = ["商品编号", "商品名称", "规格", "单位", "数量", "零售价"];
const MAX_ROWS = 5000;

function cellValue(cell) {
  const value = cell?.value;
  if (value == null) return "";
  if (typeof value === "object") {
    if (value.result != null) return value.result;
    if (value.text != null) return value.text;
    if (Array.isArray(value.richText))
      return value.richText.map((item) => item.text).join("");
  }
  return value;
}

function trimmed(cell) {
  return String(cellValue(cell) ?? "").trim();
}

function decimal(cell, decimals, label, { signed = false } = {}) {
  const raw = cellValue(cell);
  if (raw === "" || raw == null) return { error: `${label}不能为空` };
  const value = Number(raw);
  if (!Number.isFinite(value)) return { error: `${label}必须是数字` };
  if (!signed && value < 0) return { error: `${label}不能小于 0` };
  const factor = 10 ** decimals;
  const normalized = Math.round(value * factor) / factor;
  if (Math.abs(value - normalized) > 1e-9)
    return { error: `${label}最多 ${decimals} 位小数` };
  return { value: normalized };
}

async function loadWorkbook(buffer) {
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(buffer);
  } catch {
    throw new AppError("Excel 文件无法读取，请使用系统下载的 .xlsx 模板", 400);
  }
  const sheet = workbook.worksheets[0];
  if (!sheet) throw new AppError("Excel 文件没有工作表", 400);
  const headers = HEADERS.map((_, index) =>
    trimmed(sheet.getRow(1).getCell(index + 1)),
  );
  if (HEADERS.some((header, index) => headers[index] !== header))
    throw new AppError(`Excel 表头必须依次为：${HEADERS.join("、")}`, 400);
  return sheet;
}

function parseRows(sheet) {
  if (sheet.actualRowCount - 1 > MAX_ROWS)
    throw new AppError(`一次最多导入 ${MAX_ROWS} 行商品`, 400);
  const rows = [];
  const seenCodes = new Map();
  for (let rowNumber = 2; rowNumber <= sheet.actualRowCount; rowNumber += 1) {
    const row = sheet.getRow(rowNumber);
    const values = HEADERS.map((_, index) => cellValue(row.getCell(index + 1)));
    if (values.every((value) => String(value ?? "").trim() === "")) continue;
    const productCode = trimmed(row.getCell(1));
    const name = trimmed(row.getCell(2));
    const specification = trimmed(row.getCell(3));
    const unit = trimmed(row.getCell(4));
    const quantityResult = decimal(row.getCell(5), 3, "数量", { signed: true });
    const priceResult = decimal(row.getCell(6), 2, "零售价");
    const errors = [];
    if (!productCode) errors.push("商品编号不能为空");
    if (productCode.length > 64) errors.push("商品编号不能超过 64 个字符");
    if (!name) errors.push("商品名称不能为空");
    if (name.length > 120) errors.push("商品名称不能超过 120 个字符");
    if (specification.length > 120) errors.push("规格不能超过 120 个字符");
    if (!unit) errors.push("单位不能为空");
    if (unit.length > 20) errors.push("单位不能超过 20 个字符");
    if (quantityResult.error) errors.push(quantityResult.error);
    if (priceResult.error) errors.push(priceResult.error);
    if (productCode && seenCodes.has(productCode))
      errors.push(`商品编号与第 ${seenCodes.get(productCode)} 行重复`);
    else if (productCode) seenCodes.set(productCode, rowNumber);
    rows.push({
      rowNumber,
      productCode,
      name,
      specification: specification || null,
      unit,
      quantity: quantityResult.value ?? null,
      retailPrice: priceResult.value ?? null,
      errors,
    });
  }
  if (!rows.length) throw new AppError("Excel 中没有可导入的商品数据", 400);
  return rows;
}

async function buildPreview(prisma, storeId, rows, overwriteDifference) {
  const codes = [
    ...new Set(rows.map((row) => row.productCode).filter(Boolean)),
  ];
  const existing = await repository.findProducts(prisma, {
    where: { storeId, productCode: { in: codes } },
  });
  const productMap = new Map(
    existing.map((product) => [product.productCode, product]),
  );
  const list = rows.map((row) => {
    const current = productMap.get(row.productCode);
    const deleted = Boolean(current?.deletedAt);
    return {
      ...row,
      errors: deleted
        ? [
            ...row.errors,
            `商品编号 ${row.productCode} 已删除，请先恢复原商品后再导入`,
          ]
        : row.errors,
      action: deleted ? "RESTORE" : current ? "UPDATE" : "CREATE",
      currentDiffQuantity: current ? Number(current.diffQuantity) : null,
      nextDiffQuantity:
        current && !overwriteDifference
          ? Number(current.diffQuantity)
          : row.quantity,
    };
  });
  return {
    list,
    summary: {
      total: list.length,
      valid: list.filter((row) => !row.errors.length).length,
      invalid: list.filter((row) => row.errors.length).length,
      create: list.filter(
        (row) => !row.errors.length && row.action === "CREATE",
      ).length,
      update: list.filter(
        (row) => !row.errors.length && row.action === "UPDATE",
      ).length,
      restore: list.filter((row) => row.action === "RESTORE").length,
    },
  };
}

export async function productImportTemplate() {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("商品导入");
  sheet.addRow(HEADERS);
  sheet.columns = [
    { key: "productCode", width: 18 },
    { key: "name", width: 24 },
    { key: "specification", width: 20 },
    { key: "unit", width: 12 },
    { key: "quantity", width: 14 },
    { key: "retailPrice", width: 14 },
  ];
  const header = sheet.getRow(1);
  header.font = { bold: true, color: { argb: "FFFFFFFF" } };
  header.fill = {
    type: "pattern",
    pattern: "solid",
    fgColor: { argb: "FF166534" },
  };
  header.alignment = { horizontal: "center", vertical: "middle" };
  sheet.views = [{ state: "frozen", ySplit: 1 }];
  sheet.autoFilter = { from: "A1", to: "F1" };
  sheet.getColumn(5).numFmt = "0.000";
  sheet.getColumn(6).numFmt = "0.00";
  const instructions = workbook.addWorksheet("填写说明");
  instructions.addRows([
    ["字段", "说明"],
    ["商品编号", "必填，同一门店内唯一，已有编号会更新商品资料"],
    ["商品名称", "必填"],
    ["规格", "可选，仅用于区分商品"],
    ["单位", "必填，例如台、盒、个"],
    ["数量", "当前差异数量：正数表示实货多，负数表示实货少，0 表示无差异"],
    ["零售价", "必填，只用于快速区分商品，不参与金额计算"],
  ]);
  instructions.columns = [{ width: 18 }, { width: 72 }];
  instructions.getRow(1).font = { bold: true };
  return {
    buffer: await workbook.xlsx.writeBuffer(),
    filename: "商品导入模板.xlsx",
  };
}

export async function previewProductImport(
  prisma,
  actor,
  storeIdValue,
  buffer,
  overwriteDifference,
) {
  const storeId = await resolveBusinessStoreId(prisma, actor, storeIdValue);
  const sheet = await loadWorkbook(buffer);
  const rows = parseRows(sheet);
  return buildPreview(prisma, storeId, rows, overwriteDifference);
}

export async function importProducts(
  prisma,
  actor,
  storeIdValue,
  buffer,
  overwriteDifference,
) {
  const storeId = await resolveBusinessStoreId(prisma, actor, storeIdValue);
  const sheet = await loadWorkbook(buffer);
  const preview = await buildPreview(
    prisma,
    storeId,
    parseRows(sheet),
    overwriteDifference,
  );
  if (preview.summary.invalid)
    throw new AppError("导入数据存在错误，请重新预览并修正", 400);
  const number = productDifferenceInternals.operationNo("PI");
  const businessDate = productDifferenceInternals.dateOnly(
    new Date().toISOString().slice(0, 10),
  );
  const result = await prisma.$transaction(
    async (tx) => {
      let created = 0;
      let updated = 0;
      let adjusted = 0;
      const existingProducts = await repository.findProducts(tx, {
        where: {
          storeId,
          productCode: { in: preview.list.map((row) => row.productCode) },
        },
      });
      const productMap = new Map(
        existingProducts.map((product) => [product.productCode, product]),
      );
      for (const row of preview.list) {
        const current = productMap.get(row.productCode);
        if (current?.deletedAt)
          throw new AppError(
            `商品编号 ${row.productCode} 已删除，请先恢复原商品后再导入`,
            409,
          );
        if (!current) {
          const product = await repository.createProduct(tx, {
            data: {
              storeId,
              productCode: row.productCode,
              name: row.name,
              specification: row.specification,
              unit: row.unit,
              retailPrice: row.retailPrice,
              diffQuantity: row.quantity,
              status: RECORD_STATUS.ENABLED,
              createdBy: Number(actor.id),
            },
          });
          productMap.set(row.productCode, product);
          if (row.quantity !== 0) {
            await repository.createLog(tx, {
              data: {
                operationNo: number,
                storeId,
                productId: product.id,
                operationType: PRODUCT_DIFF_OPERATION.IMPORT_OPENING,
                changeQuantity: row.quantity,
                balanceAfter: row.quantity,
                businessDate,
                remark: `Excel 导入第 ${row.rowNumber} 行期初差异`,
                createdBy: Number(actor.id),
              },
            });
            adjusted += 1;
          }
          created += 1;
          continue;
        }
        const changeQuantity = overwriteDifference
          ? Math.round((row.quantity - Number(current.diffQuantity)) * 1000) /
            1000
          : 0;
        const product = await repository.updateProduct(tx, {
          where: { id: current.id },
          data: {
            name: row.name,
            specification: row.specification,
            unit: row.unit,
            retailPrice: row.retailPrice,
            updatedBy: Number(actor.id),
            ...(changeQuantity
              ? { diffQuantity: { increment: changeQuantity } }
              : {}),
          },
        });
        if (changeQuantity) {
          await repository.createLog(tx, {
            data: {
              operationNo: number,
              storeId,
              productId: current.id,
              operationType: PRODUCT_DIFF_OPERATION.IMPORT_ADJUSTMENT,
              changeQuantity,
              balanceAfter: product.diffQuantity,
              businessDate,
              remark: `Excel 导入第 ${row.rowNumber} 行调整差异`,
              createdBy: Number(actor.id),
            },
          });
          adjusted += 1;
        }
        updated += 1;
      }
      await recordOperation(tx, actor, {
        module: "product",
        action: "import",
        storeId,
        description: `Excel 导入商品 ${number}：新增 ${created}，更新 ${updated}，调整差异 ${adjusted}`,
      });
      return {
        operationNo: number,
        created,
        updated,
        adjusted,
        total: created + updated,
      };
    },
    { isolationLevel: "Serializable" },
  );
  return result;
}
