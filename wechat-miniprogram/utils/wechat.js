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

/**
 * 获取设备硬件信息（brand, model, system, platform 等）
 * 微信 3.7.0+ 起支持 HarmonyOS (platform: 'ohos' 或 'ohos_pc')
 */
export function getDeviceInfo() {
  if (typeof wx !== 'undefined' && typeof wx.getDeviceInfo === 'function') {
    return wx.getDeviceInfo() || {};
  }
  return {};
}

/**
 * 获取窗口及屏幕渲染信息（pixelRatio, screenWidth, safeArea 等）
 */
export function getWindowInfo() {
  if (typeof wx !== 'undefined' && typeof wx.getWindowInfo === 'function') {
    return wx.getWindowInfo() || {};
  }
  return {};
}

/**
 * 获取小程序自身基础信息（SDKVersion, version, theme 等）
 */
export function getAppBaseInfo() {
  if (typeof wx !== 'undefined' && typeof wx.getAppBaseInfo === 'function') {
    return wx.getAppBaseInfo() || {};
  }
  return {};
}

/**
 * 判断当前是否处于华为鸿蒙系统环境 (HarmonyOS NEXT / ohos)
 * 微信基础库从 3.7.0 起，真机 platform 为 'ohos'（手机）或 'ohos_pc'（PC），
 * 开发者工具模拟时 platform 为 'devtools' 且 system 包含 'HarmonyOS'
 */
export function isHarmonyOS() {
  const device = getDeviceInfo();
  const platform = (device.platform || '').toLowerCase();
  const system = (device.system || '').toLowerCase();
  return platform === 'ohos' || platform === 'ohos_pc' || system.includes('harmonyos');
}

/**
 * 判断当前是否为 iOS 环境
 */
export function isIOS() {
  const device = getDeviceInfo();
  return (device.platform || '').toLowerCase() === 'ios';
}

/**
 * 判断当前是否为 Android 环境
 */
export function isAndroid() {
  const device = getDeviceInfo();
  return (device.platform || '').toLowerCase() === 'android';
}

