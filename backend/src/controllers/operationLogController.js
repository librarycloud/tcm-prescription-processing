import { ok } from '../utils/response.js';
import { listOperationLogs } from '../services/operationLogService.js';

export async function listOperationLogsController(request, reply) {
  return ok(reply, await listOperationLogs(request.server.prisma, request.user, request.query || {}));
}
