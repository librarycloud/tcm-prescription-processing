import { ok } from '../utils/response.js';
import { AppError } from '../utils/appError.js';
import {
  createPrescription,
  deletePrescriptionAttachment,
  deletePrescription,
  getPrescription,
  getPrescriptionAttachment,
  listPrescriptions,
  uploadPrescriptionAttachment,
  updatePrescription,
} from '../services/prescriptionService.js';

export async function listController(request, reply) { return ok(reply, await listPrescriptions(request.server.prisma, request.user, request.query || {})); }
export async function detailController(request, reply) { return ok(reply, await getPrescription(request.server.prisma, request.user, request.params.id)); }
export async function createController(request, reply) { return ok(reply, await createPrescription(request.server.prisma, request.user, request.body || {}), '创建成功'); }
export async function updateController(request, reply) { return ok(reply, await updatePrescription(request.server.prisma, request.user, request.params.id, request.body || {}), '更新成功'); }
export async function deleteController(request, reply) { return ok(reply, await deletePrescription(request.server.prisma, request.user, request.params.id), '删除成功'); }

export async function uploadAttachmentController(request, reply) {
  const file = await request.file();
  if (!file) throw new AppError('请选择处方文件', 400);
  let buffer;
  try {
    buffer = await file.toBuffer();
  } catch (error) {
    if (error?.code === 'FST_REQ_FILE_TOO_LARGE')
      throw new AppError('处方文件不能超过 5MB', 400);
    throw error;
  }
  if (file.file?.truncated) throw new AppError('处方文件不能超过 5MB', 400);
  return ok(
    reply,
    await uploadPrescriptionAttachment(
      request.server.prisma,
      request.user,
      request.params.id,
      { buffer, filename: file.filename, mimetype: file.mimetype },
    ),
    '处方原件已上传',
  );
}

export async function attachmentController(request, reply) {
  const attachment = await getPrescriptionAttachment(
    request.server.prisma,
    request.user,
    request.params.id,
  );
  return reply
    .header('Content-Type', attachment.mimeType)
    .header(
      'Content-Disposition',
      `inline; filename*=UTF-8''${encodeURIComponent(attachment.originalName)}`,
    )
    .header('Content-Length', attachment.fileSize)
    .header('Cache-Control', 'private, no-store')
    .send(attachment.data);
}

export async function deleteAttachmentController(request, reply) {
  return ok(
    reply,
    await deletePrescriptionAttachment(
      request.server.prisma,
      request.user,
      request.params.id,
    ),
    '处方原件已删除',
  );
}
