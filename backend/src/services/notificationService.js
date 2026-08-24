import { NOTIFICATION_STATUS, SMS_PROVIDERS } from '../constants/notification.js';
import { sendSmsByProvider } from '../providers/sms/index.js';
import { AppError } from '../utils/appError.js';
import { validatePhone } from '../utils/validators.js';
import { ensureSmsDefaults } from './smsSettingsService.js';
import { resolveTemplate } from './smsTemplateService.js';
import { getAccessiblePackage } from './packageAccessService.js';
import { sendSmtpEmail } from '../providers/email/smtpProvider.js';
import { ensureEmailDefaults } from './emailSettingsService.js';
import { renderEmailTemplate } from './emailTemplateService.js';
import { decryptSetting } from '../utils/settingsEncryption.js';
import { PICKUP_METHOD_NAMES } from '../constants/notification.js';

const NOTIFICATION_PACKAGE_INCLUDE = {
  store: { select: { id: true, name: true, address: true, phone: true } }
};

function requireRequestId(value) {
  const requestId = String(value || '').trim();
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(requestId)) {
    throw new AppError('通知请求编号格式不正确', 400);
  }
  return requestId;
}

async function getProviderResources(prisma, provider, pickupMethod, requireEnabled = true) {
  await ensureSmsDefaults(prisma);
  const config = await prisma.smsConfig.findUnique({ where: { provider } });
  const template = await prisma.smsTemplate.findUnique({
    where: { provider_pickupMethod: { provider, pickupMethod: Number(pickupMethod) } }
  });
  if (!config || (requireEnabled && config.enabled !== 1)) {
    throw new AppError('当前没有启用的短信供应商', 400);
  }
  if (!template || template.enabled !== 1 || !template.templateCode) {
    throw new AppError('当前取货方式没有启用短信模板', 400);
  }
  if (!config.secretEncrypted) throw new AppError('短信供应商密钥尚未配置', 400);
  return { config, template };
}

async function deliver({ config, template, phone, packageData }) {
  const resolved = resolveTemplate(template, packageData);
  const result = await sendSmsByProvider(config.provider, {
    config,
    secretKey: decryptSetting(config.secretEncrypted),
    phone,
    template,
    values: resolved.values,
    keyedValues: resolved.keyedValues
  });
  return { result, resolved };
}

function errorDetails(error) {
  return {
    errorCode: String(error?.code || error?.name || 'SMS_SEND_FAILED').slice(0, 100),
    errorMessage: String(error?.message || '短信发送失败').slice(0, 500)
  };
}

function ensurePackageCanBeNotified(packageData) {
  if (Number(packageData.status) === 1) {
    throw new AppError('已取包裹无需再发送通知', 400);
  }
  if (Number(packageData.pickupMethod) === 2 && !String(packageData.expressTrackingNo || '').trim()) {
    throw new AppError('快递包裹填写快递单号后才能发送取货通知', 400);
  }
}

export async function getPackageNotifications(prisma, actor, packageIdValue) {
  const packageId = Number(packageIdValue);
  const packageData = await getAccessiblePackage(
    prisma,
    actor,
    { id: packageId },
    NOTIFICATION_PACKAGE_INCLUDE
  );
  await ensureSmsDefaults(prisma);
  const activeConfig = await prisma.smsConfig.findFirst({ where: { enabled: 1 } });
  const template = activeConfig
    ? await prisma.smsTemplate.findUnique({
      where: {
        provider_pickupMethod: {
          provider: activeConfig.provider,
          pickupMethod: Number(packageData.pickupMethod)
        }
      }
    })
    : null;
  await ensureEmailDefaults(prisma);
  const [emailConfig, emailTemplate, emailUser] = await Promise.all([
    prisma.emailConfig.findUnique({ where: { configKey: 'default' } }),
    prisma.emailTemplate.findUnique({
      where: { scene: `pickup_${Number(packageData.pickupMethod)}` }
    }),
    packageData.receiverPhone
      ? prisma.user.findUnique({
        where: { phone: packageData.receiverPhone },
        select: { email: true, emailVerifiedAt: true }
      })
      : Promise.resolve(null)
  ]);
  const logs = await prisma.notificationLog.findMany({
    where: { packageId },
    orderBy: { createdAt: 'desc' },
    take: 20
  });
  const operatorIds = [...new Set(logs.map((item) => item.operatorId).filter(Boolean))];
  const operators = operatorIds.length
    ? await prisma.admin.findMany({
      where: { id: { in: operatorIds } },
      select: { id: true, nickname: true, phone: true }
    })
    : [];
  const operatorMap = new Map(operators.map((item) => [item.id, item]));
  return {
    package: packageData,
    channel: 'sms',
    activeProvider: activeConfig?.provider || null,
    template: template
      ? {
        id: template.id,
        name: template.name,
        enabled: template.enabled === 1,
        preview: resolveTemplate(template, packageData).preview
      }
      : null,
    email: {
      address: emailUser?.email || null,
      verified: Boolean(emailUser?.email && emailUser?.emailVerifiedAt),
      enabled: Boolean(emailConfig?.enabled && emailConfig?.passwordEncrypted),
      template: emailTemplate
        ? {
          id: emailTemplate.id,
          name: emailTemplate.name,
          enabled: emailTemplate.enabled === 1,
          subject: emailTemplate.subject,
          content: emailTemplate.content
        }
        : null
    },
    logs: logs.map((item) => ({ ...item, operator: operatorMap.get(item.operatorId) || null }))
  };
}

export async function sendPackageNotification(prisma, actor, packageIdValue, payload) {
  if (payload.channel === 'email') {
    return sendPackageEmailNotification(prisma, actor, packageIdValue, payload);
  }
  if (payload.channel !== 'sms') throw new AppError('当前仅支持短信和邮件通知', 400);
  const requestId = requireRequestId(payload.requestId);
  const existing = await prisma.notificationLog.findUnique({ where: { requestId } });
  if (existing) return existing;

  const packageId = Number(packageIdValue);
  const packageData = await getAccessiblePackage(
    prisma,
    actor,
    { id: packageId },
    NOTIFICATION_PACKAGE_INCLUDE
  );
  ensurePackageCanBeNotified(packageData);
  if (!packageData.receiverPhone) throw new AppError('包裹未填写手机号，无法发送短信', 400);
  validatePhone(packageData.receiverPhone, '收件人手机号');

  const recent = await prisma.notificationLog.findFirst({
    where: { packageId, createdAt: { gte: new Date(Date.now() - 60_000) } },
    orderBy: { createdAt: 'desc' }
  });
  if (recent) throw new AppError('通知发送过于频繁，请一分钟后再试', 429);

  const activeConfig = await prisma.smsConfig.findFirst({ where: { enabled: 1 } });
  if (!activeConfig) throw new AppError('当前没有启用的短信供应商', 400);
  const { config, template } = await getProviderResources(
    prisma,
    activeConfig.provider,
    packageData.pickupMethod
  );
  const resolved = resolveTemplate(template, packageData);
  const log = await prisma.$transaction(async (tx) => {
    const created = await tx.notificationLog.create({
      data: {
        packageId,
        channel: 'sms',
        provider: config.provider,
        recipient: packageData.receiverPhone,
        templateId: template.id,
        templateCode: template.templateCode,
        templateParams: JSON.stringify(resolved.keyedValues),
        status: NOTIFICATION_STATUS.SENDING,
        requestId,
        operatorId: Number(actor.id)
      }
    });
    await tx.package.update({
      where: { id: packageId },
      data: { notificationStatus: NOTIFICATION_STATUS.SENDING }
    });
    return created;
  });

  try {
    const { result } = await deliver({
      config,
      template,
      phone: packageData.receiverPhone,
      packageData
    });
    const sentAt = new Date();
    return await prisma.$transaction(async (tx) => {
      const updatedLog = await tx.notificationLog.update({
        where: { id: log.id },
        data: {
          status: NOTIFICATION_STATUS.SUCCESS,
          providerRequestId: result.requestId || null,
          providerMessage: result.messageId || result.message || null,
          sentAt
        }
      });
      await tx.package.update({
        where: { id: packageId },
        data: {
          notificationStatus: NOTIFICATION_STATUS.SUCCESS,
          notificationCount: { increment: 1 },
          lastNotificationAt: sentAt
        }
      });
      return updatedLog;
    });
  } catch (error) {
    const details = errorDetails(error);
    const failedAt = new Date();
    await prisma.$transaction([
      prisma.notificationLog.update({
        where: { id: log.id },
        data: { status: NOTIFICATION_STATUS.FAILED, ...details, sentAt: failedAt }
      }),
      prisma.package.update({
        where: { id: packageId },
        data: { notificationStatus: NOTIFICATION_STATUS.FAILED, lastNotificationAt: failedAt }
      })
    ]);
    throw new AppError(details.errorMessage, 502);
  }
}

async function sendPackageEmailNotification(prisma, actor, packageIdValue, payload) {
  const requestId = requireRequestId(payload.requestId);
  const existing = await prisma.notificationLog.findUnique({ where: { requestId } });
  if (existing) return existing;

  const packageId = Number(packageIdValue);
  const packageData = await getAccessiblePackage(prisma, actor, { id: packageId });
  ensurePackageCanBeNotified(packageData);
  if (!packageData.receiverPhone) throw new AppError('包裹未填写手机号，无法关联用户邮箱', 400);
  const recipientUser = await prisma.user.findUnique({
    where: { phone: packageData.receiverPhone },
    select: { email: true, emailVerifiedAt: true }
  });
  if (!recipientUser?.email || !recipientUser.emailVerifiedAt) {
    throw new AppError('用户尚未绑定并验证邮箱，无法发送邮件', 400);
  }

  const recent = await prisma.notificationLog.findFirst({
    where: { packageId, channel: 'email', createdAt: { gte: new Date(Date.now() - 60_000) } },
    orderBy: { createdAt: 'desc' }
  });
  if (recent) throw new AppError('邮件发送过于频繁，请一分钟后再试', 429);

  await ensureEmailDefaults(prisma);
  const [config, template] = await Promise.all([
    prisma.emailConfig.findUnique({ where: { configKey: 'default' } }),
    prisma.emailTemplate.findUnique({ where: { scene: `pickup_${Number(packageData.pickupMethod)}` } })
  ]);
  if (!config?.enabled || !config.passwordEncrypted) throw new AppError('邮件服务尚未完整配置', 400);
  if (!template || template.enabled !== 1) throw new AppError('当前取货方式邮件模板尚未启用', 400);

  const values = {
    receiverName: packageData.receiverName,
    pickupCode: packageData.pickupCode,
    itemName: packageData.itemName,
    itemInfo: packageData.itemInfo || '',
    pickupMethod: PICKUP_METHOD_NAMES[Number(packageData.pickupMethod)] || '-',
    expressTrackingNo: packageData.expressTrackingNo || '',
    expressAddress: packageData.expressAddress || '',
    createdAt: packageData.createdAt
  };
  const rendered = renderEmailTemplate(template, values);
  const log = await prisma.$transaction(async (tx) => {
    const created = await tx.notificationLog.create({
      data: {
        packageId,
        channel: 'email',
        provider: 'smtp',
        recipient: recipientUser.email,
        templateId: template.id,
        templateCode: template.scene,
        templateParams: JSON.stringify(values),
        status: NOTIFICATION_STATUS.SENDING,
        requestId,
        operatorId: Number(actor.id)
      }
    });
    await tx.package.update({
      where: { id: packageId },
      data: { notificationStatus: NOTIFICATION_STATUS.SENDING }
    });
    return created;
  });

  try {
    const result = await sendSmtpEmail(config, decryptSetting(config.passwordEncrypted), {
      to: recipientUser.email,
      subject: rendered.subject,
      text: rendered.content
    });
    const sentAt = new Date();
    return prisma.$transaction(async (tx) => {
      const updated = await tx.notificationLog.update({
        where: { id: log.id },
        data: {
          status: NOTIFICATION_STATUS.SUCCESS,
          providerMessage: result.messageId || null,
          sentAt
        }
      });
      await tx.package.update({
        where: { id: packageId },
        data: {
          notificationStatus: NOTIFICATION_STATUS.SUCCESS,
          notificationCount: { increment: 1 },
          lastNotificationAt: sentAt
        }
      });
      return updated;
    });
  } catch (error) {
    const details = errorDetails(error);
    const failedAt = new Date();
    await prisma.$transaction([
      prisma.notificationLog.update({
        where: { id: log.id },
        data: { status: NOTIFICATION_STATUS.FAILED, ...details, sentAt: failedAt }
      }),
      prisma.package.update({
        where: { id: packageId },
        data: { notificationStatus: NOTIFICATION_STATUS.FAILED, lastNotificationAt: failedAt }
      })
    ]);
    throw new AppError(details.errorMessage, 502);
  }
}

export async function sendTestSms(prisma, adminId, payload) {
  const provider = String(payload.provider || '');
  if (!SMS_PROVIDERS.includes(provider)) throw new AppError('请选择短信供应商', 400);
  validatePhone(payload.phone, '测试手机号');
  const pickupMethod = Number(payload.pickupMethod);
  if (![0, 1, 2].includes(pickupMethod)) throw new AppError('请选择取货方式', 400);
  const requestId = requireRequestId(payload.requestId);
  const existing = await prisma.notificationLog.findUnique({ where: { requestId } });
  if (existing) return existing;
  const { config, template } = await getProviderResources(prisma, provider, pickupMethod, false);
  const packageData = {
    receiverName: '测试用户',
    receiverPhone: String(payload.phone),
    pickupCode: '123456',
    itemName: '测试包裹',
    itemInfo: '',
    pickupMethod,
    store: {
      name: '测试门店',
      address: '测试门店地址',
      phone: '0512-12345678'
    },
    expressTrackingNo: pickupMethod === 2 ? 'SF1234567890' : '',
    createdAt: new Date()
  };
  const resolved = resolveTemplate(template, packageData);
  const log = await prisma.notificationLog.create({
    data: {
      channel: 'sms',
      provider,
      recipient: String(payload.phone),
      templateId: template.id,
      templateCode: template.templateCode,
      templateParams: JSON.stringify(resolved.keyedValues),
      status: NOTIFICATION_STATUS.SENDING,
      requestId,
      operatorId: Number(adminId)
    }
  });
  try {
    const { result } = await deliver({
      config,
      template,
      phone: String(payload.phone),
      packageData
    });
    return prisma.notificationLog.update({
      where: { id: log.id },
      data: {
        status: NOTIFICATION_STATUS.SUCCESS,
        providerRequestId: result.requestId || null,
        providerMessage: result.messageId || result.message || null,
        sentAt: new Date()
      }
    });
  } catch (error) {
    const details = errorDetails(error);
    await prisma.notificationLog.update({
      where: { id: log.id },
      data: { status: NOTIFICATION_STATUS.FAILED, ...details, sentAt: new Date() }
    });
    throw new AppError(details.errorMessage, 502);
  }
}
