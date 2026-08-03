import { receivePrescriptionController } from "../controllers/e6IntegrationController.js";

export default async function e6IntegrationRoutes(fastify) {
  fastify.post("/prescriptions", receivePrescriptionController);
}
