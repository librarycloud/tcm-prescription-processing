import assert from 'node:assert/strict';
import test from 'node:test';
import { ROLES } from '../src/constants/roles.js';
import { AppError } from '../src/utils/appError.js';
import { createStoreAdmin, updateStoreAdmin } from '../src/services/storeAdminService.js';

const storeAdmin = { id: 7, role: ROLES.STORE_ADMIN, storeId: 3, accountType: 'admin' };

function createPrisma(current = null) {
  const calls = { created: null, updated: null };
  return {
    calls,
    prisma: {
      store: { findUnique: async ({ where }) => ({ id: where.id, status: 1, deletedAt: null }) },
      operationLog: { create: async () => ({}) },
      admin: {
        findFirst: async ({ where }) => (where.phone ? null : current),
        create: async ({ data }) => {
          calls.created = data;
          return { id: 20, ...data, store: { id: data.storeId, name: '测试门店', code: 'S003', status: 1 } };
        },
        update: async ({ data }) => {
          calls.updated = data;
          return { ...current, ...data, store: { id: current.storeId, name: '测试门店', code: 'S003', status: 1 } };
        },
        count: async () => 1
      }
    }
  };
}

test('store administrator creates a staff account only for their own store', async () => {
  const { prisma, calls } = createPrisma();

  await createStoreAdmin(prisma, {
    phone: '13800000001', password: 'password1', storeId: 3, role: ROLES.STORE_STAFF
  }, storeAdmin);

  assert.equal(calls.created.storeId, 3);
  assert.equal(calls.created.role, ROLES.STORE_STAFF);
  assert.notEqual(calls.created.password, 'password1');
});

test('store administrator cannot create an account for another store', async () => {
  const { prisma } = createPrisma();
  await assert.rejects(
    () => createStoreAdmin(prisma, {
      phone: '13800000001', password: 'password1', storeId: 4, role: ROLES.STORE_STAFF
    }, storeAdmin),
    (error) => error instanceof AppError && error.statusCode === 403
  );
});

test('store administrator can promote a same-store employee', async () => {
  const current = { id: 20, role: ROLES.STORE_STAFF, status: 1, storeId: 3, phone: '13800000001' };
  const { prisma, calls } = createPrisma(current);

  await updateStoreAdmin(prisma, 20, { role: ROLES.STORE_ADMIN }, storeAdmin);
  assert.equal(calls.updated.role, ROLES.STORE_ADMIN);
});

test('store administrator cannot change an account in another store', async () => {
  const current = { id: 20, role: ROLES.STORE_STAFF, status: 1, storeId: 4, phone: '13800000001' };
  const { prisma } = createPrisma(current);
  await assert.rejects(
    () => updateStoreAdmin(prisma, 20, { role: ROLES.STORE_ADMIN }, storeAdmin),
    (error) => error instanceof AppError && error.statusCode === 403
  );
});

test('the last enabled store administrator cannot be downgraded', async () => {
  const current = { id: 20, role: ROLES.STORE_ADMIN, status: 1, storeId: 3, phone: '13800000001' };
  const { prisma } = createPrisma(current);
  prisma.admin.count = async () => 0;

  await assert.rejects(
    () => updateStoreAdmin(prisma, 20, { role: ROLES.STORE_STAFF }, storeAdmin),
    (error) => error instanceof AppError && error.statusCode === 409
  );
});
