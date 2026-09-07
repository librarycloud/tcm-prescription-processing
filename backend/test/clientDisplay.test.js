import test from 'node:test';
import assert from 'node:assert/strict';
import {
  getClientDisplayConfig,
  saveClientDisplayConfig,
  saveWechatQrcode,
  getClientDisplayInfo
} from '../src/services/clientDisplayService.js';

test('client display service loads default config and saves updates', async () => {
  const initial = await getClientDisplayConfig();
  assert.ok(initial.wechat);
  assert.ok(initial.android);

  const updated = await saveClientDisplayConfig({
    wechat: {
      appName: '测试药房小程序',
      appId: 'wx1234567890'
    },
    android: {
      displayName: '测试药房助手 Android',
      releaseHubAppId: 'tcm-admin'
    },
    announcement: '欢迎使用移动客户端'
  });

  assert.equal(updated.wechat.appName, '测试药房小程序');
  assert.equal(updated.wechat.appId, 'wx1234567890');
  assert.equal(updated.android.displayName, '测试药房助手 Android');
  assert.equal(updated.android.releaseHubAppId, 'tcm-admin');
  assert.equal(updated.announcement, '欢迎使用移动客户端');

  // Verify saveWechatQrcode
  const fakePng = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
  const qrRes = await saveWechatQrcode(fakePng, 'image/png');
  assert.equal(qrRes.qrcodeUrl, '/client-display/qrcode');

  const info = await getClientDisplayInfo();
  assert.equal(info.wechat.appName, '测试药房小程序');
  assert.equal(info.wechat.qrcodeUrl, '/client-display/qrcode');
  assert.equal(info.announcement, '欢迎使用移动客户端');
});
