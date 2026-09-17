import request from './request';

export function getProfile() {
  return request.get('/user/me');
}

export function updateProfile(data) {
  return request.put('/user/me', data);
}

export function sendEmailCode(email) {
  return request.post('/user/email/send-code', { email });
}

export function verifyEmail(email, code) {
  return request.post('/user/email/verify', { email, code });
}
