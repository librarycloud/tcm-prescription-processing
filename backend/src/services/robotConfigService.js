import {
  ROBOT_EVENT_DEFINITIONS,
  ROBOT_PLATFORMS,
  ROBOT_SCOPE_TYPES
} from '../constants/robotNotification.js';
import { isSuperAdmin } from '../constants/roles.js';
import { sendRobotMessage, validateRobotWebhook } from '../providers/robot/index.js';
import { AppError } from '../utils/appError.js';
import { decryptSetting, encryptSetting } from '../utils/settingsEncryption.js';
import { recordOperation } from './operationLogService.js';
import { eventDefinitionList, renderRobotTemplate, validateRobotTemplate } from './robotTemplateService.js';

const PLATFORM_VALUES = new Set(Object.values(ROBOT_PLATFORMS));
const SCOPE_VALUES = new Set(Object.values(ROBOT_SCOPE_TYPES));

function clean(value, max, label, required = false) {
  const text = String(value || '').trim();
  if (required && !text) throw new AppError(`${label}不能为空`, 400);
  if (text.length > max) throw new AppError(`${label}不能超过 ${max} 个字符`, 400);
  return text || null;
}

function robotScope(actor) {
  return isSuperAdmin(actor)
    ? { deletedAt: null }
    : { deletedAt: null, scopeType: ROBOT_SCOPE_TYPES.STORE, storeId: Number(actor.storeId) };
}

function publicRobot(robot) {
  return {
    id: robot.id,
    name: robot.name,
    platform: robot.platform,
    scopeType: robot.scopeType,
    storeId: robot.storeId,
    store: robot.store || null,
    webhookConfigured: Boolean(robot.webhookEncrypted),
    secretConfigured: Boolean(robot.secretEncrypted),
    enabled: robot.enabled === 1,
    remark: robot.remark || '',
    createdAt: robot.createdAt,
    updatedAt: robot.updatedAt,
    events: (robot.eventConfigs || []).map((item) => ({
      id: item.id,
      eventCode: item.eventCode,
      eventName: ROBOT_EVENT_DEFINITIONS[item.eventCode]?.name || item.eventCode,
      enabled: item.enabled === 1,
      templateContent: item.templateContent,
      variables: ROBOT_EVENT_DEFINITIONS[item.eventCode]?.variables || []
    }))
  };
}

async function requireStore(prisma, storeIdValue) {
  const storeId = Number(storeIdValue);
  if (!Number.isInteger(storeId) || storeId <= 0) throw new AppError('请选择所属门店', 400);
  const store = await prisma.store.findFirst({ where: { id: storeId, deletedAt: null, status: 1 } });
  if (!store) throw new AppError('所属门店不存在或已停用', 400);
  return storeId;
}

async function resolveOwnership(prisma, actor, payload, current = null) {
  const requestedScope = payload.scopeType ?? current?.scopeType ?? ROBOT_SCOPE_TYPES.STORE;
  if (!SCOPE_VALUES.has(requestedScope)) throw new AppError('机器人归属类型不正确', 400);
  if (!isSuperAdmin(actor) && requestedScope !== ROBOT_SCOPE_TYPES.STORE) {
    throw new AppError('门店管理员只能配置本门店机器人', 403);
  }
  if (requestedScope === ROBOT_SCOPE_TYPES.HEADQUARTERS) {
    if (!isSuperAdmin(actor)) throw new AppError('无权配置总部机器人', 403);
    return { scopeType: requestedScope, storeId: null };
  }
  const requestedStoreId = isSuperAdmin(actor)
    ? (payload.storeId ?? current?.storeId)
    : actor.storeId;
  return { scopeType: requestedScope, storeId: await requireStore(prisma, requestedStoreId) };
}

function platformValue(value) {
  const platform = String(value || '').trim();
  if (!PLATFORM_VALUES.has(platform)) throw new AppError('机器人平台不正确', 400);
  return platform;
}

export async function listRobotConfigs(prisma, actor) {
  const robots = await prisma.robotConfig.findMany({
    where: robotScope(actor),
    include: {
      store: { select: { id: true, name: true, code: true } },
      eventConfigs: { orderBy: { id: 'asc' } }
    },
    orderBy: [{ scopeType: 'asc' }, { storeId: 'asc' }, { createdAt: 'desc' }]
  });
  return { robots: robots.map(publicRobot), eventDefinitions: eventDefinitionList() };
}

export async function getRobotConfig(prisma, actor, idValue) {
  const id = Number(idValue);
  const robot = await prisma.robotConfig.findFirst({
    where: { id, ...robotScope(actor) },
    include: {
      store: { select: { id: true, name: true, code: true } },
      eventConfigs: { orderBy: { id: 'asc' } }
    }
  });
  if (!robot) throw new AppError('群机器人不存在', 404);
  return publicRobot(robot);
}

export async function createRobotConfig(prisma, actor, payload) {
  const name = clean(payload.name, 100, '机器人名称', true);
  const platform = platformValue(payload.platform);
  const ownership = await resolveOwnership(prisma, actor, payload);
  const webhook = validateRobotWebhook(platform, payload.webhook);
  const secret = clean(payload.secret, 255, '签名密钥');
  const robot = await prisma.$transaction(async (tx) => {
    const created = await tx.robotConfig.create({
      data: {
        name,
        platform,
        ...ownership,
        webhookEncrypted: encryptSetting(webhook),
        secretEncrypted: secret ? encryptSetting(secret) : null,
        enabled: payload.enabled ? 1 : 0,
        remark: clean(payload.remark, 500, '备注'),
        createdBy: Number(actor.id),
        updatedBy: Number(actor.id)
      }
    });
    await tx.robotEventConfig.createMany({
      data: Object.entries(ROBOT_EVENT_DEFINITIONS).map(([eventCode, definition]) => ({
        robotId: created.id,
        eventCode,
        enabled: definition.defaultEnabled ? 1 : 0,
        templateContent: definition.defaultTemplate,
        createdBy: Number(actor.id),
        updatedBy: Number(actor.id)
      }))
    });
    await recordOperation(tx, actor, {
      module: 'robot-notification',
      action: 'create',
      targetId: created.id,
      storeId: ownership.storeId,
      description: `新增${ownership.scopeType === ROBOT_SCOPE_TYPES.HEADQUARTERS ? '总部' : '门店'}群机器人「${name}」`
    });
    return created;
  });
  return getRobotConfig(prisma, actor, robot.id);
}

export async function updateRobotConfig(prisma, actor, idValue, payload) {
  const id = Number(idValue);
  const current = await prisma.robotConfig.findFirst({ where: { id, ...robotScope(actor) } });
  if (!current) throw new AppError('群机器人不存在', 404);
  const platform = payload.platform === undefined ? current.platform : platformValue(payload.platform);
  const ownership = await resolveOwnership(prisma, actor, payload, current);
  const data = { ...ownership, platform, updatedBy: Number(actor.id) };
  if (payload.name !== undefined) data.name = clean(payload.name, 100, '机器人名称', true);
  if (payload.remark !== undefined) data.remark = clean(payload.remark, 500, '备注');
  if (payload.enabled !== undefined) data.enabled = payload.enabled ? 1 : 0;
  if (payload.webhook !== undefined && String(payload.webhook).trim()) {
    data.webhookEncrypted = encryptSetting(validateRobotWebhook(platform, payload.webhook));
  } else if (platform !== current.platform) {
    validateRobotWebhook(platform, decryptSetting(current.webhookEncrypted));
  }
  if (payload.clearWebhook === true) throw new AppError('Webhook 不能为空', 400);
  if (payload.secret !== undefined && String(payload.secret).trim()) {
    data.secretEncrypted = encryptSetting(clean(payload.secret, 255, '签名密钥'));
  }
  if (payload.clearSecret === true) data.secretEncrypted = null;
  const updated = await prisma.robotConfig.update({ where: { id }, data });
  await recordOperation(prisma, actor, {
    module: 'robot-notification', action: 'update', targetId: id, storeId: ownership.storeId,
    description: `修改群机器人「${updated.name}」`
  });
  return getRobotConfig(prisma, actor, id);
}

export async function deleteRobotConfig(prisma, actor, idValue) {
  const id = Number(idValue);
  const current = await prisma.robotConfig.findFirst({ where: { id, ...robotScope(actor) } });
  if (!current) throw new AppError('群机器人不存在', 404);
  await prisma.robotConfig.update({
    where: { id },
    data: { enabled: 0, deletedAt: new Date(), updatedBy: Number(actor.id) }
  });
  await recordOperation(prisma, actor, {
    module: 'robot-notification', action: 'delete', targetId: id, storeId: current.storeId,
    description: `删除群机器人「${current.name}」`
  });
  return { id };
}

export async function updateRobotEventConfig(prisma, actor, robotIdValue, eventCode, payload) {
  const robot = await getRobotConfig(prisma, actor, robotIdValue);
  if (!ROBOT_EVENT_DEFINITIONS[eventCode]) throw new AppError('不支持的机器人通知事件', 400);
  const current = await prisma.robotEventConfig.findUnique({
    where: { robotId_eventCode: { robotId: robot.id, eventCode } }
  });
  if (!current) throw new AppError('机器人事件配置不存在', 404);
  const data = { updatedBy: Number(actor.id) };
  if (payload.enabled !== undefined) data.enabled = payload.enabled ? 1 : 0;
  if (payload.templateContent !== undefined) {
    data.templateContent = validateRobotTemplate(eventCode, payload.templateContent);
  }
  const updated = await prisma.robotEventConfig.update({ where: { id: current.id }, data });
  await recordOperation(prisma, actor, {
    module: 'robot-notification', action: 'update-event', targetId: robot.id, storeId: robot.storeId,
    description: `修改机器人「${robot.name}」的${ROBOT_EVENT_DEFINITIONS[eventCode].name}配置`
  });
  return { ...updated, enabled: updated.enabled === 1, eventName: ROBOT_EVENT_DEFINITIONS[eventCode].name };
}

export async function resetRobotEventTemplate(prisma, actor, robotIdValue, eventCode) {
  const definition = ROBOT_EVENT_DEFINITIONS[eventCode];
  if (!definition) throw new AppError('不支持的机器人通知事件', 400);
  return updateRobotEventConfig(prisma, actor, robotIdValue, eventCode, {
    templateContent: definition.defaultTemplate
  });
}

function sampleVariables(eventCode) {
  const now = new Date();
  const common = { eventTime: now, operatorName: '测试管理员', storeName: '测试门店' };
  if (eventCode.startsWith('PACKAGE_')) return { ...common, packageId: 1001, pickupCode: '123456', receiverName: '张三', receiverPhoneMasked: '138****8000', itemName: '中药包裹', itemInfo: '测试消息', pickupMethod: '自提', createdAt: now, verifiedAt: now };
  if (eventCode === 'PROCESSING_COMPLETED') return { ...common, planId: 2001, prescriptionNo: 'CF202607240001', customerName: '张三', processType: '代煎', totalDose: 7, bagCount: 14, pickupCode: '123456', pickupMethod: '自提', notifyType: '微信', finishTime: now };
  return { ...common, transferId: 3001, transferNo: 'DB202607240001', fromStoreName: '中心店', toStoreName: '园区店', itemCount: 2, itemSummary: '黄芪 5kg、党参 3kg', transferDate: '2026-07-24', expectedReturnDate: '2026-08-24', remark: '测试消息' };
}

export async function testRobotConfig(prisma, actor, idValue, payload = {}) {
  const robotView = await getRobotConfig(prisma, actor, idValue);
  const robot = await prisma.robotConfig.findUnique({ where: { id: robotView.id } });
  const eventCode = String(payload.eventCode || 'PACKAGE_CREATED');
  const eventConfig = await prisma.robotEventConfig.findUnique({
    where: { robotId_eventCode: { robotId: robot.id, eventCode } }
  });
  if (!eventConfig) throw new AppError('请选择有效的测试事件', 400);
  const content = renderRobotTemplate(eventConfig.templateContent, sampleVariables(eventCode));
  await sendRobotMessage(robot.platform, {
    webhook: decryptSetting(robot.webhookEncrypted),
    secret: robot.secretEncrypted ? decryptSetting(robot.secretEncrypted) : '',
    content
  });
  await recordOperation(prisma, actor, {
    module: 'robot-notification', action: 'test', targetId: robot.id, storeId: robot.storeId,
    description: `向群机器人「${robot.name}」发送测试消息`
  });
  return { robotId: robot.id, eventCode, content };
}
