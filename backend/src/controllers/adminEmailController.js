import { getEmailSettings, updateEmailConfig, updateEmailTemplate } from '../services/emailSettingsService.js';
import { sendSmtpEmail } from '../providers/email/smtpProvider.js';
import { decryptSetting } from '../utils/settingsEncryption.js';
import { AppError } from '../utils/appError.js';
import { ensureEmailDefaults } from '../services/emailSettingsService.js';
import { ok } from '../utils/response.js';

export async function emailSettingsController(request, reply) {
  return ok(reply, await getEmailSettings(request.server.prisma));
}

export async function updateEmailConfigController(request, reply) {
  return ok(
    reply,
    await updateEmailConfig(request.server.prisma, request.user.id, request.body || {}),
    'SMTP 配置已保存'
  );
}

export async function updateEmailTemplateController(request, reply) {
  return ok(
    reply,
    await updateEmailTemplate(
      request.server.prisma,
      request.user.id,
      request.params.id,
      request.body || {}
    ),
    '邮件模板已保存'
  );
}

export async function testEmailController(request, reply) {
  const email = String(request.body?.email || '').trim();
  if (!email) throw new AppError('请输入测试邮箱', 400);
  await ensureEmailDefaults(request.server.prisma);
  const config = await request.server.prisma.emailConfig.findUnique({ where: { configKey: 'default' } });
  if (!config?.enabled || !config.passwordEncrypted) throw new AppError('请先启用并完整配置 SMTP', 400);
  await sendSmtpEmail(config, decryptSetting(config.passwordEncrypted), {
    to: email,
    subject: '取货系统 SMTP 测试邮件',
    text: '这是一封 SMTP 测试邮件。'
  });
  return ok(reply, { email }, '测试邮件已发送');
}
