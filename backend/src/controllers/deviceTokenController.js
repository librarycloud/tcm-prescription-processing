import { registerDeviceToken, unregisterDeviceToken } from '../services/deviceTokenService.js';

/**
 * POST /admin/device-tokens
 * Body: { platform: "android"|"ios"|"jpush", token: "...", deviceId?: "..." }
 * Registers or refreshes the calling admin's push token for this device.
 */
export async function registerTokenController(request, reply) {
  const { platform, token, deviceId, prescriptionNotify, transferNotify } = request.body ?? {};
  await registerDeviceToken(request.server.prisma, request.user.id, platform, token, deviceId, {
    prescriptionNotify,
    transferNotify,
  });
  return reply.status(204).send();
}

/**
 * DELETE /admin/device-tokens
 * Body: { token: "..." }
 * Removes a specific device token on logout.
 */
export async function unregisterTokenController(request, reply) {
  const { token } = request.body ?? {};
  await unregisterDeviceToken(request.server.prisma, request.user.id, token);
  return reply.status(204).send();
}

/**
 * GET /admin/device-tokens
 * Returns all active push tokens for the authenticated admin.
 */
export async function getTokensController(request, reply) {
  const tokens = await request.server.prisma.adminDeviceToken.findMany({
    where: { adminId: request.user.id },
    select: { id: true, platform: true, token: true, updatedAt: true },
    orderBy: { updatedAt: 'desc' },
  });
  return reply.send({ success: true, data: tokens });
}
