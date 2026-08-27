import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const dataDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../data");
const versionFile = path.join(dataDir, "app-version.android.json");

export async function getAndroidAppVersion() {
  const raw = await readFile(versionFile, "utf8");
  const value = JSON.parse(raw);
  const versionCode = Number(value.versionCode);
  const versionName = String(value.versionName || "").trim();
  const apkUrl = String(value.apkUrl || "").trim();
  if (!Number.isInteger(versionCode) || versionCode < 1 || !versionName || !apkUrl) {
    throw new Error("Android版本配置不完整");
  }
  return {
    versionCode,
    versionName,
    minVersionCode: Number.isInteger(Number(value.minVersionCode)) ? Number(value.minVersionCode) : 1,
    forceUpdate: Boolean(value.forceUpdate),
    releaseNotes: Array.isArray(value.releaseNotes) ? value.releaseNotes.map((item) => String(item)).filter(Boolean) : [],
    publishedAt: String(value.publishedAt || "").trim(),
    apkUrl,
    sha256: String(value.sha256 || "").trim().toLowerCase(),
    size: Number.isFinite(Number(value.size)) ? Number(value.size) : 0,
  };
}
