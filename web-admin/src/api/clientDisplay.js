import request from './request';

/**
 * 获取管理员配置（含当前 Hub 探测结果）
 */
export function getClientDisplaySettings() {
  return request.get('/admin/client-display');
}

/**
 * 更新客户端展示配置
 */
export function updateClientDisplaySettings(data) {
  return request.put('/admin/client-display', data);
}

/**
 * 上传微信小程序码图片
 */
export function uploadWechatQrcode(file) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/admin/client-display/qrcode', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
}

/**
 * 获取客户端展示数据（供右上角弹窗使用）
 */
export function getClientDisplayInfo() {
  return request.get('/client-display/info');
}
