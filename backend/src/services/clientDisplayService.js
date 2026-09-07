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

/**
 * 读取客户端展示配置
 */
export async function getClientDisplayConfig() {
  try {
    const raw = await readFile(getConfigPath(), 'utf-8');
    const parsed = JSON.parse(raw);
    const hasQrcode = await checkQrcodeExists();
    return {
      wechat: {
        ...DEFAULT_CONFIG.wechat,
        ...(parsed.wechat || {}),
        hasQrcode
      },
      android: {
        ...DEFAULT_CONFIG.android,
        ...(parsed.android || {})
      },
      announcement: parsed.announcement || ''
    };
  } catch {
    const hasQrcode = await checkQrcodeExists();
    return {
      ...DEFAULT_CONFIG,
      wechat: { ...DEFAULT_CONFIG.wechat, hasQrcode }
    };
  }
}

/**
 * 检查二维码图片是否存在
 */
async function checkQrcodeExists() {
  try {
    const s = await stat(getQrcodePath());
    return s.isFile();
  } catch {
    return false;
  }
}

/**
 * 保存客户端展示配置
 */
export async function saveClientDisplayConfig(patch) {
  const current = await getClientDisplayConfig();
  const updated = {
    wechat: {
      appName: String(patch.wechat?.appName ?? current.wechat.appName).trim(),
      appId: String(patch.wechat?.appId ?? current.wechat.appId).trim()
    },
    android: {
      releaseHubUrl: String(patch.android?.releaseHubUrl ?? current.android.releaseHubUrl).trim().replace(/\/+$/, ''),
      releaseHubAppId: String(patch.android?.releaseHubAppId ?? current.android.releaseHubAppId).trim(),
      customDownloadUrl: String(patch.android?.customDownloadUrl ?? current.android.customDownloadUrl).trim(),
      displayName: String(patch.android?.displayName ?? current.android.displayName).trim() || '药房助手 Android 版'
    },
    announcement: String(patch.announcement ?? current.announcement).trim()
  };

  const dir = getStorageDir();
  await mkdir(dir, { recursive: true });
  await writeFile(getConfigPath(), JSON.stringify(updated, null, 2), 'utf-8');

  return getClientDisplayConfig();
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
  return { qrcodeUrl: '/client-display/qrcode' };
}

/**
 * 获取微信小程序码图片路径
 */
export async function getWechatQrcodeFilePath() {
  const filePath = getQrcodePath();
  try {
    const s = await stat(filePath);
    if (!s.isFile()) return null;
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

  return {
    wechat: {
      appName: conf.wechat.appName,
      appId: conf.wechat.appId,
      qrcodeUrl: conf.wechat.hasQrcode ? '/client-display/qrcode' : null
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
