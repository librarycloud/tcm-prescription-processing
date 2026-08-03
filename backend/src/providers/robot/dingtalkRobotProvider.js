import { createHmac } from 'node:crypto';
import { postRobotJson } from './http.js';
import { RobotProviderError } from './providerError.js';

function signedWebhook(webhook, secret) {
  if (!secret) return webhook;
  const timestamp = Date.now();
  const sign = createHmac('sha256', secret).update(`${timestamp}\n${secret}`).digest('base64');
  const url = new URL(webhook);
  url.searchParams.set('timestamp', String(timestamp));
  url.searchParams.set('sign', sign);
  return url.toString();
}

export async function sendDingtalkRobot({ webhook, secret, content }) {
  const title = content.split('\n')[0].replace(/[【】]/g, '') || '业务通知';
  const result = await postRobotJson(signedWebhook(webhook, secret), {
    msgtype: 'markdown',
    markdown: { title, text: content }
  });
  if (Number(result.body?.errcode) !== 0) {
    throw new RobotProviderError(result.body?.errmsg || '钉钉机器人发送失败', String(result.body?.errcode || 'DINGTALK_ERROR'), result.response);
  }
  return { requestId: null, response: result.response };
}
