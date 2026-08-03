import AlibabaSmsSdk, { SendSmsRequest } from '@alicloud/dysmsapi20170525';
import { Config as AlibabaConfig } from '@alicloud/openapi-client';
import { providerError } from './providerError.js';

export async function sendAliyunSms({ config, secretKey, phone, template, keyedValues }) {
  const AlibabaSmsClient = AlibabaSmsSdk.default;
  const clientConfig = new AlibabaConfig({
    accessKeyId: config.accessKeyId,
    accessKeySecret: secretKey,
    regionId: config.region || 'cn-hangzhou',
    endpoint: 'dysmsapi.aliyuncs.com'
  });
  const client = new AlibabaSmsClient(clientConfig);
  const response = await client.sendSms(
    new SendSmsRequest({
      phoneNumbers: phone,
      signName: config.signName,
      templateCode: template.templateCode,
      templateParam: JSON.stringify(keyedValues)
    })
  );
  const body = response.body || response;
  if (body.code !== 'OK') {
    throw providerError(body.code, body.message || '阿里云短信发送失败');
  }
  return {
    requestId: body.requestId || '',
    messageId: body.bizId || '',
    message: body.message || '发送成功'
  };
}
