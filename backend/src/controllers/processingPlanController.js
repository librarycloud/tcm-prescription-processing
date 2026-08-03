import { ok } from "../utils/response.js";
import {
  createProcessingPlan,
  createProcessingPlanBatch,
  delayProcessingPlan,
  deleteProcessingPlan,
  getProcessingCalendar,
  generateProcessingPlanPackage,
  linkPackage,
  listProcessingPlans,
  receiveProcessingNotice,
  reorderPrescriptionPlans,
  reorderProcessingQueue,
  restoreProcessingQueue,
  transitionProcessingPlan,
  updateProcessingPlan,
} from "../services/processingPlanService.js";

export async function listController(request, reply) {
  return ok(
    reply,
    await listProcessingPlans(
      request.server.prisma,
      request.user,
      request.query || {},
    ),
  );
}
export async function createController(request, reply) {
  return ok(
    reply,
    await createProcessingPlan(
      request.server.prisma,
      request.user,
      request.body || {},
    ),
    "创建成功",
  );
}
export async function createBatchController(request, reply) {
  return ok(
    reply,
    await createProcessingPlanBatch(
      request.server.prisma,
      request.user,
      request.body || {},
    ),
    "创建成功",
  );
}
export async function updateController(request, reply) {
  return ok(
    reply,
    await updateProcessingPlan(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body || {},
    ),
    "更新成功",
  );
}
export async function transitionController(request, reply) {
  return ok(
    reply,
    await transitionProcessingPlan(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body || {},
    ),
    "状态更新成功",
  );
}
export async function generatePackageController(request, reply) {
  return ok(
    reply,
    await generateProcessingPlanPackage(
      request.server.prisma,
      request.user,
      request.params.id,
    ),
    "包裹生成成功",
  );
}
export async function deleteController(request, reply) {
  return ok(
    reply,
    await deleteProcessingPlan(
      request.server.prisma,
      request.user,
      request.params.id,
    ),
    "删除成功",
  );
}
export async function linkPackageController(request, reply) {
  return ok(
    reply,
    await linkPackage(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body?.packageId,
    ),
    "关联成功",
  );
}
export async function delayController(request, reply) {
  return ok(
    reply,
    await delayProcessingPlan(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body || {},
    ),
    "延期成功",
  );
}
export async function receiveNoticeController(request, reply) {
  return ok(
    reply,
    await receiveProcessingNotice(
      request.server.prisma,
      request.user,
      request.params.id,
      request.body || {},
    ),
    "已安排加工日期",
  );
}
export async function reorderQueueController(request, reply) {
  return ok(
    reply,
    await reorderProcessingQueue(
      request.server.prisma,
      request.user,
      request.body || {},
    ),
    "顺序已更新",
  );
}
export async function reorderPrescriptionPlansController(request, reply) {
  return ok(
    reply,
    await reorderPrescriptionPlans(
      request.server.prisma,
      request.user,
      request.params.prescriptionId,
      request.body || {},
    ),
    "批次顺序已更新",
  );
}
export async function restoreQueueController(request, reply) {
  return ok(
    reply,
    await restoreProcessingQueue(
      request.server.prisma,
      request.user,
      request.body || {},
    ),
    "已恢复默认排序",
  );
}
export async function calendarController(request, reply) {
  return ok(
    reply,
    await getProcessingCalendar(
      request.server.prisma,
      request.user,
      request.query || {},
    ),
  );
}
