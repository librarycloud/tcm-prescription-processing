import assert from 'node:assert/strict';
import test from 'node:test';
import { signLoginToken } from '../src/services/authService.js';

test('signLoginToken stores the authorization context in the JWT payload', () => {
  let signedPayload;
  let signedOptions;
  const jwt = {
    sign(payload, options) {
      signedPayload = payload;
      signedOptions = options;
      return 'token';
    }
  };

  const token = signLoginToken(jwt, {
    id: 42,
    phone: '13800000000',
    role: 0,
    storeId: 7,
    password: 'hashed-password'
  });

  assert.equal(token, 'token');
  assert.deepEqual(signedPayload, {
    id: 42,
    role: 0,
    storeId: 7,
    phone: '13800000000'
  });
  assert.deepEqual(signedOptions, { expiresIn: '7d' });
});
