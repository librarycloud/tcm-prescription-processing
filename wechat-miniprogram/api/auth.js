import { request } from '../utils/request';

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'POST',
    data
  });
}

export function userLogin(data) {
  return request({
    url: '/auth/user-login',
    method: 'POST',
    data
  });
}

export function wechatLogin(code) {
  return request({
    url: '/auth/wechat-login',
    method: 'POST',
    data: { code }
  });
}

export function bindWechat(code) {
  return request({
    url: '/auth/wechat-bind',
    method: 'POST',
    data: { code }
  });
}

export function rebindWechat(data) {
  return request({
    url: '/auth/wechat-rebind',
    method: 'POST',
    data
  });
}

export function bindWechatByPickupCode(data) {
  return request({
    url: '/auth/wechat-bind-pickup',
    method: 'POST',
    data
  });
}

export function getWechatStatus() {
  return request({ url: '/auth/wechat-status' });
}

export function unbindWechat(password) {
  return request({
    url: '/auth/wechat-unbind',
    method: 'POST',
    data: { password }
  });
}
