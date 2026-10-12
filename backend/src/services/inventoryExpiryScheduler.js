import { publishRobotNotificationEventSafely } from './robotNotificationService.js';
import { getSystemConfigValue } from './systemConfigService.js';

const EXPIRY_THRESHOLDS = [
  { eventCode: 'EXPIRY_REMINDER_1M', days: 30 },
  { eventCode: 'EXPIRY_REMINDER_3M', days: 90 },
  { eventCode: 'EXPIRY_REMINDER_6M', days: 180 },
];

// eventKey 时间窗口后缀：按日/周/月去重，利用 RobotNotificationEvent.eventKey 唯一性
function timeWindowKey(frequency, now) {
  const d = now || new Date();
  if (frequency === 'weekly') {
    // ISO week: YYYY-Www
    const jan4 = new Date(d.getFullYear(), 0, 4);
    const week = Math.ceil(((d - jan4) / 86400000 + jan4.getDay() + 1) / 7);
    return `${d.getFullYear()}-W${String(week).padStart(2, '0')}`;
  }
  if (frequency === 'monthly') {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }
  // daily (default)
  return d.toISOString().slice(0, 10);
}

function dateOnly(d) {
  if (!d) return '-';
  return new Date(d).toISOString().slice(0, 10);
}

function daysUntil(expiryDate) {
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  const exp = new Date(expiryDate);
  exp.setHours(0, 0, 0, 0);
  return Math.ceil((exp - now) / 86400000);
}

/**
 * Run expiry reminder checks for all active batches.
 * Reads `expiry_reminder_frequency` config (daily/weekly/monthly).
 * Uses eventKey dedup so same batch+threshold+window fires only once.
 */
export async function runExpiryReminderScan(prisma, logger = console) {
  const frequency = (await getSystemConfigValue(prisma, 'expiry_reminder_frequency')) || 'daily';
  const windowKey = timeWindowKey(frequency);

  const now = new Date();
  const maxDays = 180;
  const maxExpiryDate = new Date(now.getTime() + maxDays * 86400000);

  // Only batches with quantity > 0 and expiry date within 6 months
  const batches = await prisma.e6PharmacyInventoryBatch.findMany({
    where: {
      expiryDate: { lte: maxExpiryDate, gte: now },
      quantity: { gt: 0 },
    },
    include: {
      product: { select: { id: true, name: true, productCode: true, specification: true, manufacturer: true, unit: true, retailPrice: true } },
      store: { select: { id: true, name: true } },
    },
  });

  logger.info?.(`[ExpiryScheduler] Found ${batches.length} active batches within 6 months`);

  let fired = 0;
  for (const batch of batches) {
    const days = daysUntil(batch.expiryDate);
    const priceStr = batch.product?.retailPrice !== null && batch.product?.retailPrice !== undefined ? String(Number(batch.product.retailPrice)) : '-';

    for (const { eventCode, days: threshold } of EXPIRY_THRESHOLDS) {
      if (days > threshold) continue;

      const eventKey = `${eventCode}:${batch.id}:${windowKey}`;
      await publishRobotNotificationEventSafely(prisma, {
        eventKey,
        eventCode,
        businessId: batch.productId,
        primaryStoreId: batch.storeId,
        relatedStoreIds: [batch.storeId],
        occurredAt: now,
        variables: {
          storeName: batch.store?.name || '-',
          eventTime: now.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' }),
          operatorName: '-',
          productName: batch.product?.name || '-',
          productCode: batch.product?.productCode || '-',
          specification: batch.product?.specification || '-',
          manufacturer: batch.product?.manufacturer || '-',
          price: priceStr,
          retailPrice: priceStr,
          batchNo: batch.batchNo || '-',
          inboundDate: dateOnly(batch.inboundDate),
          expiryDate: dateOnly(batch.expiryDate),
          daysLeft: String(days),
          quantity: String(Number(batch.quantity)),
          unit: batch.product?.unit || '',
          locationName: batch.locationName || '-',
        },
      }).catch((err) => logger.warn?.({ err, batchId: batch.id, eventCode }, 'Expiry reminder publish failed'));

      fired++;
      // Only send for the most urgent threshold that applies
      break;
    }
  }

  logger.info?.(`[ExpiryScheduler] Fired ${fired} expiry reminder events (window: ${windowKey})`);
  return { scanned: batches.length, fired, windowKey };
}

/**
 * Calculate milliseconds until next scheduled run time (HH:MM, e.g. "08:00").
 */
function msUntilNextRun(timeStr) {
  const [hh, mm] = (timeStr || '08:00').split(':').map(Number);
  const now = new Date();
  const next = new Date(now);
  next.setHours(hh || 8, mm || 0, 0, 0);
  if (next <= now) next.setDate(next.getDate() + 1);
  return next - now;
}

export function startExpiryReminderScheduler(fastify) {
  let timer = null;

  async function scheduleNext() {
    try {
      const timeStr = await getSystemConfigValue(fastify.prisma, 'expiry_reminder_time') || '08:00';
      const delay = msUntilNextRun(timeStr);
      fastify.log.info({ nextRunIn: `${Math.round(delay / 60000)} min`, time: timeStr }, '[ExpiryScheduler] Next expiry reminder run scheduled');
      timer = setTimeout(async () => {
        try {
          await runExpiryReminderScan(fastify.prisma, fastify.log);
        } catch (err) {
          fastify.log.error({ err }, '[ExpiryScheduler] Scan failed');
        }
        scheduleNext(); // schedule next day's run
      }, delay);
      timer.unref?.();
    } catch (err) {
      fastify.log.error({ err }, '[ExpiryScheduler] Failed to schedule');
    }
  }

  scheduleNext();

  fastify.addHook('onClose', async () => {
    if (timer) clearTimeout(timer);
  });
}
