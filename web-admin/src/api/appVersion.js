import request from './request';

export function getAndroidAppVersion() {
  return request.get('/app/version/android');
}

export function syncAndroidAppVersion() {
  return request.post('/app/version/android/sync');
}
