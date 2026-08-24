import { sendVerificationCode, verifyEmailCode } from '../services/emailService.js';
import { publicUser, signLoginToken } from '../services/authService.js';
import { ok } from '../utils/response.js';

export async function sendEmailCodeController(request, reply) {
  const data = await sendVerificationCode(
    request.server.prisma,
    request.user.id,
    request.body?.email
  );
  return ok(reply, data, '验证码已发送');
}

export async function verifyEmailCodeController(request, reply) {
  const user = await verifyEmailCode(
    request.server.prisma,
    request.user.id,
    request.body?.email,
    request.body?.code,
    publicUser
  );
  return ok(
    reply,
    { user, token: await signLoginToken(request.server.jwt, request.server.authSessions, user) },
    '邮箱绑定成功'
  );
}
