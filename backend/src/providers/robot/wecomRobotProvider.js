import { postRobotJson } from './http.js';
import { RobotProviderError } from './providerError.js';

export async function sendWecomRobot({ webhook, content }) {
  const result = await postRobotJson(webhook, { msgtype: 'markdown', markdown: { content } });
  if (Number(result.body?.errcode) !== 0) {
    throw new RobotProviderError(result.body?.errmsg || '企业微信机器人发送失败', String(result.body?.errcode || 'WECOM_ERROR'), result.response);
  }
  return { requestId: null, response: result.response };
}
