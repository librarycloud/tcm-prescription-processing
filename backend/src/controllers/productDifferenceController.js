import { AppError } from "../utils/appError.js";
import { ok } from "../utils/response.js";
import {
  createProduct,
  getProductDifferenceStats,
  listProductDiffLogs,
  listProducts,
  listProductStores,
  registerProductDifference,
  reverseProductDiffLog,
  updateProduct,
  writeOffProductDifference,
} from "../services/productDifferenceService.js";
import {
  importProducts,
  previewProductImport,
  productImportTemplate,
} from "../services/productImportService.js";

export async function listProductsController(request, reply) {
  return ok(reply, await listProducts(request.server.prisma, request.user, request.query || {}));
}

export async function productStoresController(request, reply) {
  return ok(reply, await listProductStores(request.server.prisma, request.user));
}

export async function createProductController(request, reply) {
  return ok(reply, await createProduct(request.server.prisma, request.user, request.body || {}), "商品已新增");
}

export async function updateProductController(request, reply) {
  return ok(reply, await updateProduct(request.server.prisma, request.user, request.params.id, request.body || {}), "商品已更新");
}

export async function listProductDiffLogsController(request, reply) {
  return ok(reply, await listProductDiffLogs(request.server.prisma, request.user, request.query || {}));
}

export async function productDiffStatsController(request, reply) {
  return ok(reply, await getProductDifferenceStats(request.server.prisma, request.user, request.query || {}));
}

export async function registerProductDiffController(request, reply) {
  return ok(reply, await registerProductDifference(request.server.prisma, request.user, request.body || {}), "差异已登记");
}

export async function writeOffProductDiffController(request, reply) {
  return ok(reply, await writeOffProductDifference(request.server.prisma, request.user, request.body || {}), "销账成功");
}

export async function reverseProductDiffController(request, reply) {
  return ok(reply, await reverseProductDiffLog(request.server.prisma, request.user, request.params.id, request.body || {}), "冲销成功");
}

async function importFile(request) {
  const file = await request.file();
  if (!file) throw new AppError("请选择 Excel 文件", 400);
  if (!String(file.filename || "").toLowerCase().endsWith(".xlsx"))
    throw new AppError("只支持 .xlsx 文件", 400);
  const buffer = await file.toBuffer();
  return {
    buffer,
    storeId: file.fields?.storeId?.value,
    overwriteDifference: String(file.fields?.overwriteDifference?.value || "") === "1",
  };
}

export async function productImportTemplateController(_request, reply) {
  const { buffer, filename } = await productImportTemplate();
  return reply
    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    .header("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`)
    .send(buffer);
}

export async function previewProductImportController(request, reply) {
  const file = await importFile(request);
  return ok(reply, await previewProductImport(request.server.prisma, request.user, file.storeId, file.buffer, file.overwriteDifference));
}

export async function importProductsController(request, reply) {
  const file = await importFile(request);
  return ok(reply, await importProducts(request.server.prisma, request.user, file.storeId, file.buffer, file.overwriteDifference), "商品导入完成");
}
