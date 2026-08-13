import {
  verifyManager,
  verifySuperAdmin,
  verifyToken,
} from "../middlewares/auth.js";
import {
  createController,
  deleteController,
  detailController,
  listController,
  pickupCodeDetailController,
  statsController,
  updateController,
  verifyController,
} from "../controllers/adminPackageController.js";
import {
  createUserController,
  deleteUserController,
  listUsersController,
  lookupUsersController,
  updateUserController,
} from "../controllers/adminUserController.js";
import { listLoginLogsController } from "../controllers/adminLoginLogController.js";
import { listOperationLogsController } from "../controllers/operationLogController.js";
import {
  packageNotificationsController,
  sendPackageNotificationController,
  settingsController,
  testSmsController,
  updateProviderController,
  updateTemplateController,
} from "../controllers/adminSmsController.js";
import {
  createStoreAdminController,
  deleteStoreAdminController,
  listStoreAdminsController,
  updateStoreAdminController,
} from "../controllers/storeAdminController.js";
import {
  emailSettingsController,
  testEmailController,
  updateEmailConfigController,
  updateEmailTemplateController,
} from "../controllers/adminEmailController.js";
import {
  createBatchController as createPlanBatchController,
  createController as createPlanController,
  calendarController as processingCalendarController,
  delayController as delayPlanController,
  deleteController as deletePlanController,
  generatePackageController as generatePlanPackageController,
  linkPackageController,
  listController as listPlanController,
  receiveNoticeController,
  reorderPrescriptionPlansController,
  reorderQueueController,
  restoreQueueController,
  transitionController as transitionPlanController,
  updateController as updatePlanController,
  workflowController as processingWorkflowController,
  scanController as scanProcessingPlanController,
  completeDispensingController,
  createManualEquipmentUsageController,
  deletePhotoController as deleteProcessingPhotoController,
  photoController as processingPhotoController,
  startEquipmentUsageController,
  startPackagingUsageController,
  finishEquipmentUsageController,
  transferFaultyEquipmentController,
  voidEquipmentUsageController,
} from "../controllers/processingPlanController.js";
import {
  createController as createEquipmentController,
  deleteController as deleteEquipmentController,
  listController as listEquipmentController,
  updateController as updateEquipmentController,
} from "../controllers/processingEquipmentController.js";
import {
  createController as createPrescriptionController,
  deleteAttachmentController as deletePrescriptionAttachmentController,
  deleteController as deletePrescriptionController,
  detailController as prescriptionDetailController,
  listController as listPrescriptionController,
  attachmentController as prescriptionAttachmentController,
  uploadAttachmentController as uploadPrescriptionAttachmentController,
  updateController as updatePrescriptionController,
} from "../controllers/prescriptionController.js";
import {
  createDictionaryController,
  createDoctorController,
  deleteDictionaryController,
  deleteDoctorController,
  listDictionariesController,
  listDoctorsController,
  updateDictionaryController,
  updateDoctorController,
} from "../controllers/basicDataController.js";
import {
  createPrintTemplateController,
  deletePrintTemplateController,
  listPrintTemplatesController,
  updatePrintTemplateController,
} from "../controllers/adminPrintTemplateController.js";
import {
  addReturnsController as addTransferReturnsController,
  cancelController as cancelTransferController,
  confirmOutboundController as confirmTransferOutboundController,
  confirmReturnController as confirmTransferReturnController,
  createController as createTransferController,
  detailController as transferDetailController,
  listController as listTransferController,
  statsController as transferStatsController,
  storesController as transferStoresController,
  updateController as updateTransferController,
  updateExpectedReturnDateController,
  updateReturnController as updateTransferReturnController,
} from "../controllers/storeTransferController.js";
import {
  assignHerbLocationController,
  exportHerbLocationsController,
  getHerbLocationLayoutController,
  herbLocationMoveTemplateController,
  herbLocationTemplateController,
  importHerbLocationMovesController,
  importHerbLocationsController,
  listHerbLocationsController,
  listHerbLocationStoresController,
  removeHerbLocationAssignmentController,
  updateHerbLocationLayoutController,
  updateHerbLocationAssignmentController,
  updateHerbController,
} from "../controllers/herbLocationController.js";
import {
  createRobotController,
  deleteRobotController,
  listRobotLogsController,
  listRobotsController,
  resetRobotEventController,
  retryRobotLogController,
  robotDetailController,
  robotLogDetailController,
  testRobotController,
  updateRobotController,
  updateRobotEventController,
} from "../controllers/robotNotificationController.js";
import {
  createProductController,
  importProductsController,
  listProductDiffLogsController,
  listProductsController,
  previewProductImportController,
  productDiffStatsController,
  productImportTemplateController,
  productStoresController,
  registerProductDiffController,
  reverseProductDiffController,
  updateProductController,
  writeOffProductDiffController,
} from "../controllers/productDifferenceController.js";
import {
  confirmImportController as confirmE6ImportController,
  createDoctorMappingController as createE6DoctorMappingController,
  deleteDoctorMappingController as deleteE6DoctorMappingController,
  getStoreConfigController as getE6StoreConfigController,
  importDetailController as e6ImportDetailController,
  listDoctorMappingsController as listE6DoctorMappingsController,
  listImportsController as listE6ImportsController,
  rejectImportController as rejectE6ImportController,
  revalidateImportController as revalidateE6ImportController,
  saveStoreConfigController as saveE6StoreConfigController,
  updateDoctorMappingController as updateE6DoctorMappingController,
} from "../controllers/e6IntegrationController.js";

export default async function adminRoutes(fastify) {
  fastify.addHook("preHandler", fastify.rateLimit());
  fastify.addHook("preHandler", verifyToken);
  fastify.addHook("preHandler", verifyManager);

  fastify.get("/stats", statsController);
  fastify.get("/herb-locations/stores", listHerbLocationStoresController);
  fastify.get("/herb-locations", listHerbLocationsController);
  fastify.get("/herb-locations/layout", getHerbLocationLayoutController);
  fastify.get("/herb-locations/export", exportHerbLocationsController);
  fastify.get("/herb-locations/template", herbLocationTemplateController);
  fastify.get(
    "/herb-locations/move-template",
    herbLocationMoveTemplateController,
  );
  fastify.post("/herb-locations/import", importHerbLocationsController);
  fastify.post(
    "/herb-locations/move-import",
    importHerbLocationMovesController,
  );
  fastify.post("/herb-locations/assignments", assignHerbLocationController);
  fastify.put(
    "/herb-locations/assignments/:id",
    updateHerbLocationAssignmentController,
  );
  fastify.put("/herb-locations/herbs/:id", updateHerbController);
  fastify.put("/herb-locations/layout", updateHerbLocationLayoutController);
  fastify.delete(
    "/herb-locations/assignments/:id",
    removeHerbLocationAssignmentController,
  );
  fastify.get("/products/stores", productStoresController);
  fastify.get("/e6/imports", listE6ImportsController);
  fastify.get("/e6/imports/:id", e6ImportDetailController);
  fastify.post("/e6/imports/:id/confirm", confirmE6ImportController);
  fastify.post("/e6/imports/:id/reject", rejectE6ImportController);
  fastify.post("/e6/imports/:id/revalidate", revalidateE6ImportController);
  fastify.get("/e6/stores/:storeId/config", getE6StoreConfigController);
  fastify.put("/e6/stores/:storeId/config", saveE6StoreConfigController);
  fastify.get("/e6/doctor-mappings", listE6DoctorMappingsController);
  fastify.post("/e6/doctor-mappings", createE6DoctorMappingController);
  fastify.put("/e6/doctor-mappings/:id", updateE6DoctorMappingController);
  fastify.delete("/e6/doctor-mappings/:id", deleteE6DoctorMappingController);
  fastify.get("/products/import-template", productImportTemplateController);
  fastify.post("/products/import-preview", previewProductImportController);
  fastify.post("/products/import", importProductsController);
  fastify.get("/products", listProductsController);
  fastify.post("/products", createProductController);
  fastify.put("/products/:id", updateProductController);
  fastify.get("/product-differences/stats", productDiffStatsController);
  fastify.get("/product-differences/logs", listProductDiffLogsController);
  fastify.post("/product-differences/register", registerProductDiffController);
  fastify.post("/product-differences/write-off", writeOffProductDiffController);
  fastify.post(
    "/product-differences/logs/:id/reverse",
    reverseProductDiffController,
  );
  fastify.get("/store-transfers", listTransferController);
  fastify.get("/store-transfers/stats", transferStatsController);
  fastify.get("/store-transfers/stores", transferStoresController);
  fastify.get("/store-transfers/:id", transferDetailController);
  fastify.post("/store-transfers", createTransferController);
  fastify.put("/store-transfers/:id", updateTransferController);
  fastify.put(
    "/store-transfers/:id/expected-return-date",
    updateExpectedReturnDateController,
  );
  fastify.post("/store-transfers/:id/returns", addTransferReturnsController);
  fastify.put(
    "/store-transfers/:id/returns/:returnId",
    updateTransferReturnController,
  );
  fastify.post(
    "/store-transfers/:id/confirm-outbound",
    confirmTransferOutboundController,
  );
  fastify.post(
    "/store-transfers/:id/returns/:returnId/confirm",
    confirmTransferReturnController,
  );
  fastify.post("/store-transfers/:id/cancel", cancelTransferController);
  fastify.get("/packages", listController);
  fastify.get("/packages/by-code/:pickupCode", pickupCodeDetailController);
  fastify.get("/packages/:id/notifications", packageNotificationsController);
  fastify.get("/packages/:id", detailController);
  fastify.post("/packages", createController);
  fastify.put("/packages/:id", updateController);
  fastify.delete("/packages/:id", deleteController);
  fastify.post(
    "/packages/verify",
    { preHandler: fastify.rateLimit() },
    verifyController,
  );
  fastify.get("/prescriptions", listPrescriptionController);
  fastify.get(
    "/prescriptions/:id/attachment",
    prescriptionAttachmentController,
  );
  fastify.post(
    "/prescriptions/:id/attachment",
    uploadPrescriptionAttachmentController,
  );
  fastify.delete(
    "/prescriptions/:id/attachment",
    deletePrescriptionAttachmentController,
  );
  fastify.get("/prescriptions/:id", prescriptionDetailController);
  fastify.post("/prescriptions", createPrescriptionController);
  fastify.put("/prescriptions/:id", updatePrescriptionController);
  fastify.delete("/prescriptions/:id", deletePrescriptionController);
  fastify.put(
    "/prescriptions/:prescriptionId/processing-plans/order",
    reorderPrescriptionPlansController,
  );
  fastify.get("/processing-plans", listPlanController);
  fastify.get("/processing-plans/calendar", processingCalendarController);
  fastify.get("/processing-plans/by-scan", scanProcessingPlanController);
  fastify.post("/processing-plans", createPlanController);
  fastify.post("/processing-plans/batch", createPlanBatchController);
  fastify.put("/processing-plans/queue", reorderQueueController);
  fastify.post("/processing-plans/queue/restore", restoreQueueController);
  fastify.put("/processing-plans/:id", updatePlanController);
  fastify.get("/processing-plans/:id/workflow", processingWorkflowController);
  fastify.post(
    "/processing-plans/:id/dispensing-complete",
    completeDispensingController,
  );
  fastify.get(
    "/processing-plans/:id/photos/:photoId",
    processingPhotoController,
  );
  fastify.delete(
    "/processing-plans/:id/photos/:photoId",
    deleteProcessingPhotoController,
  );
  fastify.post(
    "/processing-plans/:id/equipment-usages",
    startEquipmentUsageController,
  );
  fastify.post(
    "/processing-plans/:id/equipment-usages/manual",
    createManualEquipmentUsageController,
  );
  fastify.post(
    "/processing-plans/:id/equipment-usages/:usageId/start-packaging",
    startPackagingUsageController,
  );
  fastify.post(
    "/processing-plans/:id/equipment-usages/:usageId/finish",
    finishEquipmentUsageController,
  );
  fastify.post(
    "/processing-plans/:id/equipment-usages/:usageId/void",
    voidEquipmentUsageController,
  );
  fastify.post(
    "/processing-plans/:id/equipment-usages/:usageId/fault-transfer",
    transferFaultyEquipmentController,
  );
  fastify.post("/processing-plans/:id/transition", transitionPlanController);
  fastify.post(
    "/processing-plans/:id/generate-package",
    generatePlanPackageController,
  );
  fastify.post("/processing-plans/:id/delay", delayPlanController);
  fastify.post("/processing-plans/:id/receive-notice", receiveNoticeController);
  fastify.post("/processing-plans/:id/package", linkPackageController);
  fastify.delete("/processing-plans/:id", deletePlanController);
  fastify.get("/processing-equipment", listEquipmentController);
  fastify.post("/processing-equipment", createEquipmentController);
  fastify.put("/processing-equipment/:id", updateEquipmentController);
  fastify.delete("/processing-equipment/:id", deleteEquipmentController);
  fastify.get("/dictionaries", listDictionariesController);
  fastify.post(
    "/dictionaries",
    { preHandler: verifySuperAdmin },
    createDictionaryController,
  );
  fastify.put(
    "/dictionaries/:id",
    { preHandler: verifySuperAdmin },
    updateDictionaryController,
  );
  fastify.delete(
    "/dictionaries/:id",
    { preHandler: verifySuperAdmin },
    deleteDictionaryController,
  );
  fastify.get("/doctors", listDoctorsController);
  fastify.post(
    "/doctors",
    { preHandler: verifySuperAdmin },
    createDoctorController,
  );
  fastify.put(
    "/doctors/:id",
    { preHandler: verifySuperAdmin },
    updateDoctorController,
  );
  fastify.delete(
    "/doctors/:id",
    { preHandler: verifySuperAdmin },
    deleteDoctorController,
  );
  fastify.post(
    "/packages/:id/notifications",
    sendPackageNotificationController,
  );
  fastify.get(
    "/sms/settings",
    { preHandler: verifySuperAdmin },
    settingsController,
  );
  fastify.put(
    "/sms/providers/:provider",
    { preHandler: verifySuperAdmin },
    updateProviderController,
  );
  fastify.put(
    "/sms/templates/:id",
    { preHandler: verifySuperAdmin },
    updateTemplateController,
  );
  fastify.post(
    "/sms/test",
    { preHandler: verifySuperAdmin },
    testSmsController,
  );
  fastify.get(
    "/email/settings",
    { preHandler: verifySuperAdmin },
    emailSettingsController,
  );
  fastify.put(
    "/email/settings",
    { preHandler: verifySuperAdmin },
    updateEmailConfigController,
  );
  fastify.put(
    "/email/templates/:id",
    { preHandler: verifySuperAdmin },
    updateEmailTemplateController,
  );
  fastify.post(
    "/email/test",
    { preHandler: verifySuperAdmin },
    testEmailController,
  );
  fastify.get("/print-templates", listPrintTemplatesController);
  fastify.post("/print-templates", createPrintTemplateController);
  fastify.put("/print-templates/:id", updatePrintTemplateController);
  fastify.delete("/print-templates/:id", deletePrintTemplateController);
  fastify.get("/users/match", lookupUsersController);
  fastify.get("/users", listUsersController);
  fastify.post(
    "/users",
    { preHandler: verifySuperAdmin },
    createUserController,
  );
  fastify.put("/users/:id", updateUserController);
  fastify.delete(
    "/users/:id",
    { preHandler: verifySuperAdmin },
    deleteUserController,
  );
  fastify.get(
    "/store-admins",
    { preHandler: verifySuperAdmin },
    listStoreAdminsController,
  );
  fastify.post(
    "/store-admins",
    { preHandler: verifySuperAdmin },
    createStoreAdminController,
  );
  fastify.put(
    "/store-admins/:id",
    { preHandler: verifySuperAdmin },
    updateStoreAdminController,
  );
  fastify.delete(
    "/store-admins/:id",
    { preHandler: verifySuperAdmin },
    deleteStoreAdminController,
  );
  fastify.get(
    "/login-logs",
    { preHandler: [fastify.rateLimit(), verifySuperAdmin] },
    listLoginLogsController,
  );
  fastify.get(
    "/operation-logs",
    { preHandler: verifySuperAdmin },
    listOperationLogsController,
  );
  fastify.get("/robot-notifications/robots", listRobotsController);
  fastify.post("/robot-notifications/robots", createRobotController);
  fastify.get("/robot-notifications/robots/:id", robotDetailController);
  fastify.put("/robot-notifications/robots/:id", updateRobotController);
  fastify.delete("/robot-notifications/robots/:id", deleteRobotController);
  fastify.post("/robot-notifications/robots/:id/test", testRobotController);
  fastify.put(
    "/robot-notifications/robots/:id/events/:eventCode",
    updateRobotEventController,
  );
  fastify.post(
    "/robot-notifications/robots/:id/events/:eventCode/reset-template",
    resetRobotEventController,
  );
  fastify.get("/robot-notifications/logs", listRobotLogsController);
  fastify.get("/robot-notifications/logs/:id", robotLogDetailController);
  fastify.post("/robot-notifications/logs/:id/retry", retryRobotLogController);
}
