import request from './request';

export function getAndroidAppVersion() {
  return request.get('/app/version/android');
}

export function syncAndroidAppVersion() {
  // APK 下载可能需要较长时间，不能使用普通接口的 15 秒超时。
  // 发送空 JSON 对象，避免 Axios 将空请求体标记为 urlencoded 导致后端返回 415。
  return request.post('/app/version/android/sync', {}, { timeout: 180000 });
}
