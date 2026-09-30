import crypto from 'crypto';

const captchaStore = new Map();

export function generateCaptcha() {
  const num1 = Math.floor(Math.random() * 20) + 1;
  const num2 = Math.floor(Math.random() * 20) + 1;
  const text = `${num1} + ${num2} = ?`;
  const answer = String(num1 + num2);
  
  const captchaId = crypto.randomUUID();
  captchaStore.set(captchaId, { answer, expires: Date.now() + 5 * 60000 });
  
  // Cleanup old
  for (const [key, val] of captchaStore.entries()) {
    if (val.expires < Date.now()) captchaStore.delete(key);
  }
  
  return { captchaId, text };
}

export function verifyCaptcha(captchaId, code) {
  if (!captchaId || !code) return false;
  const record = captchaStore.get(captchaId);
  if (!record || record.expires < Date.now()) return false;
  captchaStore.delete(captchaId);
  return record.answer === String(code).trim();
}
