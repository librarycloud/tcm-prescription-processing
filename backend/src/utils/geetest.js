import crypto from 'crypto';

export async function verifyGeetest4(captchaId, captchaKey, validateData) {
  const { lot_number, captcha_output, pass_token, gen_time } = validateData;
  if (!lot_number || !captcha_output || !pass_token || !gen_time) {
    return false;
  }

  const signToken = crypto.createHmac('sha256', captchaKey).update(lot_number).digest('hex');

  const params = new URLSearchParams({
    lot_number,
    captcha_output,
    pass_token,
    gen_time,
    sign_token: signToken,
    captcha_id: captchaId
  });

  try {
    const response = await fetch('http://gcaptcha4.geetest.com/validate?' + params.toString());
    const data = await response.json();
    return data.result === 'success';
  } catch (err) {
    console.error('Geetest verify error:', err);
    return false;
  }
}
