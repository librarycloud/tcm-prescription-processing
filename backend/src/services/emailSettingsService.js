import { EMAIL_SCENE_NAMES, EMAIL_TEMPLATE_VARIABLES } from '../constants/email.js';
import { PICKUP_METHOD_NAMES } from '../constants/notification.js';
import { AppError } from '../utils/appError.js';
import { encryptSetting } from '../utils/settingsEncryption.js';
import { isValidEmail } from '../utils/validators.js';

const DEFAULT_TEMPLATES = [
  {
    scene: 'verification',
    name: '邮箱验证码',
    subject: '取货系统邮箱验证码',
    content: '您的邮箱验证码是 {{code}}，{{expiresMinutes}} 分钟内有效。'
  },
  ...[0, 1, 2].map((method) => ({
    scene: `pickup_${method}`,
    name: `${PICKUP_METHOD_NAMES[method]}邮件通知`,
    subject: '您的包裹取货通知',
    content: '您好，{{receiverName}}，您的包裹“{{itemName}}”已录入，取货码为 {{pickupCode}}，取货方式为 {{pickupMethod}}。'
  }))
];

function clean(value, max, label) {
  const text = String(value || '').trim();
  if (text.length > max) throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return text || null;
}

function validateEmail(value) {
  const email = String(value || '').trim().toLowerCase();
  if (email && !isValidEmail(email)) {
    throw new AppError('邮箱格式不正确', 400);
  }
  return email;
}

export async function ensureEmailDefaults(prisma) {
  await prisma.emailConfig.upsert({
    where: { configKey: 'default' },
    update: {},
    create: { configKey: 'default', port: 465, secure: 1, enabled: 0 }
  });
  await prisma.emailTemplate.createMany({
    data: DEFAULT_TEMPLATES.map((template) => ({ ...template, enabled: 0 })),
    skipDuplicates: true
  });
}

function publicConfig(config) {
  return {
    id: config.id,
    host: config.host || '',
    port: config.port,
    secure: config.secure === 1,
    username: config.username || '',
    passwordConfigured: Boolean(config.passwordEncrypted),
    fromName: config.fromName || '',
    fromEmail: config.fromEmail || '',
    enabled: config.enabled === 1,
    updatedAt: config.updatedAt,
    updatedBy: config.updatedBy
  };
}

function publicTemplate(template) {
  return {
    ...template,
    sceneName: EMAIL_SCENE_NAMES[template.scene] || template.scene,
    enabled: template.enabled === 1
  };
}

export async function getEmailSettings(prisma) {
  await ensureEmailDefaults(prisma);
  const [config, templates] = await Promise.all([
    prisma.emailConfig.findUnique({ where: { configKey: 'default' } }),
    prisma.emailTemplate.findMany({ orderBy: { id: 'asc' } })
  ]);
  return { config: publicConfig(config), templates: templates.map(publicTemplate), variables: EMAIL_TEMPLATE_VARIABLES };
}

function validateEnabled(config) {
  if (!config.host || !config.port || !config.username || !config.passwordEncrypted || !config.fromEmail) {
    throw new AppError('启用邮件前请完整填写 SMTP 主机、端口、账号、密码和发件邮箱', 400);
  }
  validateEmail(config.fromEmail);
}

export async function updateEmailConfig(prisma, adminId, payload) {
  await ensureEmailDefaults(prisma);
  const current = await prisma.emailConfig.findUnique({ where: { configKey: 'default' } });
  const data = { updatedBy: Number(adminId) };
  if (payload.host !== undefined) data.host = clean(payload.host, 255, 'SMTP 主机');
  if (payload.port !== undefined) {
    const port = Number(payload.port);
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new AppError('SMTP 端口不正确', 400);
    data.port = port;
  }
  if (payload.secure !== undefined) data.secure = payload.secure ? 1 : 0;
  if (payload.username !== undefined) data.username = clean(payload.username, 255, 'SMTP 用户名');
  if (payload.password !== undefined && String(payload.password).trim()) {
    data.passwordEncrypted = encryptSetting(String(payload.password).trim());
  }
  if (payload.clearPassword === true) data.passwordEncrypted = null;
  if (payload.fromName !== undefined) data.fromName = clean(payload.fromName, 100, '发件人名称');
  if (payload.fromEmail !== undefined) data.fromEmail = validateEmail(payload.fromEmail) || null;
  if (payload.enabled !== undefined) data.enabled = payload.enabled ? 1 : 0;

  const merged = { ...current, ...data };
  if (merged.enabled === 1) validateEnabled(merged);
  return prisma.emailConfig.update({ where: { configKey: 'default' }, data }).then(publicConfig);
}

export async function updateEmailTemplate(prisma, adminId, id, payload) {
  const templateId = Number(id);
  if (!Number.isInteger(templateId) || templateId <= 0) throw new AppError('模板 ID 不正确', 400);
  const current = await prisma.emailTemplate.findUnique({ where: { id: templateId } });
  if (!current) throw new AppError('邮件模板不存在', 404);
  const subject = clean(payload.subject, 255, '邮件主题');
  const content = clean(payload.content, 10000, '邮件内容');
  if (!subject || !content) throw new AppError('邮件主题和内容不能为空', 400);
  const enabled = payload.enabled ? 1 : 0;
  const updated = await prisma.emailTemplate.update({
    where: { id: templateId },
    data: { name: clean(payload.name, 100, '模板名称') || current.name, subject, content, enabled, updatedBy: Number(adminId) }
  });
  return publicTemplate(updated);
}
