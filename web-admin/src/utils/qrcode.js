import QRCode from 'qrcode';

export async function createQRCodeDataUrl(content, options = {}) {
  if (!content) return '';

  const text = String(content).replace(/^(\d{3})-(\d{3})$/, '$1$2');
  return QRCode.toDataURL(text, {
    errorCorrectionLevel: 'M',
    margin: 1,
    width: options.width || 220,
    color: {
      dark: '#1f2937',
      light: '#ffffff'
    }
  });
}
