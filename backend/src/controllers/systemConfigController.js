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

export async function getPublicConfigsController(request, reply) {
  const data = await getSystemConfigs(request.server.prisma);
  return ok(reply, {
    enable_captcha: data.enable_captcha,
    geetest_captcha_id: data.geetest_captcha_id
  }, '获取成功');
}
