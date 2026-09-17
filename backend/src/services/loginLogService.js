function truncate(value, maxLength) {
  const text = String(value || '').trim();
  return text ? text.slice(0, maxLength) : null;
}

export async function recordLoginLog(request, details) {
  try {
    const ip = request.server.ipLookup.normalize(request.ip) || 'unknown';
    let storeId = details.storeId ? Number(details.storeId) : null;
    if (!storeId && details.phone) {
      const account = await (details.accountType === 'admin'
        ? request.server.prisma.admin
        : request.server.prisma.user).findUnique({
        where: { phone: String(details.phone).trim() },
        select: { storeId: true }
      });
      storeId = account?.storeId || null;
    }
    await request.server.prisma.loginLog.create({
      data: {
        userId: details.userId ? Number(details.userId) : null,
        accountType: details.accountType === 'admin' ? 'admin' : 'user',
        phone: truncate(details.phone, 20),
        loginType: truncate(details.loginType, 20) || 'unknown',
        success: details.success ? 1 : 0,
        ip,
        userAgent: truncate(request.headers['user-agent'], 500),
        message: truncate(details.message, 255),
        storeId
      }
    });
  } catch (error) {
    request.log.error({ error }, 'Failed to write login log');
  }
}
