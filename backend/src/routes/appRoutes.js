import { ok } from '../utils/response.js';
import { config } from '../config.js';

/**
 * 兼容老客户端的轻量透明代理路由：
 * 当老版本手机 App 请求 /app/version/android 或下载文件时，
 * 自动代理到独立的 app-release-hub，无需本地处理或存储文件。
 */
export default async function appRoutes(fastify) {
  // 1. 检查更新接口代理
  fastify.get('/version/android', async (request, reply) => {
    reply.header('Cache-Control', 'no-store, no-cache, must-revalidate').header('Pragma', 'no-cache');
    const hubUrl = config.appReleaseHubUrl;
    const appId = config.appReleaseHubAppId;

    if (!hubUrl || !appId) {
      // 未配置 Hub 地址或 App ID 时返回无更新，优雅兜底，避免老手机弹出 404 错误
      return ok(reply, {
        hasUpdate: false,
        versionCode: 0,
        versionName: '',
        message: '未配置 APP_RELEASE_HUB_URL 或 UPDATE_APP_ID'
      });
    }

    try {
      const parsedUrl = new URL(request.url, 'http://localhost');
      const hubTarget = new URL(`${hubUrl}/api/apps/${appId}/version/android`);
      // 传递所有已有 query 参数（如 versionCode, deviceId, channel 等）
      parsedUrl.searchParams.forEach((val, key) => {
        hubTarget.searchParams.set(key, val);
      });
      const deviceIdHeader = request.headers['x-device-id'];
      if (deviceIdHeader && !hubTarget.searchParams.has('deviceId')) {
        hubTarget.searchParams.set('deviceId', deviceIdHeader);
      }
      const headers = { Accept: 'application/json' };
      const resolvedDeviceId = deviceIdHeader || hubTarget.searchParams.get('deviceId');
      if (resolvedDeviceId) {
        headers['x-device-id'] = resolvedDeviceId;
      }
      const res = await fetch(hubTarget.toString(), {
        headers,
        signal: AbortSignal.timeout(10000),
      });

      if (!res.ok) {
        request.log.warn(`[AppReleaseProxy] Hub 返回异常状态: HTTP ${res.status}`);
        return ok(reply, { hasUpdate: false });
      }

      const json = await res.json();
      const data = json?.data;
      if (data) {
        // 确保相对路径转为 Hub 的绝对公网下载地址
        const toAbs = (u) => (u && !/^https?:\/\//i.test(u) ? `${hubUrl}/${String(u).replace(/^\/+/, '')}` : u);
        if (data.apkUrl) data.apkUrl = toAbs(data.apkUrl);
        if (data.fallbackApkUrl) data.fallbackApkUrl = toAbs(data.fallbackApkUrl);
        if (data.downloadUrl) data.downloadUrl = toAbs(data.downloadUrl);
        if (data.fallbackUrl) data.fallbackUrl = toAbs(data.fallbackUrl);
        if (data.patchUrl) data.patchUrl = toAbs(data.patchUrl);
        return ok(reply, data);
      }
      return ok(reply, { hasUpdate: false });
    } catch (err) {
      request.log.warn(`[AppReleaseProxy] 转发到 Hub 失败: ${err.message}`);
      return ok(reply, { hasUpdate: false });
    }
  });

  // 2. 安装包下载重定向
  fastify.get('/releases/:filename', async (request, reply) => {
    const hubUrl = config.appReleaseHubUrl;
    const appId = config.appReleaseHubAppId;
    const filename = String(request.params.filename || '');
    if (hubUrl && appId) {
      return reply.redirect(302, `${hubUrl}/api/apps/${appId}/releases/${filename}`);
    }
    return reply.code(404).send({ code: 404, message: '文件不存在' });
  });

  // 3. 补丁下载重定向
  fastify.get('/patches/:filename', async (request, reply) => {
    const hubUrl = config.appReleaseHubUrl;
    const appId = config.appReleaseHubAppId;
    const filename = String(request.params.filename || '');
    if (hubUrl && appId) {
      return reply.redirect(302, `${hubUrl}/api/apps/${appId}/patches/${filename}`);
    }
    return reply.code(404).send({ code: 404, message: '补丁不存在' });
  });
}
