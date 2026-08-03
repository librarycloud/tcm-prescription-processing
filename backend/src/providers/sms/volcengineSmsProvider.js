import { createHash, createHmac } from 'node:crypto';
import { providerError } from './providerError.js';

const HOST = 'sms.volcengineapi.com';
const SERVICE = 'volcSMS';
const ACTION = 'SendSms';
const VERSION = '2020-01-01';

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function hmac(key, value) {
  return createHmac('sha256', key).update(value).digest();
}

export async function sendVolcengineSms({ config, secretKey, phone, template, keyedValues }) {
  const region = config.region || 'cn-north-1';
  const body = JSON.stringify({
    SmsAccount: config.smsAccount,
    Sign: config.signName,
    TemplateID: template.templateCode,
    TemplateParam: JSON.stringify(keyedValues),
    PhoneNumbers: phone,
    Tag: '',
    UserExtCode: ''
  });
  const dateTime = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
  const shortDate = dateTime.slice(0, 8);
  const bodyHash = sha256(body);
  const canonicalQuery = `Action=${ACTION}&Version=${VERSION}`;
  const canonicalHeaders = `x-content-sha256:${bodyHash}\nx-date:${dateTime}`;
  const signedHeaders = 'x-content-sha256;x-date';
  const canonicalRequest = [
    'POST',
    '/',
    canonicalQuery,
    `${canonicalHeaders}\n`,
    signedHeaders,
    bodyHash
  ].join('\n');
  const credentialScope = `${shortDate}/${region}/${SERVICE}/request`;
  const stringToSign = [
    'HMAC-SHA256',
    dateTime,
    credentialScope,
    sha256(canonicalRequest)
  ].join('\n');
  const dateKey = hmac(secretKey, shortDate);
  const regionKey = hmac(dateKey, region);
  const serviceKey = hmac(regionKey, SERVICE);
  const signingKey = hmac(serviceKey, 'request');
  const signature = createHmac('sha256', signingKey).update(stringToSign).digest('hex');
  const authorization =
    `HMAC-SHA256 Credential=${config.accessKeyId}/${credentialScope}, ` +
    `SignedHeaders=${signedHeaders}, Signature=${signature}`;

  const httpResponse = await fetch(`https://${HOST}/?${canonicalQuery}`, {
    method: 'POST',
    signal: AbortSignal.timeout(15_000),
    headers: {
      Authorization: authorization,
      'Content-Type': 'application/json; charset=utf-8',
      'X-Content-Sha256': bodyHash,
      'X-Date': dateTime
    },
    body
  });
  const response = await httpResponse.json();
  const metadata = response.ResponseMetadata || {};
  if (!httpResponse.ok || (metadata.Code && metadata.Code !== 'Success')) {
    throw providerError(
      metadata.Code || httpResponse.status,
      metadata.Message || '火山引擎短信发送失败'
    );
  }
  return {
    requestId: metadata.RequestId || '',
    messageId: response.Result?.MessageID?.[0] || '',
    message: metadata.Message || '发送成功'
  };
}
