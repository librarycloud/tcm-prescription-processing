import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyAdmin,
  verifyManager,
  verifyStoreStaffRoute,
  verifySuperAdmin,
  verifyToken
} from '../src/middlewares/auth.js';

function requestWithToken(tokenUser) {
  return {
    user: { ...tokenUser },
    jwtVerify: async () => {},
    ip: '127.0.0.1',
    headers: { 'user-agent': 'test-agent' }
  };
}

test('verifyToken normalizes authorization claims without querying the database', async () => {
  const request = requestWithToken({ id: 7, role: 2, storeId: 9, phone: '13900000000' });

  await verifyToken(request);

  assert.equal(request.user.role, 2);
  assert.equal(request.user.storeId, 9);
  assert.equal(request.user.phone, '13900000000');
  assert.equal(request.user.ip, '127.0.0.1');
  assert.equal(request.user.userAgent, 'test-agent');
  await assert.rejects(() => verifySuperAdmin(request), { statusCode: 403 });
  await assert.doesNotReject(() => verifyManager(request));
});

test('verifyToken rejects an unsupported role claim', async () => {
  const request = requestWithToken({ id: 8, role: 9, phone: '13700000000' });
  await assert.rejects(() => verifyToken(request), { statusCode: 401 });
});

test('verifyToken rejects a store admin without a store claim', async () => {
  const request = requestWithToken({ id: 9, phone: '13600000000', role: 2, storeId: null });
  await assert.rejects(() => verifyToken(request), { statusCode: 403 });
});

test('store staff is an admin account but not a manager', async () => {
  const request = requestWithToken({ id: 10, phone: '13500000000', role: 3, storeId: 4 });

  await verifyToken(request);
  await assert.doesNotReject(() => verifyAdmin(request));
  await assert.rejects(() => verifyManager(request), { statusCode: 403 });
});

test('store staff can only use routes explicitly marked for staff', async () => {
  const request = requestWithToken({ id: 11, phone: '13400000000', role: 3, storeId: 4 });
  await verifyToken(request);

  request.routeOptions = { config: { storeStaff: true } };
  await assert.doesNotReject(() => verifyStoreStaffRoute(request));

  request.routeOptions = { config: {} };
  await assert.rejects(() => verifyStoreStaffRoute(request), { statusCode: 403 });
});

test('verifyToken rejects store staff without a store claim', async () => {
  const request = requestWithToken({ id: 12, phone: '13300000000', role: 3, storeId: null });
  await assert.rejects(() => verifyToken(request), { statusCode: 403 });
});
