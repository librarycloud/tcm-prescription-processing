import { RobotProviderError } from './providerError.js';

export async function postRobotJson(url, payload) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5000);
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json; charset=utf-8' },
      body: JSON.stringify(payload),
      redirect: 'error',
      signal: controller.signal
    });
    const text = (await response.text()).slice(0, 4000);
    let body;
    try {
      body = JSON.parse(text);
    } catch {
      body = { message: text };
    }
    if (!response.ok) {
      throw new RobotProviderError(`机器人接口返回 HTTP ${response.status}`, `HTTP_${response.status}`, text);
    }
    return { body, response: text };
  } catch (error) {
    if (error instanceof RobotProviderError) throw error;
    if (error?.name === 'AbortError') throw new RobotProviderError('机器人接口请求超时', 'TIMEOUT');
    throw new RobotProviderError(error?.message || '机器人接口请求失败', error?.code || 'NETWORK_ERROR');
  } finally {
    clearTimeout(timeout);
  }
}
