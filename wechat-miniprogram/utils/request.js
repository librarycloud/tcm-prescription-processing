import { getBaseUrl } from './config';
import { clearSession, getToken } from './auth';

export function request(options) {
  const token = getToken();

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${getBaseUrl()}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'content-type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success(res) {
        const body = res.data || {};
        if (body.code === 0) {
          resolve(body.data);
          return;
        }

        if (res.statusCode === 401) {
          clearSession();
          wx.reLaunch({ url: '/pages/login/login' });
        }

        wx.showToast({ title: body.message || '请求失败', icon: 'none' });
        reject(new Error(body.message || '请求失败'));
      },
      fail(error) {
        wx.showToast({ title: '网络异常', icon: 'none' });
        reject(error);
      }
    });
  });
}
