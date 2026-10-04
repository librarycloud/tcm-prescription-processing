/**
 * Device Token Service
 * Registers or refreshes an admin's push notification device token.
 * One token is unique globally — if it moves to a different admin, it's reassigned.
 */

const MAX_TOKENS_PER_ADMIN = 10;

/**
 * Upserts a device token for the authenticated admin.
 * Old tokens beyond the per-admin limit are pruned (oldest first).
 *
 * @param {object} prisma
 * @param {number} adminId
 * @param {'android'|'ios'} platform
 * @param {string} token
 */
export async function registerDeviceToken(prisma, adminId, platform, token) {
  if (!token || !['android', 'ios', 'jpush'].includes(platform)) return;
  const tokenStr = String(token).trim();
  if (!tokenStr) return;

    await prisma.$transaction(async (tx) => {
    // We no longer indiscriminately delete by platform here, because a user might 
    // be logged into a Sony phone (FCM) and a Huawei phone (JPush) simultaneously.

    // Upsert: token is unique — if it belongs to another admin, move it here.
    await tx.adminDeviceToken.upsert({
      where: { token: tokenStr },
      update: { adminId, platform },
      create: { adminId, platform, token: tokenStr },
    });

    // Keep only the most recent MAX_TOKENS_PER_ADMIN tokens per admin.
    const tokens = await tx.adminDeviceToken.findMany({
      where: { adminId },
      orderBy: { updatedAt: 'desc' },
      select: { id: true },
    });
    if (tokens.length > MAX_TOKENS_PER_ADMIN) {
      const staleIds = tokens.slice(MAX_TOKENS_PER_ADMIN).map((t) => t.id);
      await tx.adminDeviceToken.deleteMany({ where: { id: { in: staleIds } } });
    }
    console.log(`[DeviceToken] Registered token for adminId=${adminId}, platform=${platform}, token=${tokenStr.slice(0, 15)}...`);
  });
}

/**
 * Removes a specific device token (called on logout from a device).
 *
 * @param {object} prisma
 * @param {number} adminId
 * @param {string} token
 */
export async function unregisterDeviceToken(prisma, adminId, token) {
  if (!token) return;
  const tokenStr = String(token).trim();
  const result = await prisma.adminDeviceToken.deleteMany({
    where: { adminId, token: tokenStr },
  });
  console.log(`[DeviceToken] Unregistered token for adminId=${adminId}, token=${tokenStr.slice(0, 15)}..., deletedCount=${result.count}`);
}

/**
 * Removes all device tokens for an admin (full logout / account deactivation).
 *
 * @param {object} prisma
 * @param {number} adminId
 */
export async function clearDeviceTokens(prisma, adminId) {
  await prisma.adminDeviceToken.deleteMany({ where: { adminId } });
}
