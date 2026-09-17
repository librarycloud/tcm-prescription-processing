import { receivePrescriptionController } from "../controllers/e6IntegrationController.js";
import {
  uploadInventoryController,
  uploadProductsController,
} from "../controllers/e6PharmacySyncController.js";

export default async function e6IntegrationRoutes(fastify) {
  fastify.post("/prescriptions", receivePrescriptionController);
  fastify.post("/pharmacy/products", uploadProductsController);
  fastify.post("/pharmacy/inventory", uploadInventoryController);
}
