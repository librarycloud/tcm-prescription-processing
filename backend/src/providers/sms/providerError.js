export function providerError(code, message) {
  const error = new Error(message || '短信供应商返回失败');
  error.code = String(code || 'SMS_PROVIDER_ERROR');
  return error;
}
