let isScanning = false;

/**
 * Safe scanCode with debounce locking, light haptic feedback, and error suppression on user cancel.
 */
export function safeScanCode(options = {}) {
  if (isScanning) {
    return Promise.reject(new Error('SCAN_IN_PROGRESS'));
  }
  isScanning = true;

  return new Promise((resolve, reject) => {
    wx.scanCode({
      scanType: options.scanType || ['qrCode', 'barCode'],
      onlyFromCamera: options.onlyFromCamera ?? false,
      success(res) {
        try {
          wx.vibrateShort({ type: 'light' });
        } catch (e) {
          // Vibration is best effort
        }
        resolve(res);
      },
      fail(err) {
        const errMsg = String(err?.errMsg || '');
        if (errMsg.includes('cancel')) {
          reject(new Error('USER_CANCELLED'));
        } else {
          wx.showToast({ title: '扫码未识别，请重试', icon: 'none' });
          reject(err);
        }
      },
      complete() {
        setTimeout(() => {
          isScanning = false;
        }, 500);
      }
    });
  });
}
