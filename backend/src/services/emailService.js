import bcrypt from 'bcrypt';
import { randomInt } from 'node:crypto';
import { sendSmtpEmail } from '../providers/email/smtpProvider.js';
import { AppError } from '../utils/appError.js';
import { decryptSetting } from '../utils/settingsEncryption.js';
import { ensureEmailDefaults } from './emailSettingsService.js';
import { renderEmailTemplate } from './emailTemplateService.js';

function validateEmail(value) {
  const email = String(value || '').trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new AppError('邮箱格式不正确', 400);
  return email;
}

async function emailResources(prisma, scene) {
  await ensureEmailDefaults(prisma);
  const [config, template] = await Promise.all([
    prisma.emailConfig.findUnique({ where: { configKey: 'default' } }),
    prisma.emailTemplate.findUnique({ where: { scene } })
  ]);
  if (!config?.enabled) throw new AppError('邮件服务尚未启用', 400);
  if (!template?.enabled) throw new AppError('当前邮件模板尚未启用', 400);
  if (!config.passwordEncrypted) throw new AppError('SMTP 密码尚未配置', 400);
  return { config, template, password: decryptSetting(config.passwordEncrypted) };
}

export async function sendVerificationCode(prisma, userId, emailValue) {
  const email = validateEmail(emailValue);
  const current = await prisma.user.findUnique({ where: { id: Number(userId) } });
  if (!current) throw new AppError('用户不存在', 404);
  const owner = await prisma.user.findFirst({ where: { email, id: { not: current.id } }, select: { id: true } });
  if (owner) throw new AppError('该邮箱已被其他用户绑定', 409);
  const recent = await prisma.emailVerificationCode.findFirst({
    where: { userId: current.id, email, createdAt: { gte: new Date(Date.now() - 60_000) } },
    orderBy: { createdAt: 'desc' }
  });
  if (recent) throw new AppError('验证码发送过于频繁，请稍后再试', 429);

  const { config, template, password } = await emailResources(prisma, 'verification');
  const code = String(randomInt(100000, 1000000));
  const rendered = renderEmailTemplate(template, { code, expiresMinutes: 10 });
  await sendSmtpEmail(config, password, { to: email, subject: rendered.subject, text: rendered.content });
  await prisma.emailVerificationCode.create({
    data: {
      userId: current.id,
      email,
      codeHash: await bcrypt.hash(code, 10),
      expiresAt: new Date(Date.now() + 10 * 60_000)
    }
  });
  return { email, expiresIn: 600 };
}

export async function verifyEmailCode(prisma, userId, emailValue, codeValue, publicUser) {
  const email = validateEmail(emailValue);
  const code = String(codeValue || '').trim();
  if (!/^\d{6}$/.test(code)) throw new AppError('验证码格式不正确', 400);
  const record = await prisma.emailVerificationCode.findFirst({
    where: { userId: Number(userId), email, usedAt: null, expiresAt: { gt: new Date() } },
    orderBy: { createdAt: 'desc' }
  });
  if (!record || record.attempts >= 5) throw new AppError('验证码无效或已过期', 400);
  const matched = await bcrypt.compare(code, record.codeHash);
  if (!matched) {
    await prisma.emailVerificationCode.update({ where: { id: record.id }, data: { attempts: { increment: 1 } } });
    throw new AppError('验证码错误', 400);
  }
  const owner = await prisma.user.findFirst({ where: { email, id: { not: Number(userId) } }, select: { id: true } });
  if (owner) throw new AppError('该邮箱已被其他用户绑定', 409);
  const user = await prisma.$transaction(async (tx) => {
    await tx.emailVerificationCode.update({ where: { id: record.id }, data: { usedAt: new Date() } });
    return tx.user.update({
      where: { id: Number(userId) },
      data: { email, emailVerifiedAt: new Date() },
      include: { store: true }
    });
  });
  return publicUser(user);
}
