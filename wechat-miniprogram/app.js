// 兼容拦截旧版废弃 API wx.getSystemInfo / wx.getSystemInfoSync
// 微信官方自 3.7.0 起正式支持 HarmonyOS，并强烈建议改用 getDeviceInfo / getWindowInfo / getAppBaseInfo。
// 此处将旧 API 平滑重定向至细粒度现代 API，避免第三方库（如 TDesign 等）底层执行时触发开发者工具的废弃警告。
if (typeof wx !== 'undefined') {
  const getModernSystemInfo = () => {
    const windowInfo = typeof wx.getWindowInfo === 'function' ? wx.getWindowInfo() : {};
    const deviceInfo = typeof wx.getDeviceInfo === 'function' ? wx.getDeviceInfo() : {};
    const appBaseInfo = typeof wx.getAppBaseInfo === 'function' ? wx.getAppBaseInfo() : {};
    return {
      ...windowInfo,
      ...deviceInfo,
      ...appBaseInfo
    };
  };

  try {
    wx.getSystemInfoSync = getModernSystemInfo;
  } catch (e) {
    try {
      Object.defineProperty(wx, 'getSystemInfoSync', {
        value: getModernSystemInfo,
        writable: true,
        configurable: true
      });
    } catch (err) {}
  }

  try {
    wx.getSystemInfo = function(options = {}) {
      const res = getModernSystemInfo();
      if (typeof options.success === 'function') options.success(res);
      if (typeof options.complete === 'function') options.complete(res);
      return Promise.resolve(res);
    };
  } catch (e) {
    try {
      Object.defineProperty(wx, 'getSystemInfo', {
        value: function(options = {}) {
          const res = getModernSystemInfo();
          if (typeof options.success === 'function') options.success(res);
          if (typeof options.complete === 'function') options.complete(res);
          return Promise.resolve(res);
        },
        writable: true,
        configurable: true
      });
    } catch (err) {}
  }
}

App({
  globalData: {
    baseUrl: 'http://192.168.10.227:3000'
  }
});

