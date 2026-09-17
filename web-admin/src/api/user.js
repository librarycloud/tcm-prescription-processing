import request from './request';

export function getProfile() {
  return request.get('/user/me');
}

export function updateProfile(data) {
  return request.put('/user/me', data);
}
