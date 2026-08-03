import assert from 'node:assert/strict';
import test from 'node:test';
import { validateRobotWebhook } from '../src/providers/robot/index.js';
import {
  extractTemplateVariables,
  maskPhone,
  renderRobotTemplate,
  validateRobotTemplate
} from '../src/services/robotTemplateService.js';
import { publishRobotNotificationEvent } from '../src/services/robotNotificationService.js';
import { publishProcessingCompletedRobotEvent } from '../src/services/robotBusinessEventService.js';

test('robot templates validate event variables and render missing values safely', () => {
  const content = validateRobotTemplate(
    'PACKAGE_CREATED',
    '【包裹】{{storeName}} {{receiverName}} {{itemInfo}}'
  );
  assert.deepEqual(extractTemplateVariables(content), ['storeName', 'receiverName', 'itemInfo']);
  assert.equal(
    renderRobotTemplate(content, { storeName: '园区店', receiverName: '张三', itemInfo: '' }),
    '【包裹】园区店 张三 -'
  );
  assert.throws(
    () => validateRobotTemplate('PACKAGE_CREATED', '{{unknownValue}}'),
    /不支持的变量/
  );
  assert.throws(
    () => validateRobotTemplate('PACKAGE_CREATED', '{{storeName}'),
    /未闭合/
  );
});

test('phone masking does not expose the full internal notification value', () => {
  assert.equal(maskPhone('13800138000'), '138****8000');
  assert.equal(maskPhone(''), '-');
});

test('robot webhook validation only accepts official HTTPS hosts', () => {
  assert.match(
    validateRobotWebhook('wecom', 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test'),
    /^https:\/\/qyapi\.weixin\.qq\.com\//
  );
  assert.doesNotThrow(() => validateRobotWebhook('dingtalk', 'https://oapi.dingtalk.com/robot/send?access_token=test'));
  assert.doesNotThrow(() => validateRobotWebhook('feishu', 'https://open.feishu.cn/open-apis/bot/v2/hook/test'));
  assert.throws(() => validateRobotWebhook('wecom', 'http://qyapi.weixin.qq.com/test'), /官方 HTTPS/);
  assert.throws(() => validateRobotWebhook('wecom', 'https://example.com/test'), /官方 HTTPS/);
});

test('business events fan out to matching robot configs with message snapshots', async () => {
  let eventCreate;
  let configWhere;
  let deliveryData;
  const transaction = {
    robotNotificationEvent: {
      upsert: async ({ create }) => {
        eventCreate = create;
        return { id: 91, ...create };
      }
    },
    robotEventConfig: {
      findMany: async ({ where }) => {
        configWhere = where;
        return [
          { robotId: 1, templateContent: '总部：{{storeName}}', robot: { platform: 'wecom' } },
          { robotId: 2, templateContent: '门店：{{storeName}}', robot: { platform: 'feishu' } }
        ];
      }
    },
    robotDeliveryLog: {
      createMany: async ({ data }) => { deliveryData = data; }
    }
  };
  const prisma = { $transaction: async (callback) => callback(transaction) };

  const result = await publishRobotNotificationEvent(prisma, {
    eventKey: 'PACKAGE_CREATED:41',
    eventCode: 'PACKAGE_CREATED',
    businessId: 41,
    primaryStoreId: 3,
    relatedStoreIds: [3, 4, 3],
    operatorId: 8,
    variables: { storeName: '园区店' }
  });

  assert.deepEqual(eventCreate.relatedStoreIds, [3, 4]);
  assert.deepEqual(configWhere.robot.is.OR[1].storeId.in, [3, 4]);
  assert.equal(deliveryData.length, 2);
  assert.equal(deliveryData[0].renderedContent, '总部：园区店');
  assert.equal(deliveryData[1].renderedContent, '门店：园区店');
  assert.deepEqual(result, { eventId: 91, deliveryCount: 2 });
});

test('processing completion resolves pickup, reminder and operator display names', async () => {
  let eventVariables;
  const transaction = {
    robotNotificationEvent: {
      upsert: async ({ create }) => {
        eventVariables = create.variables;
        return { id: 92, ...create };
      }
    },
    robotEventConfig: {
      findMany: async () => [{
        robotId: 1,
        templateContent: '{{pickupMethod}}/{{notifyType}}/{{operatorName}}',
        robot: { platform: 'wecom' }
      }]
    },
    robotDeliveryLog: { createMany: async () => {} }
  };
  const prisma = {
    user: { findUnique: async () => ({ name: '王医生', nickname: '老王' }) },
    dictionary: { findFirst: async () => ({ name: '微信提醒' }) },
    robotNotificationEvent: {},
    robotEventConfig: {},
    robotDeliveryLog: {},
    $transaction: async (callback) => callback(transaction)
  };

  await publishProcessingCompletedRobotEvent(
    prisma,
    {
      id: 21,
      storeId: 3,
      store: { name: '园区店' },
      prescription: { prescriptionNo: 'CF001', customerName: '张三' },
      processType: { name: '代煎' },
      totalDose: 7,
      bagCount: 14,
      pickupMethod: 2,
      notifyType: 9,
      package: { pickupCode: '123456' },
      finishDate: new Date('2026-07-26T08:00:00Z')
    },
    { id: 8, phone: '13800138000' }
  );

  assert.equal(eventVariables.pickupMethod, '快递');
  assert.equal(eventVariables.notifyType, '微信提醒');
  assert.equal(eventVariables.operatorName, '王医生');
});
