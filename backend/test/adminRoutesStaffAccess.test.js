import assert from 'node:assert/strict';
import test from 'node:test';
import Fastify from 'fastify';
import jwt from '@fastify/jwt';
import rateLimit from '@fastify/rate-limit';
import adminRoutes from '../src/routes/adminRoutes.js';

async function appWithStoreStaff() {
  const app = Fastify();
  await app.register(rateLimit, { global: false, max: 100, timeWindow: '1 minute' });
  await app.register(jwt, { secret: 'test-secret' });
  await app.register(adminRoutes, { prefix: '/admin' });
  return app;
}

test('admin route registration only admits staff to explicitly marked routes', async () => {
  const app = await appWithStoreStaff();
  const token = app.jwt.sign({ id: 13, role: 3, storeId: 5, phone: '13200000000' });

  const denied = await app.inject({
    method: 'GET', url: '/admin/store-transfers', headers: { authorization: `Bearer ${token}` }
  });
  assert.equal(denied.statusCode, 403);
  assert.match(denied.json().message, /门店员工无权/);

  const admitted = await app.inject({
    method: 'GET', url: '/admin/packages', headers: { authorization: `Bearer ${token}` }
  });
  assert.notEqual(admitted.statusCode, 403);

  await app.close();
});
