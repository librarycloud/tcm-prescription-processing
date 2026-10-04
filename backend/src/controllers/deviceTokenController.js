import { registerDeviceToken, unregisterDeviceToken } from '../services/deviceTokenService.js';

/**
 * POST /admin/device-tokens
 * Body: { platform: "android"|"ios", token: "..." }
 * Registers or refreshes the calling admin's push token for this device.
 */
export async function registerTokenController(request, reply) {
  const { platform, token } = request.body ?? {};
  await registerDeviceToken(request.server.prisma, request.user.id, platform, token);
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
