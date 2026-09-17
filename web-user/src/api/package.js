import request from './request';

export function getUserPackages() {
  return request.get('/user/packages');
}

export function getUserPackageDetail(id) {
  return request.get(`/user/packages/${id}`);
}
