import { createHmac } from 'node:crypto';
import { postRobotJson } from './http.js';
import { RobotProviderError } from './providerError.js';

export async function sendFeishuRobot({ webhook, secret, content }) {
  const payload = { msg_type: 'text', content: { text: content } };
  if (secret) {
    const timestamp = Math.floor(Date.now() / 1000);
    payload.timestamp = String(timestamp);
    payload.sign = createHmac('sha256', `${timestamp}\n${secret}`).update('').digest('base64');
  }
  const result = await postRobotJson(webhook, payload);
  const code = Number(result.body?.code ?? result.body?.StatusCode ?? 0);
  if (code !== 0) {
    throw new RobotProviderError(result.body?.msg || result.body?.StatusMessage || '飞书机器人发送失败', String(code), result.response);
  }
  return { requestId: null, response: result.response };
}
