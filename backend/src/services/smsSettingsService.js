import {
  PICKUP_METHOD_NAMES,
  PROVIDER_NAMES,
  SMS_PROVIDERS,
  TEMPLATE_SOURCES
} from '../constants/notification.js';
import { AppError } from '../utils/appError.js';
import { encryptSetting } from '../utils/settingsEncryption.js';
import { parseVariableMapping, validateVariableMapping } from './smsTemplateService.js';

const DEFAULT_MAPPING = [
  { key: 'name', source: 'receiverName' },
  { key: 'code', source: 'pickupCode' },
  { key: 'item', source: 'itemName' },
  { key: 'method', source: 'pickupMethod' }
];

const DEFAULT_REGIONS = {
  tencent: 'ap-guangzhou',
  aliyun: 'cn-hangzhou',
  volcengine: 'cn-north-1'
};

function requireProvider(provider) {
  if (!SMS_PROVIDERS.includes(provider)) throw new AppError('不支持的短信供应商', 400);
  return provider;
}

function cleanText(value, maxLength, fieldName) {
  const text = String(value || '').trim();
  if (text.length > maxLength) throw new AppError(`${fieldName}不能超过 ${maxLength} 个字符`, 400);
  return text || null;
}

function publicConfig(item) {
  return {
    id: item.id,
    provider: item.provider,
    providerName: PROVIDER_NAMES[item.provider],
    enabled: item.enabled === 1,
    accessKeyId: item.accessKeyId || '',
    secretConfigured: Boolean(item.secretEncrypted),
    signName: item.signName || '',
    sdkAppId: item.sdkAppId || '',
    smsAccount: item.smsAccount || '',
    region: item.region || DEFAULT_REGIONS[item.provider],
    updatedAt: item.updatedAt,
    updatedBy: item.updatedBy
  };
}

function publicTemplate(item) {
  return {
    ...item,
    enabled: item.enabled === 1,
    variableMapping: parseVariableMapping(item.variableMapping)
  };
}

export async function ensureSmsDefaults(prisma) {
  await prisma.smsConfig.createMany({
    data: SMS_PROVIDERS.map((provider) => ({
      provider,
      enabled: 0,
      region: DEFAULT_REGIONS[provider]
    })),
    skipDuplicates: true
  });
  await prisma.smsTemplate.createMany({
    data: SMS_PROVIDERS.flatMap((provider) =>
      [0, 1, 2].map((pickupMethod) => ({
        provider,
        pickupMethod,
        name: `${PICKUP_METHOD_NAMES[pickupMethod]}通知模板`,
        contentPreview:
          '您好，{{receiverName}}，您的{{itemName}}已录入，取货码为{{pickupCode}}，取货方式为{{pickupMethod}}。',
        variableMapping: JSON.stringify(DEFAULT_MAPPING),
        enabled: 0
      }))
    ),
    skipDuplicates: true
  });
}

export async function getSmsSettings(prisma) {
  await ensureSmsDefaults(prisma);
  const [configs, templates] = await Promise.all([
    prisma.smsConfig.findMany({ orderBy: { id: 'asc' } }),
    prisma.smsTemplate.findMany({ orderBy: [{ provider: 'asc' }, { pickupMethod: 'asc' }] })
  ]);
  return {
    providers: configs.map(publicConfig),
    templates: templates.map(publicTemplate),
    templateSources: TEMPLATE_SOURCES
  };
}

function validateEnabledConfig(item) {
  if (!item.accessKeyId || !item.secretEncrypted || !item.signName) {
    throw new AppError('启用前请填写 AccessKey、Secret 和短信签名', 400);
  }
  if (item.provider === 'tencent' && !item.sdkAppId) {
    throw new AppError('腾讯云短信需要填写 SDK App ID', 400);
  }
  if (item.provider === 'volcengine' && !item.smsAccount) {
    throw new AppError('火山引擎短信需要填写短信账号', 400);
  }
}

export async function updateSmsConfig(prisma, adminId, providerValue, payload) {
  const provider = requireProvider(providerValue);
  await ensureSmsDefaults(prisma);
  const current = await prisma.smsConfig.findUnique({ where: { provider } });
  const data = { updatedBy: Number(adminId) };
  if (payload.accessKeyId !== undefined) {
    data.accessKeyId = cleanText(payload.accessKeyId, 255, 'AccessKey');
  }
  if (payload.secretKey !== undefined && String(payload.secretKey).trim()) {
    data.secretEncrypted = encryptSetting(String(payload.secretKey).trim());
  } else if (payload.clearSecret === true) {
    data.secretEncrypted = null;
  }
  if (payload.signName !== undefined) data.signName = cleanText(payload.signName, 100, '短信签名');
  if (payload.sdkAppId !== undefined) data.sdkAppId = cleanText(payload.sdkAppId, 64, 'SDK App ID');
  if (payload.smsAccount !== undefined) {
    data.smsAccount = cleanText(payload.smsAccount, 128, '短信账号');
  }
  if (payload.region !== undefined) data.region = cleanText(payload.region, 64, '地域');
  if (payload.enabled !== undefined) data.enabled = payload.enabled ? 1 : 0;

  const merged = { ...current, ...data };
  if (merged.enabled === 1) validateEnabledConfig(merged);

  const updated = await prisma.$transaction(async (tx) => {
    if (merged.enabled === 1) {
      await tx.smsConfig.updateMany({
        where: { provider: { not: provider } },
        data: { enabled: 0, updatedBy: Number(adminId) }
      });
    }
    return tx.smsConfig.update({ where: { provider }, data });
  });
  return publicConfig(updated);
}

export async function updateSmsTemplate(prisma, adminId, id, payload) {
  const templateId = Number(id);
  if (!Number.isInteger(templateId) || templateId <= 0) throw new AppError('模板 ID 不正确', 400);
  const current = await prisma.smsTemplate.findUnique({ where: { id: templateId } });
  if (!current) throw new AppError('短信模板不存在', 404);

  const data = { updatedBy: Number(adminId) };
  if (payload.name !== undefined) {
    const name = cleanText(payload.name, 100, '模板名称');
    if (!name) throw new AppError('模板名称不能为空', 400);
    data.name = name;
  }
  if (payload.templateCode !== undefined) {
    data.templateCode = cleanText(payload.templateCode, 128, '模板编号');
  }
  if (payload.contentPreview !== undefined) {
    data.contentPreview = cleanText(payload.contentPreview, 500, '模板预览');
  }
  if (payload.variableMapping !== undefined) {
    data.variableMapping = JSON.stringify(validateVariableMapping(payload.variableMapping));
  }
  if (payload.enabled !== undefined) data.enabled = payload.enabled ? 1 : 0;
  const merged = { ...current, ...data };
  if (merged.enabled === 1 && !merged.templateCode) {
    throw new AppError('启用模板前请填写云平台模板编号', 400);
  }
  return publicTemplate(await prisma.smsTemplate.update({ where: { id: templateId }, data }));
}
