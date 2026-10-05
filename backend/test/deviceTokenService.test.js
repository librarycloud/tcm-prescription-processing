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
  const adminDeviceToken = {
    async upsert(args) { calls.upsert = args; },
    async findMany(args) {
      if (args.where.deviceId) {
        return [{ id: 999 }];
      }
      return rows;
    },
    async deleteMany(args) { calls.deletes.push(args); },
  };
  const prisma = { adminDeviceToken };

  await registerDeviceToken(prisma, 7, 'android', 'new-fcm-token', 'device-0');

  assert.equal(calls.upsert.create.deviceId, 'device-0');
  
  assert.deepEqual(calls.deletes[0].where.id.in, [999]);
  assert.deepEqual(calls.deletes[1].where.id.in, [21, 22]);
});

test('legacy callers may register tokens without a device ID', async () => {
  let created;
  const adminDeviceToken = {
    async upsert(args) { created = args.create; },
    async findMany() { return []; },
    async deleteMany() {},
  };
  const prisma = { adminDeviceToken };

  await registerDeviceToken(prisma, 7, 'ios', 'apns-token');

  assert.equal(created.deviceId, null);
});

test('updates notification category preferences with the provider token', async () => {
  let upsert;
  const adminDeviceToken = {
    async upsert(args) { upsert = args; },
    async findMany() { return []; },
    async deleteMany() {},
  };
  const prisma = { adminDeviceToken };

  await registerDeviceToken(prisma, 7, 'android', 'fcm-token', 'device-1', {
    prescriptionNotify: false,
    transferNotify: true,
  });

  assert.equal(upsert.update.prescriptionNotify, false);
  assert.equal(upsert.update.transferNotify, true);
});
