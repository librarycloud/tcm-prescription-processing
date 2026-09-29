import request from './request';

export function login(data) {
  return request.post('/auth/login', data);
}

export function logout() {
  return request.post('/auth/logout');
}

export function getCaptcha() {
  return request.get('/admin/auth/captcha');
}
