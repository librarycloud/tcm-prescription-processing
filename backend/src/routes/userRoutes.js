import {
  detailController,
  listController,
  meController,
  updateMeController
} from '../controllers/userPackageController.js';
import { verifyToken } from '../middlewares/auth.js';
import { sendEmailCodeController, verifyEmailCodeController } from '../controllers/emailController.js';

export default async function userRoutes(fastify) {
  fastify.addHook('preHandler', fastify.rateLimit());
  fastify.addHook('preHandler', verifyToken);

  fastify.get('/me', meController);
  fastify.put('/me', updateMeController);
  fastify.post('/email/send-code', sendEmailCodeController);
  fastify.post('/email/verify', { preHandler: fastify.rateLimit() }, verifyEmailCodeController);
  fastify.get('/packages', listController);
  fastify.get('/packages/:id', detailController);
}
