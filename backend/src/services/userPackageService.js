import bcrypt from 'bcrypt';
import { AppError } from '../utils/appError.js';
import { validatePhone } from '../utils/validators.js';
import { publicUser, signLoginToken } from './authService.js';
import { ROLES } from '../constants/roles.js';

function packageInclude() {
  return {
    verifier: { select: { id: true, phone: true, nickname: true } }
  };
}

export async function listMyPackages(prisma, user) {
  if (Number(user?.role) !== ROLES.USER) throw new AppError('无普通用户权限', 403);
  return prisma.package.findMany({
    where: { receiverPhone: user.phone, deletedAt: null },
    include: packageInclude(),
    orderBy: { createdAt: 'desc' }
  });
}

export async function getMyPackageDetail(prisma, user, id) {
  if (Number(user?.role) !== ROLES.USER) throw new AppError('包裹不存在', 404);
  const data = await prisma.package.findFirst({
    where: { id: Number(id), receiverPhone: user.phone, deletedAt: null },
    include: packageInclude()
  });

  if (!data) {
    throw new AppError('包裹不存在', 404);
  }

  return data;
}

export async function getCurrentUser(prisma, userId) {
  const user = await prisma.user.findUnique({
    where: { id: Number(userId) },
    include: { store: true }
  });
  if (!user) throw new AppError('用户不存在', 404);
  return publicUser(user);
}

export async function updateCurrentUser(prisma, jwt, currentUser, payload) {
  if (payload.email !== undefined) {
    throw new AppError('邮箱必须通过验证码绑定', 400);
  }
  const current = await prisma.user.findUnique({
    where: { id: Number(currentUser.id) },
    include: { store: true }
  });
  if (!current) throw new AppError('用户不存在', 404);

  const data = {};
  const phoneChanged = payload.phone !== undefined && String(payload.phone).trim() !== current.phone;

  if (payload.phone !== undefined) {
    validatePhone(payload.phone);
    const phone = String(payload.phone).trim();
    if (phoneChanged) {
      const owner = await prisma.user.findUnique({ where: { phone } });
      if (owner && owner.id !== current.id) throw new AppError('手机号已被使用', 400);
    }
    data.phone = phone;
    data.username = phone;
  }

  if (payload.nickname !== undefined) {
    data.nickname = String(payload.nickname || '').trim() || null;
  }

  if (payload.password !== undefined && String(payload.password).trim() !== '') {
    const password = String(payload.password);
    if (password.length < 6) throw new AppError('密码至少 6 位', 400);
    data.password = await bcrypt.hash(password, 10);
  }

  if (!Object.keys(data).length) {
    return { token: signLoginToken(jwt, current), user: publicUser(current) };
  }

  const user = await prisma.$transaction(async (tx) => {
    const updated = await tx.user.update({
      where: { id: current.id },
      data,
      include: { store: true }
    });

    if (phoneChanged && current.role === ROLES.USER) {
      await tx.package.updateMany({
        where: { receiverPhone: current.phone },
        data: { receiverPhone: data.phone }
      });
    }

    return updated;
  });

  return {
    token: signLoginToken(jwt, user),
    user: publicUser(user)
  };
}
