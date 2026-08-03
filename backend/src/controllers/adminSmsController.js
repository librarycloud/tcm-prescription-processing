import {
  getPackageNotifications,
  sendPackageNotification,
  sendTestSms
} from '../services/notificationService.js';
import { getSmsSettings, updateSmsConfig, updateSmsTemplate } from '../services/smsSettingsService.js';
import { ok } from '../utils/response.js';

export async function settingsController(request, reply) {
  return ok(reply, await getSmsSettings(request.server.prisma));
}

export async function updateProviderController(request, reply) {
  const data = await updateSmsConfig(
    request.server.prisma,
    request.user.id,
    request.params.provider,
    request.body || {}
  );
  return ok(reply, data, '短信供应商配置已保存');
}

export async function updateTemplateController(request, reply) {
  const data = await updateSmsTemplate(
    request.server.prisma,
    request.user.id,
    request.params.id,
    request.body || {}
  );
  return ok(reply, data, '短信模板已保存');
}

export async function testSmsController(request, reply) {
  const data = await sendTestSms(request.server.prisma, request.user.id, request.body || {});
  return ok(reply, data, '测试短信已发送');
}

export async function packageNotificationsController(request, reply) {
  return ok(
    reply,
    await getPackageNotifications(request.server.prisma, request.user, request.params.id)
  );
}

export async function sendPackageNotificationController(request, reply) {
  const data = await sendPackageNotification(
    request.server.prisma,
    request.user,
    request.params.id,
    request.body || {}
  );
  return ok(reply, data, '取货通知已发送');
}
