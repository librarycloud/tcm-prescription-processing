import { AppError } from "../utils/appError.js";

export function prescriptionBusinessDate(value = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(value);
  const get = (type) => parts.find((part) => part.type === type)?.value;
  return `${get("year")}-${get("month")}-${get("day")}`;
}

export async function nextPrescriptionNo(
  prisma,
  tx = prisma,
  now = new Date(),
) {
  const dayText = prescriptionBusinessDate(now);
  const day = new Date(`${dayText}T00:00:00.000Z`);
  const row = await tx.prescriptionDailySequence.upsert({
    where: { sequenceDate: day },
    update: { currentValue: { increment: 1 } },
    create: { sequenceDate: day, currentValue: 1 },
  });
  if (row.currentValue > 9999)
    throw new AppError("当日处方流水号已达到上限", 409);
  return (
    "RX" +
    dayText.replaceAll("-", "") +
    String(row.currentValue).padStart(4, "0")
  );
}
