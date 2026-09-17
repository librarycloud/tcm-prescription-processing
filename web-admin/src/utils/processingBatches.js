function pad(value) {
  return String(value).padStart(2, '0');
}

export function datePlusDays(dateText, days) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(dateText || ''));
  const date = match
    ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
    : new Date();
  date.setDate(date.getDate() + Number(days || 0));
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function splitDoseBatches(totalDose, batchCount, firstDate) {
  const total = Math.max(1, Number(totalDose) || 1);
  const count = Math.max(1, Math.min(Number(batchCount) || 1, total));
  const baseDose = Math.floor(total / count);
  const remainder = total % count;
  let processDate = firstDate || new Date();
  return Array.from({ length: count }, (_, index) => {
    const dose = baseDose + (index < remainder ? 1 : 0);
    const date = processDate instanceof Date
      ? `${processDate.getFullYear()}-${pad(processDate.getMonth() + 1)}-${pad(processDate.getDate())}`
      : String(processDate);
    processDate = datePlusDays(date, dose);
    return { totalDose: dose, processDate: date };
  });
}
