import crypto from "node:crypto";
import { config } from "../config.js";

export const PICKUP_QR_PREFIX = "TCM:PICKUP:1:";

function signatureFor(payload) {
  return crypto
    .createHmac("sha256", config.pickupQrSecret)
    .update(payload, "utf8")
    .digest("base64url");
}

export function pickupQrContent(pkg) {
  const id = Number(pkg?.id);
  const code = String(pkg?.pickupCode || "").replace(/\D/g, "");
  if (!Number.isInteger(id) || id <= 0 || !/^\d{6}$/.test(code)) return "";
  const payload = `${id}:${code}`;
  return `${PICKUP_QR_PREFIX}${payload}:${signatureFor(payload)}`;
}

export function parsePickupQrContent(value) {
  const text = String(value || "").trim();
  if (!text.startsWith(PICKUP_QR_PREFIX)) return null;
  const body = text.slice(PICKUP_QR_PREFIX.length);
  const match = body.match(/^(\d+):(\d{6}):([A-Za-z0-9_-]+)$/);
  if (!match) return null;
  const payload = `${match[1]}:${match[2]}`;
  const expected = signatureFor(payload);
  const actualBuffer = Buffer.from(match[3], "base64url");
  const expectedBuffer = Buffer.from(expected, "base64url");
  if (
    actualBuffer.length !== expectedBuffer.length ||
    !crypto.timingSafeEqual(actualBuffer, expectedBuffer)
  ) return null;
  return { packageId: Number(match[1]), pickupCode: match[2] };
}

export function withPickupQrContent(pkg) {
  return pkg ? { ...pkg, pickupQrContent: pickupQrContent(pkg) } : pkg;
}
