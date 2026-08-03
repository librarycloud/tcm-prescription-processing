import { request } from '../utils/request';

export function getMyPackages() {
  return request({ url: '/user/packages' });
}

export function getMyPackageDetail(id) {
  return request({ url: `/user/packages/${id}` });
}

export function getProfile() {
  return request({ url: '/user/me' });
}

export function updateProfile(data) {
  return request({ url: '/user/me', method: 'PUT', data });
}

export function sendEmailCode(email) {
  return request({ url: '/user/email/send-code', method: 'POST', data: { email } });
}

export function verifyEmail(email, code) {
  return request({ url: '/user/email/verify', method: 'POST', data: { email, code } });
}
