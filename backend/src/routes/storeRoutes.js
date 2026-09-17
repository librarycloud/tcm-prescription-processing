import { verifySuperAdmin, verifyToken } from '../middlewares/auth.js';
import {
  createStoreController,
  deleteStoreController,
  getStoreController,
  listStoresController,
  updateStoreController
} from '../controllers/storeController.js';

export default async function storeRoutes(fastify) {
  fastify.addHook('preHandler', fastify.rateLimit());
  fastify.addHook('preHandler', verifyToken);
  fastify.addHook('preHandler', verifySuperAdmin);

  fastify.get('/stores', listStoresController);
  fastify.get('/stores/:id', getStoreController);
  fastify.post('/stores', createStoreController);
  fastify.put('/stores/:id', updateStoreController);
  fastify.delete('/stores/:id', deleteStoreController);
}
