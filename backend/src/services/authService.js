import bcrypt from "bcrypt";
import { config } from "../config.js";
import { AppError } from "../utils/appError.js";
import { validatePhone, required } from "../utils/validators.js";
import { ROLES, isManager } from "../constants/roles.js";
import { RECORD_STATUS } from "../constants/recordStatus.js";

export function publicUser(user) {
  return {
    id: user.id,
    username: user.username,
    phone: user.phone,
    role: user.role,
    storeId: user.storeId,
    store: user.store
      ? {
          id: user.store.id,
          name: user.store.name,
          code: user.store.code,
          status: user.store.status,
        }
      : null,
    nickname: user.nickname,
    email: user.email,
    emailVerified: Boolean(user.emailVerifiedAt),
    emailVerifiedAt: user.emailVerifiedAt,
    openidBound: Boolean(user.openid),
    wechatBoundAt: user.wechatBoundAt,
  };
}

export function signLoginToken(jwt, user) {
  return jwt.sign(
    {
      id: user.id,
      role: user.role,
      storeId: user.storeId || null,
      phone: user.phone,
    },
    { expiresIn: "7d" },
  );
}

export async function login(prisma, jwt, payload) {
  const { phone, password } = payload;
  validatePhone(phone);
  required(password, "密码");

  const user = await prisma.user.findUnique({
    where: { phone },
    include: { store: true },
  });
  if (!user || !isManager(user) || user.status !== RECORD_STATUS.ENABLED) {
    throw new AppError("手机号或密码错误", 401);
  }
  if (
    user.role === ROLES.STORE_ADMIN &&
    (!user.storeId ||
      user.store?.status !== RECORD_STATUS.ENABLED ||
      user.store?.deletedAt)
  ) {
    throw new AppError("账号所属门店已停用，请联系全局管理员", 403);
  }

  const matched = await bcrypt.compare(password, user.password);
  if (!matched) throw new AppError("手机号或密码错误", 401);

  return {
    token: signLoginToken(jwt, user),
    user: publicUser(user),
  };
}

export async function userLogin(prisma, jwt, payload) {
  const { phone, password } = payload;
  validatePhone(phone);
  required(password, "密码");

  const user = await prisma.user.findUnique({
    where: { phone },
    include: { store: true },
  });
  if (
    !user ||
    user.role !== ROLES.USER ||
    user.status !== RECORD_STATUS.ENABLED
  ) {
    throw new AppError("手机号或密码错误", 401);
  }

  const matched = await bcrypt.compare(password, user.password);
  if (!matched) throw new AppError("手机号或密码错误", 401);

  return {
    token: signLoginToken(jwt, user),
    user: publicUser(user),
  };
}

async function codeToWechatIdentity(code) {
  if (!config.wxAppId || !config.wxSecret) {
    throw new AppError("微信登录未配置 WX_APPID 或 WX_SECRET", 500);
  }

  required(code, "微信登录 code");
  const url = new URL("https://api.weixin.qq.com/sns/jscode2session");
  url.searchParams.set("appid", config.wxAppId);
  url.searchParams.set("secret", config.wxSecret);
  url.searchParams.set("js_code", code);
  url.searchParams.set("grant_type", "authorization_code");

  const response = await fetch(url);
  const data = await response.json();

  if (!data.openid) {
    throw new AppError(data.errmsg || "微信登录失败", 400);
  }

  return { openid: data.openid, unionid: data.unionid || null };
}

export async function wechatLogin(prisma, jwt, payload) {
  const { openid, unionid } = await codeToWechatIdentity(payload.code);
  const user = await prisma.user.findUnique({
    where: { openid },
    include: { store: true },
  });

  if (user) {
    if (user.status !== RECORD_STATUS.ENABLED)
      throw new AppError("账号已停用", 403);
    if (
      user.role === ROLES.STORE_ADMIN &&
      (user.store?.status !== RECORD_STATUS.ENABLED || user.store?.deletedAt)
    ) {
      throw new AppError("账号所属门店已停用", 403);
    }
    return {
      requiresBind: false,
      token: signLoginToken(jwt, user),
      user: publicUser(user),
    };
  }

  if (unionid) {
    const unionidUser = await prisma.user.findUnique({
      where: { unionid },
      include: { store: true },
    });
    if (unionidUser) {
      if (unionidUser.status !== RECORD_STATUS.ENABLED)
        throw new AppError("账号已停用", 403);
      if (
        unionidUser.role === ROLES.STORE_ADMIN &&
        (unionidUser.store?.status !== RECORD_STATUS.ENABLED || unionidUser.store?.deletedAt)
      ) {
        throw new AppError("账号所属门店已停用", 403);
      }
      return {
        requiresBind: false,
        token: signLoginToken(jwt, unionidUser),
        user: publicUser(unionidUser),
      };
    }
  }

  return {
    requiresBind: true,
    bindToken: jwt.sign(
      { type: 'wechat_pickup_bind', openid, unionid },
      { expiresIn: '15m' },
    ),
  };
}

export async function bindWechat(prisma, currentUser, payload) {
  const { openid, unionid } = await codeToWechatIdentity(payload.code);
  const user = await prisma.user.findUnique({
    where: { id: Number(currentUser.id) },
    include: { store: true },
  });
  if (!user || user.status !== RECORD_STATUS.ENABLED) {
    throw new AppError("账号已停用", 403);
  }
  if (
    user.role === ROLES.STORE_ADMIN &&
    (!user.storeId || user.store?.status !== RECORD_STATUS.ENABLED || user.store?.deletedAt)
  ) {
    throw new AppError("账号所属门店已停用", 403);
  }
  if (user.openid && user.openid !== openid) {
    throw new AppError("账号已绑定其他微信，请先解除绑定", 400);
  }

  const openidOwner = await prisma.user.findUnique({ where: { openid } });
  if (openidOwner && openidOwner.id !== user.id) {
    throw new AppError("该微信已绑定其他账号", 400);
  }
  if (unionid) {
    const unionidOwner = await prisma.user.findUnique({ where: { unionid } });
    if (unionidOwner && unionidOwner.id !== user.id) {
      throw new AppError("该微信已绑定其他账号", 400);
    }
  }

  const updated = await prisma.user.update({
    where: { id: user.id },
    data: {
      openid,
      unionid: unionid || undefined,
      wechatBoundAt: new Date(),
    },
    include: { store: true },
  });

  return {
    user: publicUser(updated),
  };
}

export async function rebindWechat(prisma, currentUser, payload) {
  const password = String(payload.password || '');
  required(password, '当前密码');
  const user = await prisma.user.findUnique({
    where: { id: Number(currentUser.id) },
    include: { store: true },
  });
  if (!user || user.status !== RECORD_STATUS.ENABLED) {
    throw new AppError('账号已停用', 403);
  }
  if (
    user.role === ROLES.STORE_ADMIN &&
    (!user.storeId || user.store?.status !== RECORD_STATUS.ENABLED || user.store?.deletedAt)
  ) {
    throw new AppError('账号所属门店已停用', 403);
  }
  const matched = await bcrypt.compare(password, user.password);
  if (!matched) throw new AppError('当前密码错误', 400);

  const { openid, unionid } = await codeToWechatIdentity(payload.code);
  const openidOwner = await prisma.user.findUnique({ where: { openid } });
  if (openidOwner && openidOwner.id !== user.id) {
    throw new AppError('该微信已绑定其他账号', 400);
  }
  if (unionid) {
    const unionidOwner = await prisma.user.findUnique({ where: { unionid } });
    if (unionidOwner && unionidOwner.id !== user.id) {
      throw new AppError('该微信已绑定其他账号', 400);
    }
  }

  const updated = await prisma.user.update({
    where: { id: user.id },
    data: { openid, unionid: unionid || undefined, wechatBoundAt: new Date() },
    include: { store: true },
  });
  return { user: publicUser(updated) };
}

export async function bindWechatByPickupCode(prisma, jwt, payload) {
  const { bindToken, phone, pickupCode } = payload;
  required(bindToken, '绑定凭证');
  validatePhone(phone);
  required(pickupCode, '取货码');
  const normalizedPhone = String(phone).trim();

  let decoded;
  try {
    decoded = await jwt.verify(bindToken);
  } catch {
    throw new AppError('绑定凭证已过期，请重新微信登录', 401);
  }
  if (decoded.type !== 'wechat_pickup_bind' || !decoded.openid) {
    throw new AppError('绑定凭证无效', 401);
  }

  const boundUser = await prisma.user.findUnique({
    where: { openid: decoded.openid },
    include: { store: true },
  });
  if (boundUser) {
    if (boundUser.status !== RECORD_STATUS.ENABLED) throw new AppError('账号已停用', 403);
    return { token: signLoginToken(jwt, boundUser), user: publicUser(boundUser) };
  }

  const packageRecord = await prisma.package.findUnique({
    where: { pickupCode: String(pickupCode).replace(/\D/g, '') },
  });
  if (
    !packageRecord ||
    packageRecord.deletedAt ||
    packageRecord.receiverPhone !== normalizedPhone
  ) {
    throw new AppError('手机号或取货码不匹配', 400);
  }

  const existingUser = await prisma.user.findUnique({
    where: { phone: normalizedPhone },
  });
  if (existingUser && existingUser.role !== ROLES.USER) {
    throw new AppError('管理员账号不能通过取货码绑定微信', 403);
  }
  if (existingUser && existingUser.status !== RECORD_STATUS.ENABLED) {
    throw new AppError('账号已停用', 403);
  }
  if (decoded.unionid) {
    const unionidOwner = await prisma.user.findUnique({ where: { unionid: decoded.unionid } });
    if (unionidOwner && unionidOwner.id !== existingUser?.id) {
      throw new AppError('该微信已绑定其他账号', 400);
    }
  }

  const placeholderPassword = await bcrypt.hash(`wx:${decoded.openid}:${Date.now()}`, 10);
  const user = existingUser
    ? await prisma.user.update({
        where: { id: existingUser.id },
        data: {
          openid: decoded.openid,
          unionid: decoded.unionid || undefined,
          wechatBoundAt: new Date(),
        },
        include: { store: true },
      })
    : await prisma.user.create({
        data: {
          username: normalizedPhone,
          password: placeholderPassword,
          phone: normalizedPhone,
          openid: decoded.openid,
          unionid: decoded.unionid || undefined,
          wechatBoundAt: new Date(),
          role: ROLES.USER,
          nickname: packageRecord.receiverName,
        },
        include: { store: true },
      });

  return { token: signLoginToken(jwt, user), user: publicUser(user) };
}

export async function getWechatStatus(prisma, userId) {
  const user = await prisma.user.findUnique({
    where: { id: Number(userId) },
    select: { openid: true, wechatBoundAt: true },
  });
  if (!user) throw new AppError('用户不存在', 404);
  return { bound: Boolean(user.openid), boundAt: user.wechatBoundAt };
}

export async function unbindWechat(prisma, currentUser, payload) {
  const password = String(payload.password || '');
  required(password, '密码');
  const user = await prisma.user.findUnique({ where: { id: Number(currentUser.id) } });
  if (!user) throw new AppError('用户不存在', 404);
  const matched = await bcrypt.compare(password, user.password);
  if (!matched) throw new AppError('密码错误', 400);

  const updated = await prisma.user.update({
    where: { id: user.id },
    data: { openid: null, unionid: null, wechatBoundAt: null },
    include: { store: true },
  });
  return { user: publicUser(updated) };
}
