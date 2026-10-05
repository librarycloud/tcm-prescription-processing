/**
 * Device Token Service
 * Registers or refreshes an admin's push notification device token.
 * Tokens are globally unique; Android provider tokens from one install share a device ID.
 */

const MAX_DEVICES_PER_ADMIN = 10;

/**
 * Upserts a device token for the authenticated admin.
 * Devices beyond the per-admin limit are pruned (oldest first).
 *
 * @param {object} prisma
 * @param {number} adminId
 * @param {'android'|'ios'|'jpush'} platform
 * @param {string} token
 * @param {string} [deviceId]
 * @param {{ prescriptionNotify?: boolean, transferNotify?: boolean }} [notificationPreferences]
 */
export async function registerDeviceToken(prisma, adminId, platform, token, deviceId, notificationPreferences = {}) {
  if (!token || !['android', 'ios', 'jpush'].includes(platform)) return;
  const tokenStr = String(token).trim();
  if (!tokenStr) return;
  if (deviceId != null && typeof deviceId !== 'string') return;
  const deviceIdStr = deviceId?.trim() || null;
  if (deviceId != null && (!deviceIdStr || deviceIdStr.length > 64)) return;

  const preferenceData = {};
  const asBoolean = (value) => {
    if (typeof value === 'boolean') return value;
    if (value === 1 || value === '1' || value === 'true') return true;
    if (value === 0 || value === '0' || value === 'false') return false;
    return undefined;
  };
  if (notificationPreferences.prescriptionNotify !== undefined) {
    const value = asBoolean(notificationPreferences.prescriptionNotify);
    if (value !== undefined) preferenceData.prescriptionNotify = value;
  }
  if (notificationPreferences.transferNotify !== undefined) {
    const value = asBoolean(notificationPreferences.transferNotify);
    if (value !== undefined) preferenceData.transferNotify = value;
  }

  // Upsert: token is unique — if it belongs to another admin, move it here.
  // Execute sequentially without a long transaction to prevent MySQL gap-lock deadlocks on concurrent logins.
  await prisma.adminDeviceToken.upsert({
    where: { token: tokenStr },
    update: { adminId, platform, ...(deviceIdStr ? { deviceId: deviceIdStr } : {}), ...preferenceData },
    create: { adminId, platform, token: tokenStr, deviceId: deviceIdStr, ...preferenceData },
  });

  if (deviceIdStr) {
    const staleSameDevice = await prisma.adminDeviceToken.findMany({
      where: { adminId, deviceId: deviceIdStr, platform, token: { not: tokenStr } },
      select: { id: true }
    });
    if (staleSameDevice.length > 0) {
      await prisma.adminDeviceToken.deleteMany({
        where: { id: { in: staleSameDevice.map(s => s.id) } }
      });
    }
  }

  // Keep all provider tokens for the most recent MAX_DEVICES_PER_ADMIN devices.
  const tokens = await prisma.adminDeviceToken.findMany({
    where: { adminId },
    orderBy: { updatedAt: 'desc' },
    select: { id: true, deviceId: true },
  });
  
  if (tokens.length > MAX_DEVICES_PER_ADMIN) {
    const retainedDevices = new Set();
    const staleIds = [];
    for (const row of tokens) {
      const deviceKey = row.deviceId ? `device:${row.deviceId}` : `token:${row.id}`;
      if (retainedDevices.has(deviceKey)) continue;
      if (retainedDevices.size < MAX_DEVICES_PER_ADMIN) retainedDevices.add(deviceKey);
      else staleIds.push(row.id);
    }
    if (staleIds.length > 0) {
      await prisma.adminDeviceToken.deleteMany({ where: { id: { in: staleIds } } });
    }
  }
  
  console.log(`[DeviceToken] Registered token for adminId=${adminId}, platform=${platform}, token=${tokenStr.slice(0, 15)}...`);
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
