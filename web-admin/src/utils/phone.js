export function maskPhone(phone) {
  const value = String(phone || '');
  if (value.length !== 11) return value || '-';
  return `${value.slice(0, 3)}****${value.slice(7)}`;
}

export function isValidPhone(phone) {
  return /^1[3-9]\d{9}$/.test(String(phone || '').trim());
}
