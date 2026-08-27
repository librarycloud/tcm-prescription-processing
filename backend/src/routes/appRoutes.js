import {
  androidReleaseController,
  androidVersionController,
  syncAndroidVersionController,
} from "../controllers/appVersionController.js";
import { verifySuperAdmin, verifyToken } from "../middlewares/auth.js";

export default async function appRoutes(fastify) {
  fastify.get("/version/android", androidVersionController);
  fastify.post("/version/android/sync", { preHandler: [verifyToken, verifySuperAdmin] }, syncAndroidVersionController);
  fastify.get("/releases/:filename", androidReleaseController);
}
