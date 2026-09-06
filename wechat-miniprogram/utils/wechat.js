export function getWechatLoginCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (result) => {
        if (result.code) resolve(result.code);
        else reject(new Error('微信登录失败'));
      },
      fail: reject
    });
  });
}

export function copyToClipboard(text, label = '') {
  if (!text) return;
  wx.setClipboardData({
    data: String(text).trim(),
    success() {
      if (wx.vibrateShort) {
        wx.vibrateShort({ type: 'light' });
      }
      wx.showToast({
        title: label ? `${label}已复制` : '已复制到剪贴板',
        icon: 'success',
        duration: 1500
      });
    }
  });
}

