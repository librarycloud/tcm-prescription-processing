import {
  bindWechat,
  bindWechatByPickupCode,
  getWechatStatus,
  login,
  rebindWechat,
  unbindWechat,
  userLogin,
  wechatLogin
} from '../services/authService.js';
import { recordLoginLog } from '../services/loginLogService.js';
import { recordOperation } from '../services/operationLogService.js';
import { ok } from '../utils/response.js';

async function runLoggedLogin(request, loginType, operation) {
  const attemptedPhone = String(request.body?.phone || '').trim() || null;
  try {
    const data = await operation();
    const completed = Boolean(data?.token && data?.user);
    await recordLoginLog(request, {
      userId: data?.user?.id,
      accountType: data?.user?.role === 1 ? 'user' : 'admin',
      storeId: data?.user?.storeId,
      phone: data?.user?.phone || attemptedPhone,
      loginType,
      success: completed,
      message: completed ? '登录成功' : '需要绑定用户信息'
    });
    if (completed) {
      await recordOperation(request.server.prisma, {
        ...data.user,
        ip: request.ip,
        userAgent: request.headers['user-agent']
      }, {
        module: 'auth',
        action: 'login',
        targetId: data.user.id,
        description: '登录系统'
      });
    }
    return data;
  } catch (error) {
    await recordLoginLog(request, {
      phone: attemptedPhone,
      loginType,
      success: false,
      message: error.message || '登录失败'
    });
    throw error;
  }
}

export async function loginController(request, reply) {
  const data = await runLoggedLogin(request, 'admin', () =>
    login(request.server.prisma, request.server.jwt, request.server.authSessions, request.body || {})
  );
  return ok(reply, data);
}

export async function userLoginController(request, reply) {
  const data = await runLoggedLogin(request, 'user', () =>
    userLogin(request.server.prisma, request.server.jwt, request.server.authSessions, request.body || {})
  );
  return ok(reply, data);
}

export async function wechatLoginController(request, reply) {
  const data = await runLoggedLogin(request, 'wechat', () =>
    wechatLogin(request.server.prisma, request.server.jwt, request.server.authSessions, request.body || {})
  );
  return ok(reply, data);
}

export async function bindWechatController(request, reply) {
  const data = await bindWechat(request.server.prisma, request.user, request.body || {});
  await recordOperation(request.server.prisma, request.user, {
    module: 'auth',
    action: 'wechat_bind',
    targetId: request.user.id,
    description: '绑定微信'
  });
  return ok(reply, data, '绑定成功');
}

export async function rebindWechatController(request, reply) {
  const data = await rebindWechat(request.server.prisma, request.user, request.body || {});
  await recordOperation(request.server.prisma, request.user, {
    module: 'auth',
    action: 'wechat_rebind',
    targetId: request.user.id,
    description: '修改微信绑定'
  });
  return ok(reply, data, '微信绑定已修改');
}

export async function bindWechatByPickupCodeController(request, reply) {
  const data = await runLoggedLogin(request, 'wechat-bind-pickup', () =>
    bindWechatByPickupCode(request.server.prisma, request.server.jwt, request.server.authSessions, request.body || {})
  );
  return ok(reply, data, '绑定成功');
}

export async function wechatStatusController(request, reply) {
  return ok(reply, await getWechatStatus(request.server.prisma, request.user));
}

export async function unbindWechatController(request, reply) {
  const data = await unbindWechat(request.server.prisma, request.user, request.body || {});
  await recordOperation(request.server.prisma, request.user, {
    module: 'auth',
    action: 'wechat_unbind',
    targetId: request.user.id,
    description: '解除微信绑定'
  });
  return ok(reply, data, '已解除微信绑定');
}

export async function logoutController(request, reply) {
  await request.server.authSessions.revoke({
    accountType: request.user.accountType,
    accountId: request.user.id,
    jti: request.user.jti
  });
  await recordOperation(request.server.prisma, request.user, {
    module: 'auth',
    action: 'logout',
    targetId: request.user.id,
    description: '退出系统'
  });
  return ok(reply, null, '退出成功');
}
