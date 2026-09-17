import {
  ROBOT_DELIVERY_STATUS,
  ROBOT_DELIVERY_STATUS_VALUES,
  ROBOT_EVENT_DEFINITIONS,
  ROBOT_SCOPE_TYPES,
} from "../constants/robotNotification.js";
import { isSuperAdmin } from "../constants/roles.js";
import { sendRobotMessage } from "../providers/robot/index.js";
import { AppError } from "../utils/appError.js";
import { decryptSetting } from "../utils/settingsEncryption.js";
import { recordOperation } from "./operationLogService.js";
import { renderRobotTemplate } from "./robotTemplateService.js";

const RETRY_DELAYS = [10_000, 60_000, 300_000];

function positiveInt(value, fallback = null) {
  const number = Number(value);
  return Number.isInteger(number) && number > 0 ? number : fallback;
}

function uniqueStoreIds(values) {
  return [
    ...new Set(
      (values || []).map(Number).filter((id) => Number.isInteger(id) && id > 0),
    ),
  ];
}

export async function publishRobotNotificationEvent(prisma, payload) {
  const definition = ROBOT_EVENT_DEFINITIONS[payload.eventCode];
  if (!definition) throw new AppError("不支持的机器人通知事件", 400);
  const eventKey = String(payload.eventKey || "").trim();
  if (!eventKey || eventKey.length > 160)
    throw new AppError("机器人通知事件编号不正确", 400);
  const relatedStoreIds = uniqueStoreIds(payload.relatedStoreIds);
  const occurredAt =
    payload.occurredAt instanceof Date ? payload.occurredAt : new Date();
  return prisma.$transaction(async (tx) => {
    const event = await tx.robotNotificationEvent.upsert({
      where: { eventKey },
      update: {},
      create: {
        eventKey,
        eventCode: payload.eventCode,
        businessType: definition.businessType,
        businessId: Number(payload.businessId),
        primaryStoreId: positiveInt(payload.primaryStoreId),
        relatedStoreIds,
        variables: payload.variables || {},
        operatorId: positiveInt(payload.operatorId),
        occurredAt,
      },
    });
    const eventConfigs = await tx.robotEventConfig.findMany({
      where: {
        eventCode: payload.eventCode,
        enabled: 1,
        robot: {
          is: {
            enabled: 1,
            deletedAt: null,
            OR: [
              { scopeType: ROBOT_SCOPE_TYPES.HEADQUARTERS },
              {
                scopeType: ROBOT_SCOPE_TYPES.STORE,
                storeId: { in: relatedStoreIds },
              },
            ],
          },
        },
      },
      include: { robot: true },
    });
    const deliveries = [
      ...new Map(eventConfigs.map((item) => [item.robotId, item])).values(),
    ].map((item) => ({
      eventId: event.id,
      robotId: item.robotId,
      platform: item.robot.platform,
      templateContent: item.templateContent,
      renderedContent: renderRobotTemplate(
        item.templateContent,
        payload.variables || {},
      ),
      status: ROBOT_DELIVERY_STATUS.PENDING,
    }));
    if (deliveries.length)
      await tx.robotDeliveryLog.createMany({
        data: deliveries,
        skipDuplicates: true,
      });
    return { eventId: event.id, deliveryCount: deliveries.length };
  });
}

export async function publishRobotNotificationEventSafely(
  prisma,
  payload,
  logger = console,
) {
  if (
    !prisma?.robotNotificationEvent ||
    !prisma?.robotEventConfig ||
    !prisma?.robotDeliveryLog
  ) {
    return null;
  }
  try {
    return await publishRobotNotificationEvent(prisma, payload);
  } catch (error) {
    logger?.error?.(
      { error, eventKey: payload.eventKey },
      "Failed to enqueue robot notification",
    );
    return null;
  }
}

function logScope(actor) {
  return isSuperAdmin(actor)
    ? {}
    : {
        robot: {
          is: {
            scopeType: ROBOT_SCOPE_TYPES.STORE,
            storeId: Number(actor.storeId),
          },
        },
      };
}

function parseDate(value, end = false) {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  if (end) date.setDate(date.getDate() + 1);
  return date;
}

export async function listRobotDeliveryLogs(prisma, actor, query = {}) {
  const page = Math.max(Number(query.page) || 1, 1);
  const pageSize = Math.min(Math.max(Number(query.pageSize) || 20, 1), 100);
  const where = { ...logScope(actor) };
  if (
    query.status !== undefined &&
    query.status !== null &&
    query.status !== ""
  ) {
    const status = Number(query.status);
    if (!ROBOT_DELIVERY_STATUS_VALUES.includes(status))
      throw new AppError("机器人通知状态不正确", 400);
    where.status = status;
  }
  if (query.platform) where.platform = String(query.platform);
  const robotId = positiveInt(query.robotId);
  if (robotId) where.robotId = robotId;
  if (query.eventCode)
    where.event = {
      ...(where.event || {}),
      is: { eventCode: String(query.eventCode) },
    };
  const storeId = positiveInt(query.storeId);
  if (storeId)
    where.event = {
      is: { ...(where.event?.is || {}), primaryStoreId: storeId },
    };
  const start = parseDate(query.startDate);
  const end = parseDate(query.endDate, true);
  if (start || end) {
    where.createdAt = {};
    if (start) where.createdAt.gte = start;
    if (end) where.createdAt.lt = end;
  }
  const include = {
    robot: {
      select: {
        id: true,
        name: true,
        scopeType: true,
        storeId: true,
        store: { select: { id: true, name: true } },
      },
    },
    event: true,
  };
  const [list, total] = await Promise.all([
    prisma.robotDeliveryLog.findMany({
      where,
      include,
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.robotDeliveryLog.count({ where }),
  ]);
  return {
    list,
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) },
  };
}

export async function getRobotDeliveryLog(prisma, actor, idValue) {
  const log = await prisma.robotDeliveryLog.findFirst({
    where: { id: Number(idValue), ...logScope(actor) },
    include: { robot: { include: { store: true } }, event: true },
  });
  if (!log) throw new AppError("机器人发送记录不存在", 404);
  return log;
}

export async function retryRobotDeliveryLog(prisma, actor, idValue) {
  const current = await getRobotDeliveryLog(prisma, actor, idValue);
  if (current.status !== ROBOT_DELIVERY_STATUS.FAILED)
    throw new AppError("只有发送失败的记录可以重试", 400);
  const updated = await prisma.robotDeliveryLog.update({
    where: { id: current.id },
    data: {
      status: ROBOT_DELIVERY_STATUS.PENDING,
      attemptCount: 0,
      nextRetryAt: new Date(),
      errorCode: null,
      errorMessage: null,
    },
  });
  await recordOperation(prisma, actor, {
    module: "robot-notification",
    action: "retry",
    targetId: current.id,
    storeId: current.robot.storeId,
    description: `重新发送机器人通知记录 ${current.id}`,
  });
  return updated;
}

async function deliverClaimed(prisma, log, logger) {
  const attempt = log.attemptCount + 1;
  try {
    if (!log.robot.enabled || log.robot.deletedAt)
      throw Object.assign(new Error("机器人已停用或删除"), {
        code: "ROBOT_DISABLED",
      });
    const result = await sendRobotMessage(log.platform, {
      webhook: decryptSetting(log.robot.webhookEncrypted),
      secret: log.robot.secretEncrypted
        ? decryptSetting(log.robot.secretEncrypted)
        : "",
      content: log.renderedContent,
    });
    await prisma.robotDeliveryLog.update({
      where: { id: log.id },
      data: {
        status: ROBOT_DELIVERY_STATUS.SUCCESS,
        attemptCount: attempt,
        nextRetryAt: null,
        providerRequestId: result.requestId || null,
        providerResponse: String(result.response || "").slice(0, 4000),
        errorCode: null,
        errorMessage: null,
        sentAt: new Date(),
      },
    });
  } catch (error) {
    const canRetry =
      attempt <= RETRY_DELAYS.length && error?.code !== "ROBOT_DISABLED";
    await prisma.robotDeliveryLog.update({
      where: { id: log.id },
      data: {
        status: canRetry
          ? ROBOT_DELIVERY_STATUS.RETRYING
          : ROBOT_DELIVERY_STATUS.FAILED,
        attemptCount: attempt,
        nextRetryAt: canRetry
          ? new Date(Date.now() + RETRY_DELAYS[attempt - 1])
          : null,
        providerResponse: error?.response
          ? String(error.response).slice(0, 4000)
          : null,
        errorCode: String(
          error?.code || error?.name || "ROBOT_SEND_FAILED",
        ).slice(0, 100),
        errorMessage: String(error?.message || "群机器人发送失败").slice(
          0,
          500,
        ),
      },
    });
    logger?.warn?.(
      { deliveryId: log.id, attempt, error: error?.message },
      "Robot notification delivery failed",
    );
  }
}

export async function processRobotDeliveryBatch(
  prisma,
  logger = console,
  limit = 20,
) {
  const now = new Date();
  await prisma.robotDeliveryLog.updateMany({
    where: {
      status: ROBOT_DELIVERY_STATUS.SENDING,
      updatedAt: { lt: new Date(Date.now() - 10 * 60_000) },
    },
    data: {
      status: ROBOT_DELIVERY_STATUS.RETRYING,
      nextRetryAt: now,
      errorCode: "STALE_SENDING",
      errorMessage: "发送进程异常中断，已重新排队",
    },
  });
  const candidates = await prisma.robotDeliveryLog.findMany({
    where: {
      OR: [
        { status: ROBOT_DELIVERY_STATUS.PENDING },
        { status: ROBOT_DELIVERY_STATUS.RETRYING, nextRetryAt: { lte: now } },
      ],
    },
    include: { robot: true },
    orderBy: { createdAt: "asc" },
    take: limit,
  });
  let processed = 0;
  for (const log of candidates) {
    const claimed = await prisma.robotDeliveryLog.updateMany({
      where: { id: log.id, status: log.status, updatedAt: log.updatedAt },
      data: { status: ROBOT_DELIVERY_STATUS.SENDING },
    });
    if (claimed.count !== 1) continue;
    processed += 1;
    await deliverClaimed(prisma, log, logger);
  }
  return processed;
}

export function startRobotDeliveryWorker(fastify) {
  let running = false;
  const run = async () => {
    if (running) return;
    running = true;
    try {
      await processRobotDeliveryBatch(fastify.prisma, fastify.log);
    } catch (error) {
      fastify.log.error({ error }, "Robot notification worker failed");
    } finally {
      running = false;
    }
  };
  const timer = setInterval(() => void run(), 5000);
  timer.unref?.();
  fastify.addHook("onClose", async () => clearInterval(timer));
  void run();
}
