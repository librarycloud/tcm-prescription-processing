import { getUploadConfig, updateUploadConfig, generateUploadStrategy } from '../services/uploadConfigService.js';
import { ok } from '../utils/response.js';

export async function getUploadConfigController(request, reply) {
  const config = await getUploadConfig(request.server.prisma);
  return ok(reply, config);
}

export async function updateUploadConfigController(request, reply) {
  const config = await updateUploadConfig(request.server.prisma, request.body);
  return ok(reply, config);
}

export async function getUploadStrategyController(request, reply) {
  const { category = 'default', filename, mimeType } = request.query;
  const strategy = await generateUploadStrategy(
    request.server.prisma,
    category,
    filename,
    mimeType
  );
  return ok(reply, strategy);
}
