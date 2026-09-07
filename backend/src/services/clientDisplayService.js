import { mkdir, readFile, writeFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { config } from '../config.js';
import { AppError } from '../utils/appError.js';

const CLIENT_DISPLAY_DIR = 'client-display';
const CONFIG_FILE_NAME = 'config.json';
const QRCODE_FILE_NAME = 'wechat-qrcode.png';

const DEFAULT_CONFIG = {
  wechat: {
    appName: '药房助手',
    appId: '',
    hasQrcode: false
  },
  android: {
    releaseHubUrl: '',
    releaseHubAppId: '',
    customDownloadUrl: '',
    displayName: '药房助手 Android 版'
  },
  announcement: ''
};

function getStorageDir() {
  return path.join(config.uploadDir, CLIENT_DISPLAY_DIR);
}

function getConfigPath() {
  return path.join(getStorageDir(), CONFIG_FILE_NAME);
}

function getQrcodePath() {
  return path.join(getStorageDir(), QRCODE_FILE_NAME);
}

async function getRawConfig() {
  try {
    const raw = await readFile(getConfigPath(), 'utf-8');
    return JSON.parse(raw) || {};
  } catch {
    return {};
  }
}

/**
 * 读取客户端展示配置
 */
export async function getClientDisplayConfig() {
  const raw = await getRawConfig();
  const hasQrcode = await checkQrcodeExists();
  const hasSecret = Boolean(raw.wechat?.appSecret || config.wxSecret);
  const effectiveAppId = (raw.wechat?.appId || config.wxAppId || '').trim();

  return {
    wechat: {
      ...DEFAULT_CONFIG.wechat,
      ...(raw.wechat || {}),
      appSecret: undefined, // 不明文返回密钥
      hasSecret,
      effectiveAppId,
      hasQrcode
    },
    android: {
      ...DEFAULT_CONFIG.android,
      ...(raw.android || {})
    },
    announcement: raw.announcement || ''
  };
}

function detectMimeType(buffer) {
  if (buffer.length >= 4 && buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4E && buffer[3] === 0x47) {
    return 'image/png';
  }
  if (buffer.length >= 3 && buffer[0] === 0xFF && buffer[1] === 0xD8 && buffer[2] === 0xFF) {
    return 'image/jpeg';
  }
  if (buffer.length >= 4 && buffer[0] === 0x47 && buffer[1] === 0x49 && buffer[2] === 0x46 && buffer[3] === 0x38) {
    return 'image/gif';
  }
  if (buffer.length >= 12 && buffer.toString('utf8', 0, 4) === 'RIFF' && buffer.toString('utf8', 8, 12) === 'WEBP') {
    return 'image/webp';
  }
  return 'image/png';
}

/**
 * 检查二维码图片是否存在且合法
 */
async function checkQrcodeExists() {
  try {
    const s = await stat(getQrcodePath());
    return s.isFile() && s.size >= 64;
  } catch {
    return false;
  }
}

/**
 * 获取微信小程序码图片的 Base64 Data URL
 */
export async function getWechatQrcodeBase64() {
  const filePath = getQrcodePath();
  try {
    const s = await stat(filePath);
    if (!s.isFile() || s.size < 64) return null;
    const buf = await readFile(filePath);
    const mime = detectMimeType(buf);
    return `data:${mime};base64,${buf.toString('base64')}`;
  } catch {
    return null;
  }
}

/**
 * 保存客户端展示配置
 */
export async function saveClientDisplayConfig(patch) {
  const raw = await getRawConfig();
  const updated = {
    wechat: {
      appName: String(patch.wechat?.appName ?? raw.wechat?.appName ?? DEFAULT_CONFIG.wechat.appName).trim(),
      appId: String(patch.wechat?.appId ?? raw.wechat?.appId ?? '').trim(),
      appSecret: patch.wechat?.appSecret
        ? String(patch.wechat.appSecret).trim()
        : (raw.wechat?.appSecret || '')
    },
    android: {
      releaseHubUrl: String(patch.android?.releaseHubUrl ?? raw.android?.releaseHubUrl ?? '').trim().replace(/\/+$/, ''),
      releaseHubAppId: String(patch.android?.releaseHubAppId ?? raw.android?.releaseHubAppId ?? '').trim(),
      customDownloadUrl: String(patch.android?.customDownloadUrl ?? raw.android?.customDownloadUrl ?? '').trim(),
      displayName: String(patch.android?.displayName ?? raw.android?.displayName ?? '').trim() || '药房助手 Android 版'
    },
    announcement: String(patch.announcement ?? raw.announcement ?? '').trim()
  };

  const dir = getStorageDir();
  await mkdir(dir, { recursive: true });
  await writeFile(getConfigPath(), JSON.stringify(updated, null, 2), 'utf-8');

  return getClientDisplayConfig();
}

/**
 * 调用微信官方 API 生成小程序码 (getwxacodeunlimit)
 */
export async function generateWechatOfficialQrcode(options = {}) {
  const raw = await getRawConfig();
  const appId = (options.appId || raw.wechat?.appId || config.wxAppId || '').trim();
  const appSecret = (options.appSecret || raw.wechat?.appSecret || config.wxSecret || '').trim();

  if (!appId || !appSecret) {
    throw new AppError('未配置微信小程序 AppID 或 AppSecret，请先在下方输入或在系统环境变量中配置 WX_APPID / WX_SECRET', 400);
  }

  // 1. 获取微信 access_token
  const tokenUrl = new URL('https://api.weixin.qq.com/cgi-bin/token');
  tokenUrl.searchParams.set('grant_type', 'client_credential');
  tokenUrl.searchParams.set('appid', appId);
  tokenUrl.searchParams.set('secret', appSecret);

  let tokenRes;
  try {
    tokenRes = await fetch(tokenUrl, { signal: AbortSignal.timeout(10000) });
  } catch (err) {
    throw new AppError(`连接微信服务器超时: ${err.message}`, 500);
  }

  const tokenData = await tokenRes.json();
  if (!tokenData.access_token) {
    throw new AppError(`获取微信 access_token 失败: ${tokenData.errmsg || '未知错误'} (代码: ${tokenData.errcode})`, 400);
  }

  // 2. 调用 getwxacodeunlimit 生成小程序码
  const qrcodeApiUrl = `https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=${encodeURIComponent(tokenData.access_token)}`;
  const payload = {
    scene: String(options.scene || '1').trim().slice(0, 32),
    check_path: false,
    env_version: options.envVersion || 'release',
    width: Number(options.width) || 430
  };

  if (options.page && String(options.page).trim()) {
    payload.page = String(options.page).trim();
  }

  let codeRes;
  try {
    codeRes = await fetch(qrcodeApiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(15000)
    });
  } catch (err) {
    throw new AppError(`请求微信生成小程序码超时: ${err.message}`, 500);
  }

  const arrayBuffer = await codeRes.arrayBuffer();
  const buffer = Buffer.from(arrayBuffer);

  // 检查是否返回错误 JSON
  const previewText = buffer.toString('utf8', 0, 120);
  if (previewText.startsWith('{') && (previewText.includes('"errcode"') || previewText.includes('"errmsg"'))) {
    try {
      const errJson = JSON.parse(buffer.toString('utf8'));
      throw new AppError(`微信官方生成失败: ${errJson.errmsg} (错误码: ${errJson.errcode})`, 400);
    } catch (e) {
      if (e instanceof AppError) throw e;
    }
  }

  if (buffer.length < 64) {
    throw new AppError('微信官方返回的图片数据无效', 500);
  }

  // 3. 保存并返回 Base64
  const result = await saveWechatQrcode(buffer, 'image/jpeg');
  return result;
}

/**
 * 保存微信小程序码图片
 */
export async function saveWechatQrcode(buffer, mimeType) {
  const allowedMimes = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
  if (!allowedMimes.includes(mimeType)) {
    throw new AppError('仅支持 PNG / JPG / WebP / GIF 格式的图片', 400);
  }

  const dir = getStorageDir();
  await mkdir(dir, { recursive: true });
  await writeFile(getQrcodePath(), buffer);
  const actualMime = detectMimeType(buffer) || mimeType;
  return {
    qrcodeUrl: `data:${actualMime};base64,${buffer.toString('base64')}`
  };
}

/**
 * 获取微信小程序码图片路径
 */
export async function getWechatQrcodeFilePath() {
  const filePath = getQrcodePath();
  try {
    const s = await stat(filePath);
    if (!s.isFile() || s.size < 64) return null;
    return filePath;
  } catch {
    return null;
  }
}

/**
 * 向 App Release Hub 拉取最新 Android 版本信息
 */
export async function fetchReleaseHubVersion(hubUrl, appId) {
  const resolvedHubUrl = (hubUrl || config.appReleaseHubUrl || '').replace(/\/+$/, '');
  const resolvedAppId = (appId || config.appReleaseHubAppId || '').trim();

  if (!resolvedHubUrl || !resolvedAppId) {
    return {
      connected: false,
      message: '未配置 App Release Hub 地址或 App ID'
    };
  }

  try {
    const targetUrl = `${resolvedHubUrl}/api/apps/${resolvedAppId}/version/android`;
    const res = await fetch(targetUrl, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(8000)
    });

    if (!res.ok) {
      return {
        connected: false,
        message: `Hub 响应状态异常 (HTTP ${res.status})`
      };
    }

    const json = await res.json();
    const data = json?.data;
    if (!data) {
      return {
        connected: false,
        message: 'Hub 返回数据为空'
      };
    }

    const toAbs = (u) => (u && !/^https?:\/\//i.test(u) ? `${resolvedHubUrl}/${String(u).replace(/^\/+/, '')}` : u);
    const downloadUrl = toAbs(data.downloadUrl || data.apkUrl || data.fallbackApkUrl || data.fallbackUrl || '');

    return {
      connected: true,
      versionCode: data.versionCode || 0,
      versionName: data.versionName || '',
      releaseNotes: Array.isArray(data.releaseNotes) ? data.releaseNotes : [],
      downloadUrl,
      size: data.size || data.fallbackApkSize || 0,
      sha256: data.sha256 || '',
      publishedAt: data.publishedAt || '',
      changelogUrl: data.changelogUrl || ''
    };
  } catch (err) {
    return {
      connected: false,
      message: `无法连接到 Hub: ${err.message}`
    };
  }
}

/**
 * 获取面向客户端完整展示的信息
 */
export async function getClientDisplayInfo() {
  const conf = await getClientDisplayConfig();

  const hubVersion = await fetchReleaseHubVersion(
    conf.android.releaseHubUrl,
    conf.android.releaseHubAppId
  );

  const downloadUrl = conf.android.customDownloadUrl || hubVersion.downloadUrl || '';

  const qrcodeBase64 = await getWechatQrcodeBase64();

  return {
    wechat: {
      appName: conf.wechat.appName,
      appId: conf.wechat.appId,
      qrcodeUrl: qrcodeBase64
    },
    android: {
      displayName: conf.android.displayName,
      downloadUrl,
      versionCode: hubVersion.versionCode || null,
      versionName: hubVersion.versionName || null,
      releaseNotes: hubVersion.releaseNotes || [],
      size: hubVersion.size || 0,
      sha256: hubVersion.sha256 || '',
      publishedAt: hubVersion.publishedAt || '',
      changelogUrl: hubVersion.changelogUrl || '',
      hubConnected: hubVersion.connected,
      hubMessage: hubVersion.message || ''
    },
    announcement: conf.announcement
  };
}
