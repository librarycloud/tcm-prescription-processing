import bcrypt from 'bcrypt';
import { AppError } from '../utils/appError.js';
import { validatePhone, normalizeOptionalPhone, normalizeOptionalUsername, requireAccountIdentifier } from '../utils/validators.js';
import { publicUser, signLoginToken } from './authService.js';
import { ROLES } from '../constants/roles.js';
import { withPickupQrContent } from '../utils/pickupQr.js';

function packageInclude() {
  return {
    verifier: { select: { id: true, phone: true, nickname: true } }
  };
}

export async function listMyPackages(prisma, user) {
  if (Number(user?.role) !== ROLES.USER) throw new AppError('无普通用户权限', 403);
  const data = await prisma.package.findMany({
    where: { receiverPhone: user.phone, deletedAt: null },
    include: packageInclude(),
    orderBy: { createdAt: 'desc' }
  });
  return data.map(withPickupQrContent);
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

  return withPickupQrContent(data);
}

export async function getCurrentUser(prisma, currentUser) {
  const isAdmin = currentUser.accountType === 'admin';
  const user = await (isAdmin ? prisma.admin : prisma.user).findUnique({
    where: { id: Number(currentUser.id) },
    include: isAdmin ? { store: true } : undefined
  });
  if (!user) throw new AppError('用户不存在', 404);
  return publicUser(user);
}

export async function updateCurrentUser(prisma, jwt, authSessions, currentUser, payload) {
  if (payload.email !== undefined) {
    throw new AppError('邮箱必须通过验证码绑定', 400);
  }
  const isAdmin = currentUser.accountType === 'admin';
  const repository = isAdmin ? prisma.admin : prisma.user;
  const current = await repository.findUnique({
    where: { id: Number(currentUser.id) },
    include: isAdmin ? { store: true } : undefined
  });
  if (!current) throw new AppError('用户不存在', 404);

  const data = {};
  const phoneChanged = payload.phone !== undefined &&
    (isAdmin ? normalizeOptionalPhone(payload.phone) : String(payload.phone).trim()) !== current.phone;

  if (payload.phone !== undefined) {
    const phone = isAdmin ? normalizeOptionalPhone(payload.phone) : String(payload.phone).trim();
    if (!isAdmin) validatePhone(phone);
    if (phoneChanged) {
      if (phone) {
        const owner = await repository.findUnique({ where: { phone } });
        if (owner && owner.id !== current.id) throw new AppError('手机号已被使用', 400);
      }
    }
    data.phone = phone;
  }

  if (payload.username !== undefined) {
    const username = normalizeOptionalUsername(payload.username);
    if (username) {
      const owner = await repository.findFirst({
        where: { username, id: { not: current.id } },
        select: { id: true },
      });
      if (owner) throw new AppError('用户名已被使用', 409);
    }
    data.username = username;
  }

  if (isAdmin) {
    requireAccountIdentifier(
      data.phone !== undefined ? data.phone : current.phone,
      data.username !== undefined ? data.username : current.username,
    );
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
    return { token: await signLoginToken(jwt, authSessions, current), user: publicUser(current) };
  }

  if (phoneChanged || data.password || data.username !== undefined && data.username !== current.username) {
    await authSessions.revokeAccount({
      accountType: isAdmin ? 'admin' : 'user',
      accountId: Number(current.id)
    });
  }

  const user = await prisma.$transaction(async (tx) => {
    const updated = await (isAdmin ? tx.admin : tx.user).update({
      where: { id: current.id },
      data,
      include: isAdmin ? { store: true } : undefined
    });

    if (phoneChanged && !isAdmin) {
      await tx.package.updateMany({
        where: { receiverPhone: current.phone },
        data: { receiverPhone: data.phone }
      });
    }

    return updated;
  });

  return {
    token: await signLoginToken(jwt, authSessions, user),
    user: publicUser(user)
  };
}
