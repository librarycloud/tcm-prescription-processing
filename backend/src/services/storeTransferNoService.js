import { AppError } from "../utils/appError.js";
import { prescriptionBusinessDate } from "./prescriptionNoService.js";

export async function nextStoreTransferNo(tx, now = new Date()) {
  const dayText = prescriptionBusinessDate(now);
  const day = new Date(`${dayText}T00:00:00.000Z`);
  const row = await tx.storeTransferDailySequence.upsert({
    where: { sequenceDate: day },
    update: { currentValue: { increment: 1 } },
    create: { sequenceDate: day, currentValue: 1 },
  });
  if (row.currentValue > 9999)
    throw new AppError("当日调拨流水号已达到上限", 409);
  return `TR${dayText.replaceAll("-", "")}${String(row.currentValue).padStart(4, "0")}`;
}
