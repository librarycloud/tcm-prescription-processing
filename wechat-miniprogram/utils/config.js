const env = wx.getAccountInfoSync().miniProgram.envVersion;
const baseUrls = {
  develop: 'https://admin.trthz.com/api',
  trial: 'https://admin.trthz.com/api', // 替换为真实的测试环境域名
  release: 'https://admin.trthz.com/api'      // 替换为真实的正式环境域名
};

export function getBaseUrl() {
  return baseUrls[env] || baseUrls.release;
}
