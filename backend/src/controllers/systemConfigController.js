import { getSystemConfigs, updateSystemConfigs } from '../services/systemConfigService.js';
import { ok } from '../utils/response.js';

export async function getConfigsController(request, reply) {
  const data = await getSystemConfigs(request.server.prisma);
  return ok(reply, data, '获取成功');
}

export async function updateConfigsController(request, reply) {
  const data = await updateSystemConfigs(request.server.prisma, request.body);
  return ok(reply, data, '更新成功');
}
