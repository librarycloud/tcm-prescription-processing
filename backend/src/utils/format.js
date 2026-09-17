export function formatPickupCode(value) {
  const text = String(value || '').trim();
  return /^\d{6}$/.test(text) ? `${text.slice(0, 3)}-${text.slice(3)}` : text;
}
