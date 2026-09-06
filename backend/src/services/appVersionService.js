import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { config } from "../config.js";
import { checkBsdiffAvailable } from "./patchService.js";

const dataDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../data");
const legacyVersionFile = path.join(dataDir, "app-version.android.json");
const manifestFile = path.join(dataDir, "app-versions.json");

/**
 * Resolve download URL with optional external high-bandwidth base URL
 */
export function resolveDownloadUrl(relativeUrl) {
  if (!relativeUrl) return "";
  if (/^https?:\/\//i.test(relativeUrl)) return relativeUrl;
  const base = config.appDownloadBaseUrl;
  const cleanPath = relativeUrl.startsWith("/") ? relativeUrl : `/${relativeUrl}`;
  if (base) {
    return `${base}${cleanPath}`;
  }
  return cleanPath;
}

/**
 * Load app version manifest, with automatic migration from legacy json file
 */
export async function loadAppVersionsManifest() {
  await mkdir(dataDir, { recursive: true });
  try {
    const raw = await readFile(manifestFile, "utf8");
    const parsed = JSON.parse(raw);
    if (parsed && parsed.latest && Number.isInteger(parsed.latest.versionCode)) {
      return parsed;
    }
  } catch {
    // If manifest doesn't exist yet, fall through to legacy file
  }

  // Fallback to legacy file
  let legacy = null;
  try {
    const legacyRaw = await readFile(legacyVersionFile, "utf8");
    legacy = JSON.parse(legacyRaw);
  } catch {
    legacy = {
      versionCode: 1,
      versionName: "1.0",
      minVersionCode: 1,
      forceUpdate: false,
      releaseNotes: [],
      publishedAt: new Date().toISOString().slice(0, 10),
      apkUrl: "/app/releases/app-release.apk",
      sha256: "",
      size: 0,
    };
  }

  const initialManifest = {
    latest: {
      versionCode: Number(legacy.versionCode) || 1,
      versionName: String(legacy.versionName || "1.0"),
      minVersionCode: Number(legacy.minVersionCode) || 1,
      forceUpdate: Boolean(legacy.forceUpdate),
      releaseNotes: Array.isArray(legacy.releaseNotes) ? legacy.releaseNotes : [],
      publishedAt: String(legacy.publishedAt || ""),
      apkUrl: String(legacy.apkUrl || "/app/releases/app-release.apk"),
      sha256: String(legacy.sha256 || "").toLowerCase(),
      size: Number(legacy.size || 0),
    },
    history: [
      {
        versionCode: Number(legacy.versionCode) || 1,
        versionName: String(legacy.versionName || "1.0"),
        apkUrl: String(legacy.apkUrl || "/app/releases/app-release.apk"),
        sha256: String(legacy.sha256 || "").toLowerCase(),
        size: Number(legacy.size || 0),
        publishedAt: String(legacy.publishedAt || ""),
      },
    ],
    patches: [],
  };

  await saveAppVersionsManifest(initialManifest);
  return initialManifest;
}

/**
 * Save manifest to disk and sync legacy version file for compatibility
 */
export async function saveAppVersionsManifest(manifest) {
  await mkdir(dataDir, { recursive: true });
  await writeFile(manifestFile, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  if (manifest.latest) {
    await writeFile(legacyVersionFile, `${JSON.stringify(manifest.latest, null, 2)}\n`, "utf8");
  }
}

/**
 * Get Android app version, supporting incremental differential updates
 */
export async function getAndroidAppVersion({ currentVersionCode } = {}) {
  const manifest = await loadAppVersionsManifest();
  const latest = manifest.latest;
  const versionCode = Number(latest.versionCode);
  const versionName = String(latest.versionName || "").trim();
  const apkUrl = String(latest.apkUrl || "").trim();

  if (!Number.isInteger(versionCode) || versionCode < 1 || !versionName || !apkUrl) {
    throw new Error("Android版本配置不完整");
  }

  const clientCode = Number.isInteger(Number(currentVersionCode)) ? Number(currentVersionCode) : null;
  const hasUpdate = clientCode !== null ? versionCode > clientCode : true;
  const minVersionCode = Number.isInteger(Number(latest.minVersionCode)) ? Number(latest.minVersionCode) : 1;
  const forceUpdate = Boolean(latest.forceUpdate) || (clientCode !== null && clientCode < minVersionCode);

  const changelogUrl = String(latest.changelogUrl || "").trim() ||
    (latest.githubUrl ? String(latest.githubUrl).trim() : "");

  const baseResponse = {
    versionCode,
    versionName,
    minVersionCode,
    forceUpdate,
    releaseNotes: Array.isArray(latest.releaseNotes) ? latest.releaseNotes.map((item) => String(item)).filter(Boolean) : [],
    changelogUrl,
    publishedAt: String(latest.publishedAt || "").trim(),
    downloadBaseUrl: config.appDownloadBaseUrl || "",
    hasUpdate,
  };

  // If client provided a versionCode and there is an update, see if an incremental patch exists
  if (clientCode !== null && hasUpdate) {
    const patch = (manifest.patches || []).find(
      (p) => Number(p.fromVersionCode) === clientCode && Number(p.targetVersionCode) === versionCode
    );

    if (patch) {
      return {
        ...baseResponse,
        updateType: "incremental",
        patchUrl: resolveDownloadUrl(patch.patchUrl),
        patchSha256: String(patch.patchSha256 || "").toLowerCase(),
        patchSize: Number(patch.patchSize || 0),
        fromVersionCode: patch.fromVersionCode,
        targetApkSha256: String(latest.sha256 || "").toLowerCase(),
        fallbackApkUrl: resolveDownloadUrl(latest.apkUrl),
        fallbackApkSize: Number(latest.size || 0),
      };
    }
  }

  return {
    ...baseResponse,
    updateType: "full",
    apkUrl: resolveDownloadUrl(latest.apkUrl),
    sha256: String(latest.sha256 || "").toLowerCase(),
    size: Number.isFinite(Number(latest.size)) ? Number(latest.size) : 0,
  };
}

/**
 * Get patch status and history matrix for administration UI
 */
export async function getAppPatchesMatrix() {
  const manifest = await loadAppVersionsManifest();
  const bsdiffAvailable = await checkBsdiffAvailable();
  const latestSize = Number(manifest.latest.size || 0);

  const patchesWithStats = (manifest.patches || []).map((p) => {
    const patchSize = Number(p.patchSize || 0);
    const savedBytes = latestSize > 0 ? Math.max(0, latestSize - patchSize) : 0;
    const savedPercentage = latestSize > 0 ? ((savedBytes / latestSize) * 100).toFixed(1) : "0.0";
    return {
      ...p,
      resolvedPatchUrl: resolveDownloadUrl(p.patchUrl),
      patchSize,
      savedBytes,
      savedPercentage: Number(savedPercentage),
    };
  });

  return {
    latest: manifest.latest,
    history: manifest.history || [],
    patches: patchesWithStats,
    bsdiffAvailable,
    appDownloadBaseUrl: config.appDownloadBaseUrl || "",
  };
}
