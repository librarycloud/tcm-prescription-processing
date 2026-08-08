const DEFAULT_VERSION = '1.0.0';
const UPLOAD_DATE = '2026-08-08';

export function getAppInfo() {
  let version = DEFAULT_VERSION;

  try {
    const accountInfo = wx.getAccountInfoSync();
    version = accountInfo?.miniProgram?.version || DEFAULT_VERSION;
  } catch (error) {
    // Account information may be unavailable in older development environments.
  }

  return {
    version,
    uploadDate: UPLOAD_DATE
  };
}
