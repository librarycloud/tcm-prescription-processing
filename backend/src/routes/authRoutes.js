import {
  bindWechatController,
  bindWechatByPickupCodeController,
  loginController,
  logoutController,
  rebindWechatController,
  unbindWechatController,
  userLoginController,
  wechatLoginController,
  wechatStatusController
} from '../controllers/authController.js';
import { verifyToken } from '../middlewares/auth.js';

export default async function authRoutes(fastify) {
  fastify.addHook('preHandler', fastify.rateLimit());

  fastify.post('/login', { preHandler: fastify.rateLimit() }, loginController);
  fastify.post('/user-login', { preHandler: fastify.rateLimit() }, userLoginController);
  fastify.post('/wechat-login', { preHandler: fastify.rateLimit() }, wechatLoginController);
  fastify.post('/wechat-bind', { preHandler: verifyToken }, bindWechatController);
  fastify.post('/wechat-rebind', { preHandler: verifyToken }, rebindWechatController);
  fastify.post(
    '/wechat-bind-pickup',
    { preHandler: fastify.rateLimit() },
    bindWechatByPickupCodeController,
  );
  fastify.get('/wechat-status', { preHandler: verifyToken }, wechatStatusController);
  fastify.post('/wechat-unbind', { preHandler: verifyToken }, unbindWechatController);
  fastify.post('/logout', { preHandler: verifyToken }, logoutController);
}
