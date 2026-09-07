import { createReadStream } from 'node:fs';
import { ok, fail } from '../utils/response.js';
import {
  getClientDisplayConfig,
  saveClientDisplayConfig,
  saveWechatQrcode,
  getWechatQrcodeFilePath,
  getClientDisplayInfo,
  fetchReleaseHubVersion
} from '../services/clientDisplayService.js';
import { AppError } from '../utils/appError.js';

/**
 * 获取管理员配置（含当前 Hub 连接探测）
 */
export async function getClientDisplaySettingsController(_request, reply) {
  const conf = await getClientDisplayConfig();
  const hubProbe = await fetchReleaseHubVersion(
    conf.android.releaseHubUrl,
    conf.android.releaseHubAppId
  );
  return ok(reply, {
    config: conf,
    hubProbe
  });
}

/**
 * 更新客户端展示配置
 */
export async function updateClientDisplaySettingsController(request, reply) {
  const body = request.body || {};
  const updated = await saveClientDisplayConfig(body);
  const hubProbe = await fetchReleaseHubVersion(
    updated.android.releaseHubUrl,
    updated.android.releaseHubAppId
  );
  return ok(reply, {
    config: updated,
    hubProbe
  }, '客户端展示配置已保存');
}

/**
 * 上传微信小程序码图片
 */
export async function uploadWechatQrcodeController(request, reply) {
  const file = await request.file();
  if (!file) {
    return fail(reply, '请选择要上传的小程序码图片', 400);
  }

  const buffer = await file.toBuffer();
  if (!buffer || buffer.length === 0) {
    return fail(reply, '上传的文件为空', 400);
  }

  if (buffer.length > 5 * 1024 * 1024) {
    return fail(reply, '图片大小不能超过 5MB', 400);
  }

  const result = await saveWechatQrcode(buffer, file.mimetype);
  return ok(reply, result, '微信小程序码上传成功');
}

/**
 * 获取客户端展示数据（供右上角弹窗及前端展示）
 */
export async function getClientDisplayInfoController(_request, reply) {
  const info = await getClientDisplayInfo();
  return ok(reply, info);
}

/**
 * 访问微信小程序码图片（公开路由）
 */
export async function serveWechatQrcodeController(_request, reply) {
  const filePath = await getWechatQrcodeFilePath();
  if (!filePath) {
    return fail(reply, '小程序码尚未上传', 404);
  }

  reply
    .header('Content-Type', 'image/png')
    .header('Cache-Control', 'public, max-age=300');

  return reply.send(createReadStream(filePath));
}
