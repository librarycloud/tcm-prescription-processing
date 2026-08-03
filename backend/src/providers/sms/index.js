import { sendAliyunSms } from './aliyunSmsProvider.js';
import { sendTencentSms } from './tencentSmsProvider.js';
import { sendVolcengineSms } from './volcengineSmsProvider.js';

const providers = {
  tencent: sendTencentSms,
  aliyun: sendAliyunSms,
  volcengine: sendVolcengineSms
};

export function sendSmsByProvider(provider, options) {
  if (!providers[provider]) throw new Error('不支持的短信供应商');
  return providers[provider](options);
}
