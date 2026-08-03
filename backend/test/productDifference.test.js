import assert from "node:assert/strict";
import test from "node:test";
import ExcelJS from "exceljs";
import { PRODUCT_DIFF_OPERATION } from "../src/constants/productDifference.js";
import {
  productDifferenceInternals,
  signedRegisterQuantity,
} from "../src/services/productDifferenceService.js";
import { productImportTemplate } from "../src/services/productImportService.js";

test("product difference registration derives the sign from the business type", () => {
  assert.equal(
    signedRegisterQuantity(PRODUCT_DIFF_OPERATION.PRE_RECEIPT, 10),
    10,
  );
  assert.equal(
    signedRegisterQuantity(PRODUCT_DIFF_OPERATION.PRE_SHIPMENT, 10),
    -10,
  );
  assert.throws(
    () => signedRegisterQuantity(PRODUCT_DIFF_OPERATION.WRITE_OFF_RECEIPT, 10),
    /差异类型不正确/,
  );
});

test("product difference quantities use three decimal places and reject zero", () => {
  assert.equal(productDifferenceInternals.quantity("1.2344"), 1.234);
  assert.equal(productDifferenceInternals.quantity("1.2346"), 1.235);
  assert.throws(() => productDifferenceInternals.quantity(0), /必须大于 0/);
  assert.throws(() => productDifferenceInternals.quantity(-1), /必须大于 0/);
});

test("product retail prices are normalized without contributing to quantity", () => {
  assert.equal(productDifferenceInternals.money("19.995"), 20);
  assert.equal(productDifferenceInternals.money(0), 0);
  assert.throws(() => productDifferenceInternals.money(-0.01), /零售价格式不正确/);
});

test("product difference operation numbers stay short and recognizable", () => {
  const number = productDifferenceInternals.operationNo();
  assert.match(number, /^PD\d{6}[A-Z0-9]{4}$/);
  assert.equal(number.length, 12);
});

test("product import template exposes the agreed six columns", async () => {
  const { buffer, filename } = await productImportTemplate();
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(buffer);
  const headers = workbook.worksheets[0].getRow(1).values.slice(1);
  assert.equal(filename, "商品导入模板.xlsx");
  assert.deepEqual(headers, ["商品编号", "商品名称", "规格", "单位", "数量", "零售价"]);
});
