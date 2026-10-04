import test from 'node:test';
import assert from 'node:assert/strict';
import { dispatchAndroidPushes } from '../src/services/pushNotificationService.js';

test('uses FCM first and skips JPush when FCM accepts the message', async () => {
  const calls = [];
  await dispatchAndroidPushes(
    [
      { adminId: 1, platform: 'android', token: 'fcm-token' },
      { adminId: 1, platform: 'jpush', token: 'jpush-token' },
    ],
    { title: 'title', body: 'body' },
    async (token) => { calls.push(`fcm:${token}`); return { status: 'accepted' }; },
    async (tokens) => calls.push(`jpush:${tokens.join(',')}`),
    async () => {},
  );

  assert.deepEqual(calls, ['fcm:fcm-token']);
});

test('falls back to JPush when FCM rejects the message', async () => {
  const calls = [];
  await dispatchAndroidPushes(
    [
      { adminId: 1, platform: 'android', token: 'fcm-token' },
      { adminId: 1, platform: 'jpush', token: 'jpush-token' },
    ],
    { title: 'title', body: 'body' },
    async (token) => { calls.push(`fcm:${token}`); return { status: 'rejected' }; },
    async (tokens) => calls.push(`jpush:${tokens.join(',')}`),
    async () => {},
  );

  assert.deepEqual(calls, ['fcm:fcm-token', 'jpush:jpush-token']);
});

test('dispatches each admin independently and cleans unregistered FCM tokens', async () => {
  const calls = [];
  await dispatchAndroidPushes(
    [
      { adminId: 1, platform: 'android', token: 'expired-fcm' },
      { adminId: 1, platform: 'jpush', token: 'admin-one-jpush' },
      { adminId: 2, platform: 'jpush', token: 'admin-two-jpush' },
    ],
    { title: 'title', body: 'body' },
    async () => ({ status: 'rejected', invalidToken: true }),
    async (tokens) => calls.push(tokens),
    async (token) => calls.push(`remove:${token}`),
  );

  assert.deepEqual(calls.sort((left, right) => String(left).localeCompare(String(right))), [
    ['admin-one-jpush'],
    'remove:expired-fcm',
    ['admin-two-jpush'],
  ].sort((left, right) => String(left).localeCompare(String(right))));
});

test('does not fall back when the FCM outcome is unknown', async () => {
  const calls = [];
  await dispatchAndroidPushes(
    [
      { adminId: 1, platform: 'android', token: 'fcm-token' },
      { adminId: 1, platform: 'jpush', token: 'jpush-token' },
    ],
    { title: 'title', body: 'body' },
    async (token) => { calls.push(`fcm:${token}`); return { status: 'unknown' }; },
    async (tokens) => calls.push(`jpush:${tokens.join(',')}`),
    async () => {},
  );

  assert.deepEqual(calls, ['fcm:fcm-token']);
});

test('falls back only on the device whose FCM request was rejected', async () => {
  const calls = [];
  await dispatchAndroidPushes(
    [
      { adminId: 1, deviceId: 'device-a', platform: 'android', token: 'fcm-a' },
      { adminId: 1, deviceId: 'device-a', platform: 'jpush', token: 'jpush-a' },
      { adminId: 1, deviceId: 'device-b', platform: 'android', token: 'fcm-b' },
      { adminId: 1, deviceId: 'device-b', platform: 'jpush', token: 'jpush-b' },
    ],
    { title: 'title', body: 'body' },
    async (token) => {
      calls.push(`fcm:${token}`);
      return { status: token === 'fcm-a' ? 'accepted' : 'rejected' };
    },
    async (tokens) => calls.push(`jpush:${tokens.join(',')}`),
    async () => {},
  );

  assert.deepEqual(calls.sort(), ['fcm:fcm-a', 'fcm:fcm-b', 'jpush:jpush-b']);
});
