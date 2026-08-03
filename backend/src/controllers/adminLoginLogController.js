import { listLoginLogs } from '../services/adminLoginLogService.js';
import { ok } from '../utils/response.js';

export async function listLoginLogsController(request, reply) {
  const data = await listLoginLogs(
    request.server.prisma,
    request.server.ipLookup,
    request.user,
    request.query || {}
  );
  return ok(reply, data);
}
