export const DEFAULT_USAGE_METHOD = ['一天两次', '一次一袋', '早晚餐后温服', '具体请遵医嘱'].join(
  '\n'
);

export function normalizeUsageMethod(value) {
  return String(value || '')
    .replace(/\r\n?/g, '\n')
    .trim();
}

export function usageMethodForPrint(value) {
  const normalized = normalizeUsageMethod(value);
  if (!normalized) return DEFAULT_USAGE_METHOD;
  if (normalized.includes('\n')) return normalized;
  return ['一天两次', '一次一袋', normalized, '具体请遵医嘱'].join('\n');
}

export function applyUsagePreset(value, { frequency, schedule }) {
  const lines = usageMethodForPrint(value).split('\n');
  lines[0] = normalizeUsageMethod(frequency) || lines[0];
  lines[2] = normalizeUsageMethod(schedule) || lines[2];
  return lines.join('\n');
}
