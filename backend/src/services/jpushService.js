/**
 * JPush (极光推送) Push Notification Service
 *
 * Uses JPush REST API v3 to send push notifications.
 * JPush works in mainland China where FCM/APNs are often unreachable.
 *
 * Required environment variables:
 *   JPUSH_APP_KEY      — AppKey from JPush console
 *   JPUSH_MASTER_SECRET — MasterSecret from JPush console
 *
 * docs: https://docs.jiguang.cn/jpush/server/push/rest_api_v3_push
 */

const JPUSH_PUSH_URL = 'https://api.jpush.cn/v3/push';

function getJPushAuth() {
  const appKey = process.env.JPUSH_APP_KEY;
  const secret = process.env.JPUSH_MASTER_SECRET;
  if (!appKey || !secret) return null;
  return Buffer.from(`${appKey}:${secret}`).toString('base64');
}

/**
 * Sends a JPush notification to one or more Registration IDs.
 *
 * @param {string[]} registrationIds  — JPush Registration IDs from app SDK
 * @param {{ title: string, body: string, data?: Record<string,string> }} payload
 */
export async function sendJPushNotification(registrationIds, { title, body, data = {} }) {
  if (!registrationIds?.length) return;
  const auth = getJPushAuth();
  if (!auth) return;

  const message = {
    platform: 'android',   // iOS uses native APNs directly, not JPush
    audience: { registration_id: registrationIds },
    notification: {
      android: {
        title,
        alert: body,
        extras: data,
        channel_id: 'transfer_alerts',
        sound: 'default',
        alert_type: 7,
      },
    },
    options: {
      apns_production: process.env.APNS_PRODUCTION === 'true',
    },
  };

  try {
    const response = await fetch(JPUSH_PUSH_URL, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${auth}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(message),
    });
    if (!response.ok) {
      const text = await response.text();
      console.warn('[Push] JPush send failed:', response.status, text.slice(0, 200));
    }
  } catch (err) {
    console.warn('[Push] JPush request error:', err?.message);
  }
}
