import {
  androidPatchController,
  androidReleaseController,
  androidVersionController,
  appPatchesController,
  generatePatchController,
  syncAndroidVersionController,
} from "../controllers/appVersionController.js";
import { verifySuperAdmin, verifyToken } from "../middlewares/auth.js";

export default async function appRoutes(fastify) {
  // Public client endpoints
  fastify.get("/version/android", androidVersionController);
  fastify.get("/releases/:filename", androidReleaseController);
  fastify.get("/patches/:filename", androidPatchController);

  // Admin release & patch management
  fastify.post("/version/android/sync", { preHandler: [verifyToken, verifySuperAdmin] }, syncAndroidVersionController);
  fastify.get("/version/patches", { preHandler: [verifyToken, verifySuperAdmin] }, appPatchesController);
  fastify.post("/version/patches/generate", { preHandler: [verifyToken, verifySuperAdmin] }, generatePatchController);
}
