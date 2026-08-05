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

export function uploadFile(options) {
  const token = getToken();
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${getBaseUrl()}${options.url}`,
      filePath: options.filePath,
      name: options.name || 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      formData: options.formData || {},
      success(res) {
        let body = {};
        try {
          body = JSON.parse(res.data || '{}');
        } catch {
          body = {};
        }
        if (body.code === 0) {
          resolve(body.data);
          return;
        }
        if (res.statusCode === 401) {
          clearSession();
          wx.reLaunch({ url: '/pages/login/login' });
        }
        wx.showToast({ title: body.message || '上传失败', icon: 'none' });
        reject(new Error(body.message || '上传失败'));
      },
      fail(error) {
        wx.showToast({ title: '网络异常', icon: 'none' });
        reject(error);
      }
    });
  });
}
