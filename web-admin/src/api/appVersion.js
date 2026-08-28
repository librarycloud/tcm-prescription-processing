import request from './request';

export function getAndroidAppVersion() {
  return request.get('/app/version/android');
}

export function syncAndroidAppVersion() {
  // APK 下载可能需要较长时间，不能使用普通接口的 15 秒超时。
  return request.post('/app/version/android/sync', null, { timeout: 180000 });
}
