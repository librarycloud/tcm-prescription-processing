let isScanning = false;

/**
 * Safe scanCode with debounce locking, light haptic feedback, and error suppression on user cancel.
 */
export function safeScanCode(options = {}) {
  const usesCallbacks = typeof options.success === 'function' ||
    typeof options.fail === 'function' ||
    typeof options.complete === 'function';
  if (isScanning) {
    const error = new Error('SCAN_IN_PROGRESS');
    if (typeof options.fail === 'function') options.fail(error);
    return usesCallbacks ? Promise.resolve(null) : Promise.reject(error);
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
        if (typeof options.success === 'function') options.success(res);
        resolve(res);
      },
      fail(err) {
        const errMsg = String(err?.errMsg || '');
        const error = errMsg.includes('cancel') ? new Error('USER_CANCELLED') : err;
        if (errMsg.includes('cancel')) {
          // User cancellation is expected and should stay silent.
        } else {
          wx.showToast({ title: '扫码未识别，请重试', icon: 'none' });
        }
        if (typeof options.fail === 'function') options.fail(error);
        if (usesCallbacks) resolve(null);
        else reject(error);
      },
      complete() {
        if (typeof options.complete === 'function') options.complete();
        setTimeout(() => {
          isScanning = false;
        }, 500);
      }
    });
  });
}
