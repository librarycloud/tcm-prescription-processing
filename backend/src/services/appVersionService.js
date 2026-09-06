import { readFile, writeFile, mkdir, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { config } from "../config.js";
import { checkBsdiffAvailable } from "./patchService.js";
import { generatePatchBetweenVersions } from "./githubReleaseService.js";

const dataDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../data");
const legacyVersionFile = path.join(dataDir, "app-version.android.json");
const manifestFile = path.join(dataDir, "app-versions.json");
const releaseDir = path.join(dataDir, "releases");

/**
 * Resolve download URL with optional external high bandwidth base URL
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
      versionCode: Number(legacy.versionCode || 1),
      versionName: String(legacy.versionName || "1.0"),
      minVersionCode: Number(legacy.minVersionCode || 1),
      forceUpdate: Boolean(legacy.forceUpdate),
      releaseNotes: Array.isArray(legacy.releaseNotes) ? legacy.releaseNotes : [],
      publishedAt: String(legacy.publishedAt || new Date().toISOString().slice(0, 10)),
      apkUrl: String(legacy.apkUrl || "/app/releases/app-release.apk"),
      sha256: String(legacy.sha256 || ""),
      size: Number(legacy.size || 0),
      changelogUrl: String(legacy.changelogUrl || legacy.githubUrl || ""),
    },
    history: [
      {
        versionCode: Number(legacy.versionCode || 1),
        versionName: String(legacy.versionName || "1.0"),
        apkUrl: String(legacy.apkUrl || "/app/releases/app-release.apk"),
        sha256: String(legacy.sha256 || ""),
        size: Number(legacy.size || 0),
        publishedAt: String(legacy.publishedAt || new Date().toISOString().slice(0, 10)),
      },
    ],
    patches: [],
  };

  await saveAppVersionsManifest(initialManifest);
  return initialManifest;
}

/**
 * Save app versions manifest and write-through sync legacy file
 */
export async function saveAppVersionsManifest(manifest) {
  await mkdir(dataDir, { recursive: true });
  await writeFile(manifestFile, JSON.stringify(manifest, null, 2), "utf8");

  // Sync latest into legacy file for backwards compatibility
  if (manifest.latest) {
    const legacyData = {
      versionCode: manifest.latest.versionCode,
      versionName: manifest.latest.versionName,
      minVersionCode: manifest.latest.minVersionCode,
      forceUpdate: manifest.latest.forceUpdate,
      releaseNotes: manifest.latest.releaseNotes,
      publishedAt: manifest.latest.publishedAt,
      apkUrl: manifest.latest.apkUrl,
      sha256: manifest.latest.sha256,
      size: manifest.latest.size,
      changelogUrl: manifest.latest.changelogUrl,
    };
    await writeFile(legacyVersionFile, JSON.stringify(legacyData, null, 2), "utf8");
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

  // If client provided a versionCode and there is an update, see if an incremental patch exists or can be generated on-demand
  if (clientCode !== null && hasUpdate) {
    let patch = (manifest.patches || []).find(
      (p) => Number(p.fromVersionCode) === clientCode && Number(p.targetVersionCode) === versionCode
    );

    // On-demand dynamic generation: if no pre-generated patch exists, check if local APKs exist and generate now
    if (!patch) {
      try {
        const oldApkPath = path.join(releaseDir, `app-release-v${clientCode}.apk`);
        const newApkPath = path.join(releaseDir, `app-release-v${versionCode}.apk`);
        const [oldStat, newStat, bsdiffOk] = await Promise.all([
          stat(oldApkPath).catch(() => null),
          stat(newApkPath).catch(() => null),
          checkBsdiffAvailable().catch(() => false),
        ]);

        if (oldStat?.isFile() && newStat?.isFile() && bsdiffOk) {
          // Timeout after 6 seconds to prevent blocking client update check
          const timeoutPromise = new Promise((_, reject) =>
            setTimeout(() => reject(new Error("On-demand patch generation timed out")), 6000)
          );
          const genResult = await Promise.race([
            generatePatchBetweenVersions(clientCode, versionCode),
            timeoutPromise,
          ]);

          if (genResult?.patchRecord) {
            patch = genResult.patchRecord;
          }
        }
      } catch (dynamicErr) {
        console.warn(`[AppVersion] 动态生成增量补丁失败或超时 (v${clientCode} -> v${versionCode}):`, dynamicErr.message);
      }
    }

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
  const latestCode = Number(manifest.latest?.versionCode || 0);

  const historyList = Array.isArray(manifest.history)
    ? manifest.history.map((h) => {
        if (Number(h.versionCode) === latestCode && manifest.latest) {
          return { ...manifest.latest, ...h, size: Number(h.size || manifest.latest.size || 0) };
        }
        return { ...h };
      })
    : [];
  if (manifest.latest && !historyList.some((h) => Number(h.versionCode) === latestCode)) {
    historyList.unshift({ ...manifest.latest });
  }

  // Sort descending by versionCode
  historyList.sort((a, b) => Number(b.versionCode) - Number(a.versionCode));

  const allPatches = Array.isArray(manifest.patches) ? manifest.patches : [];

  // Group by target version
  const versionGroups = historyList.map((ver) => {
    const vCode = Number(ver.versionCode);
    const isLatest = vCode === latestCode;
    const vSize = Number(ver.size || (isLatest ? manifest.latest?.size : 0) || 0);

    // Incoming patches upgrading to this version
    const incomingPatches = allPatches
      .filter((p) => Number(p.targetVersionCode) === vCode)
      .map((p) => {
        const patchSize = Number(p.patchSize || 0);
        const savedBytes = vSize > 0 ? Math.max(0, vSize - patchSize) : 0;
        const savedPercentage = vSize > 0 ? ((savedBytes / vSize) * 100).toFixed(1) : "0.0";
        const fromVer = historyList.find((h) => Number(h.versionCode) === Number(p.fromVersionCode));
        return {
          ...p,
          fromVersionName: fromVer?.versionName || `v${p.fromVersionCode}`,
          resolvedPatchUrl: resolveDownloadUrl(p.patchUrl),
          patchSize,
          savedBytes,
          savedPercentage: Number(savedPercentage),
        };
      })
      .sort((a, b) => Number(b.fromVersionCode) - Number(a.fromVersionCode));

    const eligibleOlderVersions = historyList.filter((h) => Number(h.versionCode) < vCode);
    const coveredFromCodes = new Set(incomingPatches.map((p) => Number(p.fromVersionCode)));
    const missingOlderVersions = eligibleOlderVersions.filter((h) => !coveredFromCodes.has(Number(h.versionCode)));

    return {
      versionCode: vCode,
      versionName: ver.versionName || `v${vCode}`,
      isLatest,
      size: vSize,
      apkUrl: resolveDownloadUrl(ver.apkUrl),
      sha256: ver.sha256 || "",
      publishedAt: ver.publishedAt || "",
      releaseNotes: ver.releaseNotes || [],
      patches: incomingPatches,
      eligibleCount: eligibleOlderVersions.length,
      coveredCount: incomingPatches.length,
      missingCount: missingOlderVersions.length,
      missingVersions: missingOlderVersions.map((h) => ({
        versionCode: Number(h.versionCode),
        versionName: h.versionName || `v${h.versionCode}`,
      })),
    };
  });

  // Flat patches list for backwards compatibility
  const patchesWithStats = allPatches.map((p) => {
    const targetVer = historyList.find((h) => Number(h.versionCode) === Number(p.targetVersionCode));
    const targetSize = targetVer ? Number(targetVer.size || 0) : Number(manifest.latest?.size || 0);
    const patchSize = Number(p.patchSize || 0);
    const savedBytes = targetSize > 0 ? Math.max(0, targetSize - patchSize) : 0;
    const savedPercentage = targetSize > 0 ? ((savedBytes / targetSize) * 100).toFixed(1) : "0.0";
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
    history: historyList,
    patches: patchesWithStats,
    versionGroups,
    bsdiffAvailable,
    appDownloadBaseUrl: config.appDownloadBaseUrl || "",
  };
}
