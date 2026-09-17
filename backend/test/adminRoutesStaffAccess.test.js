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
  app.decorate('authSessions', { has: async () => true });
  await app.register(adminRoutes, { prefix: '/admin' });
  return app;
}

test('admin route registration only admits staff to explicitly marked routes', async () => {
  const app = await appWithStoreStaff();
  const token = app.jwt.sign({
    id: 13,
    role: 3,
    storeId: 5,
    phone: '13200000000',
    jti: 'staff-session'
  });

  const denied = await app.inject({
    method: 'POST', url: '/admin/product-differences/register', headers: { authorization: `Bearer ${token}` }
  });
  assert.equal(denied.statusCode, 403);
  assert.match(denied.json().message, /门店员工无权/);

  const admitted = await app.inject({
    method: 'GET', url: '/admin/packages', headers: { authorization: `Bearer ${token}` }
  });
  assert.notEqual(admitted.statusCode, 403);

  for (const url of [
    '/admin/prescriptions',
    '/admin/e6-pharmacy/products',
    '/admin/e6/imports',
    '/admin/product-differences/stats',
    '/admin/store-transfers',
    '/admin/yd-goods-check',
    '/admin/yd-goods-check/1',
    '/admin/yd-goods-check/1/items',
    '/admin/yd-goods-check/1/candidates',
  ]) {
    const response = await app.inject({
      method: 'GET', url, headers: { authorization: `Bearer ${token}` },
    });
    assert.notEqual(response.statusCode, 403, `${url} should permit staff read access`);
  }

  const initialCount = await app.inject({
    method: 'POST',
    url: '/admin/yd-goods-check/1/items',
    headers: { authorization: `Bearer ${token}` },
  });
  assert.notEqual(initialCount.statusCode, 403);

  for (const [method, url] of [
    ['POST', '/admin/herb-locations/assignments'],
    ['PUT', '/admin/herb-locations/assignments/1'],
  ]) {
    const response = await app.inject({
      method, url, headers: { authorization: `Bearer ${token}` },
    });
    assert.notEqual(response.statusCode, 403, `${method} ${url} should permit staff herb location access`);
  }

  for (const [method, url] of [
    ['POST', '/admin/prescriptions'],
    ['POST', '/admin/yd-goods-check'],
    ['POST', '/admin/yd-goods-check/1/finish'],
    ['POST', '/admin/yd-goods-check/items/1/review'],
    ['PUT', '/admin/herb-locations/herbs/1'],
    ['DELETE', '/admin/herb-locations/assignments/1'],
  ]) {
    const response = await app.inject({
      method, url, headers: { authorization: `Bearer ${token}` },
    });
    assert.equal(response.statusCode, 403, `${method} ${url} should reject staff write access`);
  }

  const locationUpdate = await app.inject({
    method: 'PUT',
    url: '/admin/yd-goods-check/items/1/location',
    headers: { authorization: `Bearer ${token}` },
  });
  assert.notEqual(locationUpdate.statusCode, 403, 'staff should be able to update an unreviewed count location');

  await app.close();
});
