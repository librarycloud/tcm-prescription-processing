import {
  detailController,
  listController,
  meController,
  updateMeController
} from '../controllers/userPackageController.js';
import { verifyToken, verifyUser } from '../middlewares/auth.js';
import { sendEmailCodeController, verifyEmailCodeController } from '../controllers/emailController.js';

export default async function userRoutes(fastify) {
  fastify.addHook('preHandler', fastify.rateLimit());
  fastify.addHook('preHandler', verifyToken);

  fastify.get('/me', meController);
  fastify.put('/me', updateMeController);
  fastify.post('/email/send-code', { preHandler: verifyUser }, sendEmailCodeController);
  fastify.post('/email/verify', { preHandler: [fastify.rateLimit(), verifyUser] }, verifyEmailCodeController);
  fastify.get('/packages', { preHandler: verifyUser }, listController);
  fastify.get('/packages/:id', { preHandler: verifyUser }, detailController);
}
