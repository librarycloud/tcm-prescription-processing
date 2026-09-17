import { createHash, createHmac } from 'node:crypto';
import { providerError } from './providerError.js';

const HOST = 'sms.tencentcloudapi.com';
const SERVICE = 'sms';
const ACTION = 'SendSms';
const VERSION = '2021-01-11';

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function hmac(key, value) {
  return createHmac('sha256', key).update(value).digest();
}

export async function sendTencentSms({ config, secretKey, phone, template, values }) {
  const payload = JSON.stringify({
    PhoneNumberSet: [`+86${phone}`],
    SmsSdkAppId: config.sdkAppId,
    SignName: config.signName,
    TemplateId: template.templateCode,
    TemplateParamSet: values
  });
  const timestamp = Math.floor(Date.now() / 1000);
  const date = new Date(timestamp * 1000).toISOString().slice(0, 10);
  const canonicalHeaders =
    `content-type:application/json; charset=utf-8\nhost:${HOST}\nx-tc-action:${ACTION.toLowerCase()}\n`;
  const signedHeaders = 'content-type;host;x-tc-action';
  const canonicalRequest = ['POST', '/', '', canonicalHeaders, signedHeaders, sha256(payload)].join(
    '\n'
  );
  const credentialScope = `${date}/${SERVICE}/tc3_request`;
  const stringToSign = [
    'TC3-HMAC-SHA256',
    timestamp,
    credentialScope,
    sha256(canonicalRequest)
  ].join('\n');
  const secretDate = hmac(`TC3${secretKey}`, date);
  const secretService = hmac(secretDate, SERVICE);
  const secretSigning = hmac(secretService, 'tc3_request');
  const signature = createHmac('sha256', secretSigning).update(stringToSign).digest('hex');
  const authorization =
    `TC3-HMAC-SHA256 Credential=${config.accessKeyId}/${credentialScope}, ` +
    `SignedHeaders=${signedHeaders}, Signature=${signature}`;

  const httpResponse = await fetch(`https://${HOST}`, {
    method: 'POST',
    signal: AbortSignal.timeout(15_000),
    headers: {
      Authorization: authorization,
      'Content-Type': 'application/json; charset=utf-8',
      Host: HOST,
      'X-TC-Action': ACTION,
      'X-TC-Version': VERSION,
      'X-TC-Timestamp': String(timestamp),
      'X-TC-Region': config.region || 'ap-guangzhou'
    },
    body: payload
  });
  const data = await httpResponse.json();
  const response = data.Response || {};
  if (!httpResponse.ok || response.Error) {
    throw providerError(response.Error?.Code || httpResponse.status, response.Error?.Message);
  }
  const status = response.SendStatusSet?.[0];
  if (!status || status.Code !== 'Ok') {
    throw providerError(status?.Code, status?.Message || '腾讯云短信发送失败');
  }
  return {
    requestId: response.RequestId || '',
    messageId: status.SerialNo || '',
    message: status.Message || '发送成功'
  };
}
