import assert from 'node:assert/strict';
import test from 'node:test';
import { verifyManager, verifySuperAdmin, verifyToken } from '../src/middlewares/auth.js';

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
