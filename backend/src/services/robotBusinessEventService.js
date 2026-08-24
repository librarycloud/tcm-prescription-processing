import { PICKUP_METHOD_NAMES } from '../constants/notification.js';
import { DICTIONARY_TYPES } from '../constants/processing.js';
import { maskPhone } from './robotTemplateService.js';
import { publishRobotNotificationEventSafely } from './robotNotificationService.js';

async function resolveOperatorName(prisma, actor) {
  if (actor?.name || actor?.nickname) return actor.name || actor.nickname;
  if (actor?.id && (prisma?.admin?.findUnique || prisma?.user?.findUnique)) {
    try {
      const repository = prisma.admin?.findUnique ? prisma.admin : prisma.user;
      const user = await repository.findUnique({
        where: { id: Number(actor.id) },
        select: { name: true, nickname: true }
      });
      return user?.name || user?.nickname || '管理员';
    } catch {
      return '管理员';
    }
  }
  return '管理员';
}

async function resolveNotifyTypeName(prisma, value) {
  const notifyTypeId = Number(value);
  if (prisma?.dictionary?.findFirst) {
    try {
      const item = await prisma.dictionary.findFirst({
        where: { type: DICTIONARY_TYPES.NOTIFY_TYPE, id: notifyTypeId },
        select: { name: true }
      });
      if (item?.name) return item.name;
    } catch {
      // Fall through to legacy names when the dictionary is temporarily unavailable.
    }
  }
  return '-';
}

function dateTime(value = new Date()) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
}

function dateOnly(value) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toISOString().slice(0, 10);
}

export async function publishPackageRobotEvent(prisma, eventCode, pkg, actor) {
  const occurredAt = new Date();
  const resolvedOperatorName = await resolveOperatorName(prisma, actor);
  return publishRobotNotificationEventSafely(prisma, {
    eventKey: `${eventCode}:${pkg.id}`,
    eventCode,
    businessId: pkg.id,
    primaryStoreId: pkg.storeId,
    relatedStoreIds: [pkg.storeId],
    operatorId: actor?.id,
    occurredAt,
    variables: {
      eventTime: dateTime(occurredAt),
      operatorName: resolvedOperatorName,
      storeName: pkg.store?.name,
      packageId: pkg.id,
      pickupCode: pkg.pickupCode,
      receiverName: pkg.receiverName,
      receiverPhoneMasked: maskPhone(pkg.receiverPhone),
      itemName: pkg.itemName,
      itemInfo: pkg.itemInfo,
      pickupMethod: PICKUP_METHOD_NAMES[Number(pkg.pickupMethod)] || '-',
      createdAt: dateTime(pkg.createdAt),
      verifiedAt: dateTime(pkg.pickedAt || occurredAt)
    }
  });
}

export async function publishProcessingCompletedRobotEvent(prisma, plan, actor) {
  const occurredAt = plan.finishDate || new Date();
  const [resolvedOperatorName, notifyTypeName] = await Promise.all([
    resolveOperatorName(prisma, actor),
    resolveNotifyTypeName(prisma, plan.notifyType)
  ]);
  return publishRobotNotificationEventSafely(prisma, {
    eventKey: `PROCESSING_COMPLETED:${plan.id}`,
    eventCode: 'PROCESSING_COMPLETED',
    businessId: plan.id,
    primaryStoreId: plan.storeId,
    relatedStoreIds: [plan.storeId],
    operatorId: actor?.id,
    occurredAt,
    variables: {
      eventTime: dateTime(occurredAt),
      operatorName: resolvedOperatorName,
      storeName: plan.store?.name,
      planId: plan.id,
      prescriptionNo: plan.prescription?.prescriptionNo,
      customerName: plan.prescription?.customerName,
      processType: plan.processType?.name,
      totalDose: plan.totalDose,
      bagCount: plan.bagCount,
      pickupCode: plan.pickupCode || plan.package?.pickupCode,
      pickupMethod: PICKUP_METHOD_NAMES[Number(plan.pickupMethod)] || '-',
      notifyType: notifyTypeName,
      finishTime: dateTime(occurredAt)
    }
  });
}

function transferItemSummary(items = []) {
  const parts = items.slice(0, 5).map((item) => `${item.itemName} ${Number(item.quantity)}${item.unit}`);
  if (items.length > 5) parts.push(`等 ${items.length} 项`);
  return parts.join('、') || '-';
}

export async function publishTransferRobotEvent(prisma, eventCode, transfer, actor, suffix = '') {
  const occurredAt = new Date();
  const eventKey = `${eventCode}:${transfer.id}${suffix ? `:${suffix}` : ''}`;
  const resolvedOperatorName = await resolveOperatorName(prisma, actor);
  return publishRobotNotificationEventSafely(prisma, {
    eventKey,
    eventCode,
    businessId: transfer.id,
    primaryStoreId: transfer.fromStoreId,
    relatedStoreIds: [transfer.fromStoreId, transfer.toStoreId],
    operatorId: actor?.id,
    occurredAt,
    variables: {
      eventTime: dateTime(occurredAt),
      operatorName: resolvedOperatorName,
      storeName: transfer.fromStore?.name,
      transferId: transfer.id,
      transferNo: transfer.transferNo,
      fromStoreName: transfer.fromStore?.name,
      toStoreName: transfer.toStore?.name,
      itemCount: transfer.items?.length || 0,
      itemSummary: transferItemSummary(transfer.items),
      transferDate: dateOnly(transfer.transferDate),
      expectedReturnDate: dateOnly(transfer.expectedReturnDate),
      remark: transfer.cancelReason || transfer.remark
    }
  });
}
