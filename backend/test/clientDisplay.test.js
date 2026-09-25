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
      releaseHubUrl: 'https://hub.example.com///',
      releaseHubAppId: 'tcm-admin'
    },
    ios: {
      displayName: '测试药房助手 iOS',
      testflightUrl: 'https://testflight.apple.com/join/xxxx'
    },
    serverUrl: 'https://api.tcm.example.com///',
    announcement: '欢迎使用移动客户端'
  });

  assert.equal(updated.wechat.appName, '测试药房小程序');
  assert.equal(updated.wechat.appId, 'wx1234567890');
  assert.equal(updated.android.displayName, '测试药房助手 Android');
  assert.equal(updated.android.releaseHubUrl, 'https://hub.example.com');
  assert.equal(updated.android.releaseHubAppId, 'tcm-admin');
  assert.equal(updated.ios.displayName, '测试药房助手 iOS');
  assert.equal(updated.ios.testflightUrl, 'https://testflight.apple.com/join/xxxx');
  assert.equal(updated.serverUrl, 'https://api.tcm.example.com');
  assert.equal(updated.announcement, '欢迎使用移动客户端');

  // Verify saveWechatQrcode with a valid 1x1 PNG (67 bytes)
  const validPng = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
    'base64'
  );
  const qrRes = await saveWechatQrcode(validPng, 'image/png');
  assert.ok(qrRes.qrcodeUrl.startsWith('data:image/png;base64,'));

  const info = await getClientDisplayInfo();
  assert.equal(info.wechat.appName, '测试药房小程序');
  assert.ok(info.wechat.qrcodeUrl?.startsWith('data:image/png;base64,'));
  assert.equal(info.ios.displayName, '测试药房助手 iOS');
  assert.equal(info.ios.testflightUrl, 'https://testflight.apple.com/join/xxxx');
  assert.equal(info.serverUrl, 'https://api.tcm.example.com');
  assert.equal(info.announcement, '欢迎使用移动客户端');
});
