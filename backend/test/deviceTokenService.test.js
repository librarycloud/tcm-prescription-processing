import test from 'node:test';
import assert from 'node:assert/strict';
import { registerDeviceToken } from '../src/services/deviceTokenService.js';

test('prunes whole device groups instead of separating paired provider tokens', async () => {
  const rows = [];
  for (let device = 0; device < 11; device += 1) {
    rows.push(
      { id: device * 2 + 1, deviceId: `device-${device}`, platform: 'android', token: `fcm-${device}` },
      { id: device * 2 + 2, deviceId: `device-${device}`, platform: 'jpush', token: `jpush-${device}` },
    );
  }
  const calls = { deletes: [] };
  const tx = {
    adminDeviceToken: {
      async upsert(args) { calls.upsert = args; },
      async findMany() { return rows; },
      async deleteMany(args) { calls.deletes.push(args); },
    },
  };
  const prisma = { async $transaction(callback) { return callback(tx); } };

  await registerDeviceToken(prisma, 7, 'android', 'new-fcm-token', 'device-0');

  assert.equal(calls.upsert.create.deviceId, 'device-0');
  assert.deepEqual(calls.deletes[0].where, {
    adminId: 7,
    deviceId: 'device-0',
    platform: 'android',
    token: { not: 'new-fcm-token' },
  });
  assert.deepEqual(calls.deletes[1].where.id.in, [21, 22]);
});

test('legacy callers may register tokens without a device ID', async () => {
  let created;
  const tx = {
    adminDeviceToken: {
      async upsert(args) { created = args.create; },
      async findMany() { return []; },
      async deleteMany() {},
    },
  };

  await registerDeviceToken({ async $transaction(callback) { return callback(tx); } }, 7, 'ios', 'apns-token');

  assert.equal(created.deviceId, null);
});
