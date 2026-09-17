import {
  createRobotConfig,
  deleteRobotConfig,
  getRobotConfig,
  listRobotConfigs,
  resetRobotEventTemplate,
  testRobotConfig,
  updateRobotConfig,
  updateRobotEventConfig
} from '../services/robotConfigService.js';
import {
  getRobotDeliveryLog,
  listRobotDeliveryLogs,
  retryRobotDeliveryLog
} from '../services/robotNotificationService.js';
import { ok } from '../utils/response.js';

export async function listRobotsController(request, reply) { return ok(reply, await listRobotConfigs(request.server.prisma, request.user)); }
export async function robotDetailController(request, reply) { return ok(reply, await getRobotConfig(request.server.prisma, request.user, request.params.id)); }
export async function createRobotController(request, reply) { return ok(reply, await createRobotConfig(request.server.prisma, request.user, request.body || {}), '群机器人已创建'); }
export async function updateRobotController(request, reply) { return ok(reply, await updateRobotConfig(request.server.prisma, request.user, request.params.id, request.body || {}), '群机器人已保存'); }
export async function deleteRobotController(request, reply) { return ok(reply, await deleteRobotConfig(request.server.prisma, request.user, request.params.id), '群机器人已删除'); }
export async function testRobotController(request, reply) { return ok(reply, await testRobotConfig(request.server.prisma, request.user, request.params.id, request.body || {}), '测试消息已发送'); }
export async function updateRobotEventController(request, reply) { return ok(reply, await updateRobotEventConfig(request.server.prisma, request.user, request.params.id, request.params.eventCode, request.body || {}), '事件配置已保存'); }
export async function resetRobotEventController(request, reply) { return ok(reply, await resetRobotEventTemplate(request.server.prisma, request.user, request.params.id, request.params.eventCode), '模板已恢复'); }
export async function listRobotLogsController(request, reply) { return ok(reply, await listRobotDeliveryLogs(request.server.prisma, request.user, request.query || {})); }
export async function robotLogDetailController(request, reply) { return ok(reply, await getRobotDeliveryLog(request.server.prisma, request.user, request.params.id)); }
export async function retryRobotLogController(request, reply) { return ok(reply, await retryRobotDeliveryLog(request.server.prisma, request.user, request.params.id), '通知已重新排队'); }
