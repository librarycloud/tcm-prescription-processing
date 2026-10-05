/**
 * Push Notification Service
 *
 * Supports two platforms:
 *   - Android: Firebase Cloud Messaging (FCM) via HTTP v1 API
 *   - iOS:     Apple Push Notification service (APNs) via JWT + HTTP/2
 *
 * All credentials are read from environment variables — never hardcoded.
 * All send functions swallow errors so push failures never break business logic.
 *
 * Environment variables required:
 *   FCM_SERVICE_ACCOUNT_KEY  — JSON string of the Firebase service account private key
 *   APNS_KEY_P8              — Contents of the .p8 key file (newlines as \n)
 *   APNS_KEY_ID              — 10-char key ID from Apple Developer portal
 *   APNS_TEAM_ID             — 10-char team ID from Apple Developer account
 *   APNS_BUNDLE_ID           — iOS app bundle identifier (e.g. com.yourcompany.tcmadmin)
 *   APNS_PRODUCTION          — Set to "true" for production APNs; omit for sandbox
 */

import https from 'https';
import http2 from 'http2';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { sendJPushNotification } from './jpushService.js';


// ─── FCM ─────────────────────────────────────────────────────────────────────



let _fcmAccessToken = null;
let _fcmAccessTokenExpiry = 0;
let _fcmSaCached = undefined;

function parseFcmServiceAccount() {
  if (_fcmSaCached !== undefined) return _fcmSaCached;

  const raw = process.env.FCM_SERVICE_ACCOUNT_KEY;
  if (raw) {
    const trimmed = raw.trim();
    // 1. Direct JSON string
    if (trimmed.startsWith('{')) {
      try {
        _fcmSaCached = JSON.parse(trimmed);
        console.log('[Push] FCM service account loaded from env JSON string (project_id:', _fcmSaCached.project_id, ')');
        return _fcmSaCached;
      } catch {
        console.error('[Push] FCM_SERVICE_ACCOUNT_KEY is not valid JSON string');
      }
    }
    // 2. File path provided in env
    try {
      const resolvedPath = path.isAbsolute(trimmed) ? trimmed : path.resolve(process.cwd(), trimmed);
      if (fs.existsSync(resolvedPath)) {
        _fcmSaCached = JSON.parse(fs.readFileSync(resolvedPath, 'utf8'));
        console.log('[Push] FCM service account loaded from env path (project_id:', _fcmSaCached.project_id, ')');
        return _fcmSaCached;
      }
    } catch (e) {
      console.error('[Push] Failed to read FCM key from configured env path:', e.message);
    }
  }

  // 3. Fallback: Auto-detect any *firebase-adminsdk*.json or firebase-key.json in cwd or backend dir
  const candidateDirs = [process.cwd(), path.resolve(process.cwd(), 'backend')];
  
  for (const dir of candidateDirs) {
    try {
      if (!fs.existsSync(dir)) continue;
      const files = fs.readdirSync(dir);
      const keyFile = files.find(f => (f.includes('firebase-adminsdk') || f.includes('firebase-service-account')) && f.endsWith('.json'));
      if (keyFile) {
        const fullPath = path.join(dir, keyFile);
        _fcmSaCached = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
        console.log('[Push] FCM service account auto-detected at:', fullPath, '(project_id:', _fcmSaCached.project_id, ')');
        return _fcmSaCached;
      }
    } catch {}
  }

  console.warn('[Push] WARNING: No FCM service account key found! Checked paths:', candidateDirs);
  _fcmSaCached = null;
  return null;
}

/**
 * Obtains a short-lived OAuth2 access token for FCM HTTP v1 API.
 * Tokens are cached until 5 min before expiry.
 */
async function getFcmAccessToken() {
  if (_fcmAccessToken && Date.now() < _fcmAccessTokenExpiry) {
    return _fcmAccessToken;
  }
  const sa = parseFcmServiceAccount();
  if (!sa) return null;

  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    iss: sa.client_email,
    sub: sa.client_email,
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
  })).toString('base64url');

  const sign = crypto.createSign('RSA-SHA256');
  sign.update(`${header}.${payload}`);
  const sig = sign.sign(sa.private_key, 'base64url');
  const jwt = `${header}.${payload}.${sig}`;

  const body = new URLSearchParams({
    grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    assertion: jwt,
  }).toString();

  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!response.ok) {
    console.error('[Push] FCM token exchange failed:', await response.text());
    return null;
  }
  const json = await response.json();
  _fcmAccessToken = json.access_token;
  // Expire 5 minutes early
  _fcmAccessTokenExpiry = Date.now() + (json.expires_in - 300) * 1000;
  return _fcmAccessToken;
}

/**
 * Sends a single FCM notification to an Android device token.
 */
async function sendFcmNotification(deviceToken, { title, body, data = {} }) {
  const sa = parseFcmServiceAccount();
  if (!sa?.project_id) {
    console.warn('[Push] Cannot send FCM: No valid service account (sa is null or missing project_id)');
    return { status: 'rejected', invalidToken: false };
  }

  const accessToken = await getFcmAccessToken();
  if (!accessToken) {
    console.warn('[Push] Cannot send FCM: Failed to obtain OAuth2 access token from Google');
    return { status: 'rejected', invalidToken: false };
  }

  const message = {
    message: {
      token: deviceToken,
      notification: { title, body },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      android: {
        priority: 'high',
        notification: { sound: 'default', channel_id: 'transfer_alerts' },
      },
    },
  };

  try {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(message),
      },
    );
    if (!response.ok) {
      const text = await response.text();
      console.warn('[Push] FCM send failed for token:', deviceToken.slice(0, 20), text);
      let invalidToken = false;
      try {
        const error = JSON.parse(text).error;
        invalidToken = error?.details?.some((detail) => detail.errorCode === 'UNREGISTERED') === true;
      } catch {}
      return { status: response.status >= 500 ? 'unknown' : 'rejected', invalidToken };
    } else {
      console.log('[Push] FCM send SUCCESS for token:', deviceToken.slice(0, 20));
      return { status: 'accepted', invalidToken: false };
    }
  } catch (err) {
    console.error('[Push] FCM fetch network error:', err.message);
    return { status: 'unknown', invalidToken: false };
  }
}

// ─── APNs ─────────────────────────────────────────────────────────────────────

let _apnsJwt = null;
let _apnsJwtIssuedAt = 0;

/**
 * Returns a signed APNs provider JWT (valid 1 hour, refreshed every 55 minutes).
 */
function getApnsJwt() {
  const now = Math.floor(Date.now() / 1000);
  if (_apnsJwt && now - _apnsJwtIssuedAt < 55 * 60) return _apnsJwt;

  const keyId = process.env.APNS_KEY_ID;
  const teamId = process.env.APNS_TEAM_ID;
  if (!keyId || !teamId) {
    console.warn('[Push] APNs Key ID or Team ID missing. keyId:', !!keyId, 'teamId:', !!teamId);
    return null;
  }

  // APNS_KEY_P8 can be:
  //   1. A file path: "./AuthKey_XXXXXXXX.p8"  (relative to process.cwd())
  //   2. Inline key content (starts with "-----BEGIN")
  let p8 = '';
  const raw = (process.env.APNS_KEY_P8 || '').trim();
  if (!raw) {
    console.warn('[Push] APNS_KEY_P8 missing');
    return null;
  }

  if (raw.startsWith('-----')) {
    // Inline content — unescape \n sequences written in .env
    p8 = raw.replace(/\\n/g, '\n');
  } else {
    // Treat as file path
    const filePath = path.isAbsolute(raw) ? raw : path.resolve(process.cwd(), raw);
    try {
      p8 = fs.readFileSync(filePath, 'utf8').trim();
    } catch (err) {
      console.error('[Push] Failed to read APNs .p8 file:', filePath, err.message);
      return null;
    }
  }

  if (!p8) {
    console.warn('[Push] APNs p8 is empty');
    return null;
  }

  const header = Buffer.from(JSON.stringify({ alg: 'ES256', kid: keyId })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ iss: teamId, iat: now })).toString('base64url');

  const sign = crypto.createSign('SHA256');
  sign.update(`${header}.${payload}`);
  try {
    _apnsJwt = `${header}.${payload}.${sign.sign({ key: p8, dsaEncoding: 'ieee-p1363' }, 'base64url')}`;
    _apnsJwtIssuedAt = now;
    console.log('[Push] APNs JWT generated successfully');
    return _apnsJwt;
  } catch (err) {
    console.error('[Push] APNs JWT signing failed:', err.message);
    return null;
  }
}

/**
 * Sends a single APNs notification to an iOS device token.
 * Uses the HTTP/2 APNs provider API via Node's built-in `https` module.
 */
async function sendApnsNotification(deviceToken, { title, body, data = {} }) {
  const jwt = getApnsJwt();
  const bundleId = process.env.APNS_BUNDLE_ID;
  if (!jwt || !bundleId) {
    console.warn('[Push] APNs abort: credentials or bundle ID missing');
    return false;
  }

  const primaryHost = process.env.APNS_PRODUCTION === 'true'
    ? 'api.push.apple.com'
    : 'api.sandbox.push.apple.com';
  const alternateHost = primaryHost === 'api.push.apple.com'
    ? 'api.sandbox.push.apple.com'
    : 'api.push.apple.com';
  const apnsPayload = JSON.stringify({
    aps: { alert: { title, body }, sound: 'default', badge: 1 },
    ...data,
  });

  const sendToHost = (host) => new Promise((resolve) => {
    let settled = false;
    const finish = (result) => {
      if (settled) return;
      settled = true;
      resolve(result);
    };
    let client;
    try {
      client = http2.connect(`https://${host}`);
      client.on('error', (err) => {
        console.warn('[Push] APNs http2 client error:', err.message);
        client.destroy();
        finish({ accepted: false });
      });
      client.setTimeout(15_000, () => {
        console.warn('[Push] APNs request timed out');
        client.destroy();
        finish({ accepted: false });
      });
      const req = client.request({
        [http2.constants.HTTP2_HEADER_METHOD]: 'POST',
        [http2.constants.HTTP2_HEADER_PATH]: `/3/device/${deviceToken}`,
        authorization: `bearer ${jwt}`,
        'apns-topic': bundleId,
        'apns-push-type': 'alert',
        'apns-priority': '10',
        'content-type': 'application/json',
        'content-length': Buffer.byteLength(apnsPayload),
      });
      req.on('response', (headers) => {
        const status = headers[http2.constants.HTTP2_HEADER_STATUS];
        let raw = '';
        req.on('data', (chunk) => { raw += chunk; });
        req.on('end', () => {
          client.close();
          if (status !== 200) {
            console.warn('[Push] APNs send failed:', status, raw.slice(0, 200), 'Token:', deviceToken.slice(0, 10) + '...');
            let reason = null;
            try { reason = JSON.parse(raw).reason; } catch {}
            finish({ accepted: false, reason });
          } else {
            console.log(`[Push] APNs send success to token: ${deviceToken.slice(0, 10)}...`);
            finish({ accepted: true });
          }
        });
      });
      req.on('error', (err) => {
        console.warn('[Push] APNs request error:', err.message);
        client.close();
        finish({ accepted: false });
      });
      req.end(apnsPayload);
    } catch (err) {
      client?.close();
      console.warn('[Push] APNs request setup failed:', err.message);
      finish({ accepted: false });
    }
  });

  const result = await sendToHost(primaryHost);
  if (!result.accepted && result.reason === 'BadDeviceToken') {
    return (await sendToHost(alternateHost)).accepted;
  }
  return result.accepted;
}

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Sends a push notification to a single device token.
 * @param {'android'|'ios'|'jpush'} platform
 * @param {string} token  — FCM token / APNs hex token / JPush registration ID
 * @param {{ title: string, body: string, data?: Record<string,string> }} payload
 */
export async function sendPushToToken(platform, token, payload) {
  try {
    if (platform === 'android') {
      await sendFcmNotification(token, payload);
    } else if (platform === 'ios') {
      await sendApnsNotification(token, payload);
    } else if (platform === 'jpush') {
      await sendJPushNotification([token], payload);
    }
  } catch (err) {
    console.warn('[Push] Unexpected error sending to', platform, err?.message);
  }
}

function notificationCategory(payload) {
  const eventCode = String(payload?.data?.eventCode || '').toUpperCase();
  if (eventCode.startsWith('TRANSFER_') || eventCode.startsWith('STOCKTAKING') || eventCode.startsWith('GOODS_CHECK')) return 'transfer';
  if (eventCode.startsWith('PACKAGE_') || eventCode === 'PROCESSING_COMPLETED' || eventCode.startsWith('E6') || eventCode.startsWith('PRESCRIPTION')) return 'prescription';
  return null;
}

function acceptsNotification(token, category) {
  if (category === 'transfer') return token.transferNotify !== false;
  if (category === 'prescription') return token.prescriptionNotify !== false;
  return true;
}

export async function dispatchAndroidPushes(tokens, payload, sendFcm, sendJPush, removeInvalidFcm) {
  const category = notificationCategory(payload);
  const admins = new Map();
  for (const token of tokens.filter((item) => acceptsNotification(item, category))) {
    const adminKey = `${token.adminId}:${token.deviceId ?? 'legacy'}`;
    const admin = admins.get(adminKey) || { fcm: [], jpush: [] };
    admin[token.platform === 'jpush' ? 'jpush' : 'fcm'].push(token);
    admins.set(adminKey, admin);
  }

  await Promise.all([...admins.values()].map(async ({ fcm, jpush }) => {
    if (!fcm.length) {
      if (jpush.length) await sendJPush(jpush.map((token) => token.token), payload);
      return;
    }

    let accepted = false;
    let resultUnknown = false;
    for (const token of fcm) {
      let result;
      try {
        result = await sendFcm(token.token, payload);
      } catch (error) {
        console.warn('[Push] FCM dispatch error:', error?.message);
        result = { status: 'unknown', invalidToken: false };
      }
      const delivery = typeof result === 'boolean'
        ? { status: result ? 'accepted' : 'rejected', invalidToken: false }
        : result;
      if (delivery?.invalidToken) {
        try {
          await removeInvalidFcm(token.token);
        } catch (error) {
          console.warn('[Push] Failed to remove unregistered FCM token:', error?.message);
        }
      }
      accepted ||= delivery?.status === 'accepted' || delivery?.accepted === true;
      resultUnknown ||= delivery?.status === 'unknown';
    }
    if (!accepted && !resultUnknown && jpush.length) {
      await sendJPush(jpush.map((token) => token.token), payload);
    }
  }));
}

/**
 * Loads all device tokens for the given admin IDs and sends push notifications.
 * JPush tokens are batched into a single API call for efficiency.
 * Errors on individual tokens are swallowed — one bad token won't block others.
 *
 * @param {object} prisma
 * @param {number[]} adminIds
 * @param {{ title: string, body: string, data?: Record<string,string> }} payload
 */
export async function sendPushToAdmins(prisma, adminIds, payload) {
  if (!adminIds?.length) return;
  let tokens;
  try {
    tokens = await prisma.adminDeviceToken.findMany({
      where: { adminId: { in: adminIds } },
      select: {
        adminId: true,
        deviceId: true,
        platform: true,
        token: true,
        prescriptionNotify: true,
        transferNotify: true,
      },
    });
  } catch (err) {
    console.warn('[Push] Failed to load device tokens:', err?.message);
    return;
  }

  console.log(`[Push] sendPushToAdmins adminIds=${JSON.stringify(adminIds)}, tokens found (${tokens?.length || 0}):`, tokens?.map(t => ({ platform: t.platform, deviceId: t.deviceId, token: t.token.slice(0, 15) + '...' })));

  const androidTokens = tokens.filter((token) => token.platform === 'android' || token.platform === 'jpush');
  const category = notificationCategory(payload);
  const iosTokens = tokens
    .filter((token) => token.platform === 'ios')
    .filter((token) => acceptsNotification(token, category));

  await Promise.allSettled([
    dispatchAndroidPushes(
      androidTokens,
      payload,
      sendFcmNotification,
      async (registrationIds, message) => {
        const result = await sendJPushNotification(registrationIds, message);
        return result?.accepted === true;
      },
      (token) => prisma.adminDeviceToken.deleteMany({ where: { platform: 'android', token } }),
    ),
    ...iosTokens.map((token) => sendPushToToken('ios', token.token, payload)),
  ]);
}

/**
 * Loads all device tokens for admins belonging to the given store IDs and sends pushes.
 *
 * @param {object} prisma
 * @param {number[]} storeIds
 * @param {{ title: string, body: string, data?: Record<string,string> }} payload
 * @param {number[]} [excludeAdminIds]  — IDs to skip (e.g. the actor who triggered the event)
 */
export async function sendPushToStores(prisma, storeIds, payload, excludeAdminIds = []) {
  if (!storeIds?.length) return;
  let admins;
  try {
    admins = await prisma.admin.findMany({
      where: {
        storeId: { in: storeIds },
        status: 1,
        id: excludeAdminIds.length ? { notIn: excludeAdminIds } : undefined,
        deviceTokens: { some: {} },
      },
      select: { id: true },
    });
  } catch (err) {
    console.warn('[Push] Failed to load admins for stores:', err?.message);
    return;
  }
  await sendPushToAdmins(prisma, admins.map((a) => a.id), payload);
}
