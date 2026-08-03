export function normalizeExpressTrackingNo(value) {
  const text = String(value || '').trim();
  const sfMatch = text.match(/SF\s*[-:]?\s*(\d{10,16})/i);
  if (sfMatch) return `SF${sfMatch[1]}`;

  const compact = text.replace(/\s+/g, '');
  const trackingMatch = compact.match(/[A-Z]{1,4}\d{8,20}/i);
  return (trackingMatch?.[0] || compact).toUpperCase().slice(0, 100);
}
