import { ROBOT_PLATFORMS } from '../../constants/robotNotification.js';
import { AppError } from '../../utils/appError.js';
import { sendDingtalkRobot } from './dingtalkRobotProvider.js';
import { sendFeishuRobot } from './feishuRobotProvider.js';
import { sendWecomRobot } from './wecomRobotProvider.js';

const HOSTS = {
  [ROBOT_PLATFORMS.WECOM]: new Set(['qyapi.weixin.qq.com']),
  [ROBOT_PLATFORMS.DINGTALK]: new Set(['oapi.dingtalk.com']),
  [ROBOT_PLATFORMS.FEISHU]: new Set(['open.feishu.cn', 'open.larksuite.com'])
};

export function validateRobotWebhook(platform, webhookValue) {
  const webhook = String(webhookValue || '').trim();
  let url;
  try {
    url = new URL(webhook);
  } catch {
    throw new AppError('机器人 Webhook 格式不正确', 400);
  }
  if (url.protocol !== 'https:' || !HOSTS[platform]?.has(url.hostname.toLowerCase())) {
    throw new AppError('机器人 Webhook 必须使用对应平台的官方 HTTPS 地址', 400);
  }
  if (url.username || url.password) throw new AppError('机器人 Webhook 不能包含账号信息', 400);
  return url.toString();
}

export async function sendRobotMessage(platform, options) {
  const webhook = validateRobotWebhook(platform, options.webhook);
  if (platform === ROBOT_PLATFORMS.WECOM) return sendWecomRobot({ ...options, webhook });
  if (platform === ROBOT_PLATFORMS.DINGTALK) return sendDingtalkRobot({ ...options, webhook });
  if (platform === ROBOT_PLATFORMS.FEISHU) return sendFeishuRobot({ ...options, webhook });
  throw new AppError('不支持的机器人平台', 400);
}
