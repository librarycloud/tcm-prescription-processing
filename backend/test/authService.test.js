import assert from 'node:assert/strict';
import test from 'node:test';
import { signLoginToken } from '../src/services/authService.js';

test('signLoginToken stores the authorization context and creates a Redis session', async () => {
  let signedPayload;
  let signedOptions;
  const jwt = {
    sign(payload, options) {
      signedPayload = payload;
      signedOptions = options;
      return 'token';
    }
  };

  let createdSession;
  const token = await signLoginToken(jwt, {
    create: async (session) => {
      createdSession = session;
    }
  }, {
    id: 42,
    phone: '13800000000',
    role: 0,
    storeId: 7,
    password: 'hashed-password'
  });

  assert.equal(token, 'token');
  assert.equal(signedPayload.id, 42);
  assert.equal(signedPayload.role, 0);
  assert.equal(signedPayload.storeId, 7);
  assert.equal(signedPayload.phone, '13800000000');
  assert.match(signedPayload.jti, /^[0-9a-f-]{36}$/i);
  assert.deepEqual(createdSession, {
    accountType: 'admin', accountId: 42, jti: signedPayload.jti
  });
  assert.deepEqual(signedOptions, { expiresIn: '7d' });
});
