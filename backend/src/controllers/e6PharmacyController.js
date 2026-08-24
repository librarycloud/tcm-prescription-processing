import { ok } from "../utils/response.js";
import { AppError } from "../utils/appError.js";
import {
  e6PharmacyBarcodeTemplate,
  importE6PharmacyBarcodes,
  listE6PharmacyProducts,
} from "../services/e6PharmacyService.js";

export async function listE6PharmacyProductsController(request, reply) {
  return ok(
    reply,
    await listE6PharmacyProducts(
      request.server.prisma,
      request.user,
      request.query || {},
    ),
  );
}

export async function e6PharmacyBarcodeTemplateController(_request, reply) {
  const { buffer, filename } = await e6PharmacyBarcodeTemplate();
  return reply
    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    .header("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`)
    .send(buffer);
}

export async function importE6PharmacyBarcodesController(request, reply) {
  const file = await request.file();
  if (!file) throw new AppError("请选择 Excel 文件", 400);
  if (!String(file.filename || "").toLowerCase().endsWith(".xlsx"))
    throw new AppError("只支持 .xlsx 文件", 400);
  return ok(reply, await importE6PharmacyBarcodes(request.server.prisma, await file.toBuffer()), "条形码导入完成");
}
