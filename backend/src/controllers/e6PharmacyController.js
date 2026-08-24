import { ok } from "../utils/response.js";
import { AppError } from "../utils/appError.js";
import {
  e6PharmacyBarcodeTemplate,
  importE6PharmacyBarcodes,
  listE6PharmacyProducts,
} from "../services/e6PharmacyService.js";
import {
  deleteE6PharmacyCategoryMapping,
  listE6PharmacyCategoryMappings,
  saveE6PharmacyCategoryMapping,
} from "../services/e6PharmacyCategoryService.js";

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

export async function listE6PharmacyCategoryMappingsController(request, reply) {
  return ok(reply, await listE6PharmacyCategoryMappings(
    request.server.prisma,
    request.query?.includeDisabled === "1",
  ));
}

export async function saveE6PharmacyCategoryMappingController(request, reply) {
  return ok(reply, await saveE6PharmacyCategoryMapping(
    request.server.prisma,
    request.params.id,
    request.body || {},
    request.user,
  ), "保存成功");
}

export async function createE6PharmacyCategoryMappingController(request, reply) {
  return ok(reply, await saveE6PharmacyCategoryMapping(
    request.server.prisma,
    null,
    request.body || {},
    request.user,
  ), "创建成功");
}

export async function deleteE6PharmacyCategoryMappingController(request, reply) {
  return ok(reply, await deleteE6PharmacyCategoryMapping(
    request.server.prisma,
    request.params.id,
    request.user,
  ), "删除成功");
}
