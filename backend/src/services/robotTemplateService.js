import { ROBOT_EVENT_DEFINITIONS } from '../constants/robotNotification.js';
import { AppError } from '../utils/appError.js';

const VARIABLE_PATTERN = /\{\{\s*([A-Za-z][A-Za-z0-9]*)\s*\}\}/g;

export function extractTemplateVariables(content) {
  const variables = [];
  for (const match of String(content || '').matchAll(VARIABLE_PATTERN)) variables.push(match[1]);
  return [...new Set(variables)];
}

export function validateRobotTemplate(eventCode, contentValue) {
  const definition = ROBOT_EVENT_DEFINITIONS[eventCode];
  if (!definition) throw new AppError('不支持的机器人通知事件', 400);
  const content = String(contentValue || '').trim();
  if (!content) throw new AppError('通知模板不能为空', 400);
  if (content.length > 4000) throw new AppError('通知模板不能超过 4000 个字符', 400);
  const leftovers = content.replace(VARIABLE_PATTERN, '');
  if (leftovers.includes('{{') || leftovers.includes('}}')) {
    throw new AppError('通知模板包含未闭合的变量', 400);
  }
  const allowed = new Set(definition.variables.map((item) => item.key));
  const unknown = extractTemplateVariables(content).filter((key) => !allowed.has(key));
  if (unknown.length) throw new AppError(`模板包含不支持的变量：${unknown.join('、')}`, 400);
  return content;
}

export function maskPhone(value) {
  const phone = String(value || '').trim();
  if (!phone) return '-';
  if (phone.length < 7) return `${phone.slice(0, 2)}****`;
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

function formatValue(value) {
  if (value === null || value === undefined || value === '') return '-';
  if (value instanceof Date) {
    return value.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' });
  }
  return String(value);
}

export function renderRobotTemplate(content, variables = {}) {
  return String(content || '').replace(VARIABLE_PATTERN, (_match, key) => formatValue(variables[key]));
}

export function eventDefinitionList() {
  return Object.entries(ROBOT_EVENT_DEFINITIONS).map(([eventCode, definition]) => ({
    eventCode,
    name: definition.name,
    defaultEnabled: definition.defaultEnabled,
    defaultTemplate: definition.defaultTemplate,
    variables: definition.variables
  }));
}
