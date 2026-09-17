import { toPositiveInt } from '../utils/validators.js';
import { isSuperAdmin } from '../constants/roles.js';

function parseSuccess(value) {
  if (value === 1 || value === '1') return 1;
  if (value === 0 || value === '0') return 0;
  return null;
}

function parseDate(value, endOfDay = false) {
  if (!value) return null;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  if (endOfDay) date.setDate(date.getDate() + 1);
  return date;
}

async function buildWhere(prisma, actor, query) {
  const where = isSuperAdmin(actor) ? {} : { storeId: Number(actor.storeId) };
  if (isSuperAdmin(actor) && query.storeId) where.storeId = Number(query.storeId);
  const success = parseSuccess(query.success);
  if (success !== null) where.success = success;

  if (query.loginType) where.loginType = String(query.loginType).trim();

  const startDate = parseDate(query.startDate);
  const endDate = parseDate(query.endDate, true);
  if (startDate || endDate) {
    where.createdAt = {};
    if (startDate) where.createdAt.gte = startDate;
    if (endDate) where.createdAt.lt = endDate;
  }

  const keyword = String(query.keyword || '').trim();
  if (keyword) {
    const [users, admins] = await Promise.all([
      prisma.user.findMany({
      where: {
        OR: [{ phone: { contains: keyword } }, { nickname: { contains: keyword } }]
      },
      select: { id: true }
      }),
      prisma.admin.findMany({
        where: { OR: [{ phone: { contains: keyword } }, { nickname: { contains: keyword } }] },
        select: { id: true }
      })
    ]);
    const userIds = users.map((user) => user.id);
    const adminIds = admins.map((admin) => admin.id);
    where.OR = [
      { phone: { contains: keyword } },
      { ip: { contains: keyword } },
      { userAgent: { contains: keyword } },
      { message: { contains: keyword } },
      ...(userIds.length ? [{ userId: { in: userIds }, accountType: 'user' }] : []),
      ...(adminIds.length ? [{ userId: { in: adminIds }, accountType: 'admin' }] : [])
    ];
  }

  return where;
}

export async function listLoginLogs(prisma, ipLookup, actor, query) {
  const page = toPositiveInt(query.page, 1);
  const pageSize = Math.min(toPositiveInt(query.pageSize, 20), 100);
  const where = await buildWhere(prisma, actor, query);

  const [list, total] = await Promise.all([
    prisma.loginLog.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip: (page - 1) * pageSize,
      take: pageSize
    }),
    prisma.loginLog.count({ where })
  ]);

  const userIds = [...new Set(list.filter((item) => item.accountType === 'user').map((item) => item.userId).filter(Boolean))];
  const adminIds = [...new Set(list.filter((item) => item.accountType === 'admin').map((item) => item.userId).filter(Boolean))];
  const users = userIds.length
    ? await prisma.user.findMany({
      where: { id: { in: userIds } },
      select: { id: true, phone: true, nickname: true }
    })
    : [];
  const admins = adminIds.length
    ? await prisma.admin.findMany({
      where: { id: { in: adminIds } },
      select: { id: true, phone: true, nickname: true }
    })
    : [];
  const userMap = new Map(users.map((user) => [user.id, user]));
  const adminMap = new Map(admins.map((admin) => [admin.id, admin]));

  return {
    list: list.map((item) => ({
      ...item,
      user: item.userId ? (item.accountType === 'admin' ? adminMap : userMap).get(item.userId) || null : null,
      location: ipLookup.lookup(item.ip)
    })),
    pagination: { page, pageSize, total, pages: Math.ceil(total / pageSize) }
  };
}
