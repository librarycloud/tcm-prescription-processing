import {
  assignHerbLocation,
  exportHerbLocationMoveTemplate,
  exportHerbLocations,
  getHerbLocationLayout,
  importHerbLocations,
  importHerbLocationMoves,
  listHerbLocations,
  listHerbLocationStores,
  removeHerbLocationAssignment,
  updateHerb,
  updateHerbLocationAssignment,
  updateHerbLocationLayout,
} from "../services/herbLocationService.js";
import { ok } from "../utils/response.js";
import { AppError } from "../utils/appError.js";

export async function listHerbLocationStoresController(request, reply) {
  return ok(reply, await listHerbLocationStores(request.server.prisma, request.user));
}

export async function listHerbLocationsController(request, reply) {
  return ok(reply, await listHerbLocations(request.server.prisma, request.user, request.query || {}));
}

export async function getHerbLocationLayoutController(request, reply) {
  return ok(reply, await getHerbLocationLayout(request.server.prisma, request.user, request.query || {}));
}

export async function assignHerbLocationController(request, reply) {
  return ok(reply, await assignHerbLocation(request.server.prisma, request.user, request.body || {}), "斗谱已保存");
}

export async function removeHerbLocationAssignmentController(request, reply) {
  return ok(reply, await removeHerbLocationAssignment(request.server.prisma, request.user, request.params.id), "药材已移除");
}

export async function updateHerbLocationLayoutController(request, reply) {
  return ok(reply, await updateHerbLocationLayout(request.server.prisma, request.user, request.body || {}), "斗柜布局已更新");
}

export async function updateHerbController(request, reply) {
  return ok(reply, await updateHerb(request.server.prisma, request.user, request.params.id, request.body || {}), "药材已更新");
}

export async function updateHerbLocationAssignmentController(request, reply) {
  return ok(
    reply,
    await updateHerbLocationAssignment(request.server.prisma, request.user, request.params.id, request.body || {}),
    "药材位置已更新",
  );
}

async function sendWorkbook(request, reply, template) {
  const { buffer, filename } = await exportHerbLocations(request.server.prisma, request.user, request.query || {}, template);
  reply
    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    .header("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`)
    .send(buffer);
}

export async function exportHerbLocationsController(request, reply) {
  return sendWorkbook(request, reply, false);
}

export async function herbLocationTemplateController(request, reply) {
  return sendWorkbook(request, reply, true);
}

export async function herbLocationMoveTemplateController(request, reply) {
  const { buffer, filename } = await exportHerbLocationMoveTemplate(
    request.server.prisma,
    request.user,
    request.query || {},
  );
  reply
    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    .header("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`)
    .send(buffer);
}

export async function importHerbLocationsController(request, reply) {
  const file = await request.file();
  if (!file) throw new AppError("请选择 Excel 文件", 400);
  const buffer = await file.toBuffer();
  return ok(
    reply,
    await importHerbLocations(
      request.server.prisma,
      request.user,
      file.fields?.storeId?.value,
      { buffer },
    ),
    "斗谱导入完成",
  );
}

export async function importHerbLocationMovesController(request, reply) {
  const file = await request.file();
  if (!file) throw new AppError("请选择 Excel 文件", 400);
  const buffer = await file.toBuffer();
  return ok(
    reply,
    await importHerbLocationMoves(
      request.server.prisma,
      request.user,
      file.fields?.storeId?.value,
      { buffer },
    ),
    "斗谱位置批量修改完成",
  );
}
