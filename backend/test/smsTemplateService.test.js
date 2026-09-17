import assert from 'node:assert/strict';
import test from 'node:test';
import { TEMPLATE_SOURCES } from '../src/constants/notification.js';
import {
  buildBusinessValues,
  resolveTemplate,
  validateVariableMapping
} from '../src/services/smsTemplateService.js';

const packageData = {
  receiverName: '张三',
  pickupCode: '123456',
  itemName: '代煎药',
  pickupMethod: 2,
  expressTrackingNo: 'SF1234567890',
  store: {
    name: '苏州店',
    address: '苏州市姑苏区测试路 1 号',
    phone: '0512-12345678'
  }
};

test('SMS templates expose store and express tracking variables', () => {
  assert.ok(TEMPLATE_SOURCES.includes('storeName'));
  assert.ok(TEMPLATE_SOURCES.includes('storeAddress'));
  assert.ok(TEMPLATE_SOURCES.includes('storePhone'));
  assert.ok(TEMPLATE_SOURCES.includes('expressTrackingNo'));

  const values = buildBusinessValues(packageData);
  assert.equal(values.storeName, '苏州店');
  assert.equal(values.storeAddress, '苏州市姑苏区测试路 1 号');
  assert.equal(values.storePhone, '0512-12345678');
  assert.equal(values.expressTrackingNo, 'SF1234567890');
});

test('SMS template mappings and previews resolve the new variables', () => {
  const variableMapping = validateVariableMapping([
    { key: 'store', source: 'storeName' },
    { key: 'address', source: 'storeAddress' },
    { key: 'phone', source: 'storePhone' },
    { key: 'tracking', source: 'expressTrackingNo' }
  ]);
  const resolved = resolveTemplate(
    {
      variableMapping,
      contentPreview:
        '{{storeName}}，地址：{{storeAddress}}，电话：{{storePhone}}，快递单号：{{expressTrackingNo}}'
    },
    packageData
  );

  assert.deepEqual(resolved.keyedValues, {
    store: '苏州店',
    address: '苏州市姑苏区测试路 1 号',
    phone: '0512-12345678',
    tracking: 'SF1234567890'
  });
  assert.equal(
    resolved.preview,
    '苏州店，地址：苏州市姑苏区测试路 1 号，电话：0512-12345678，快递单号：SF1234567890'
  );
});
