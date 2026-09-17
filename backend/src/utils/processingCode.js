import crypto from "node:crypto";

function datePart(value = new Date()) {
  const year = String(value.getFullYear()).slice(-2);
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

export function processingPlanQrContent(scanToken) {
  return scanToken ? `TCM:PLAN:1:${scanToken}` : "";
}

export function processingEquipmentQrContent(scanToken) {
  return scanToken ? `TCM:EQUIPMENT:1:${scanToken}` : "";
}

export function scanValue(value, prefix) {
  const text = String(value || "").trim();
  const marker = `TCM:${prefix}:1:`;
  return text.startsWith(marker) ? text.slice(marker.length) : text;
}

export async function generateProcessingPlanIdentity(prisma) {
  void prisma;
  return {
    planCode: `JG${datePart()}-${crypto.randomBytes(3).toString("hex").toUpperCase()}`,
    scanToken: crypto.randomBytes(16).toString("hex"),
  };
}

export function newEquipmentScanToken() {
  return crypto.randomBytes(16).toString("hex");
}
