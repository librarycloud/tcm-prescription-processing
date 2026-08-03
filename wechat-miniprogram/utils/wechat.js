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
