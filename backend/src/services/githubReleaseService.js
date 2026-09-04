import { createWriteStream } from "node:fs";
import { copyFile, mkdir, rename, rm, stat } from "node:fs/promises";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import { fileURLToPath } from "node:url";
import {
  loadAppVersionsManifest,
  saveAppVersionsManifest,
} from "./appVersionService.js";
import {
  checkBsdiffAvailable,
  computeFileSha256,
  generatePatch,
} from "./patchService.js";

const dataDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../data");
const releaseDir = path.join(dataDir, "releases");
const patchDir = path.join(dataDir, "patches");
const metadataTimeoutMs = 30_000;
const apkTimeoutMs = 180_000;

async function fetchWithTimeout(url, options, timeoutMs) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new Error(`GitHub request timed out after ${Math.ceil(timeoutMs / 1000)} seconds`);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function downloadWithTimeout(url, options, destination, timeoutMs) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    if (!response.ok || !response.body) throw new Error(`APK download failed: ${response.status}`);
    await pipeline(response.body, createWriteStream(destination));
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new Error(`APK download timed out after ${Math.ceil(timeoutMs / 1000)} seconds`);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * Manually generate a patch between two local versioned APKs
 */
export async function generatePatchBetweenVersions(fromVersionCode, targetVersionCode) {
  const manifest = await loadAppVersionsManifest();
  const fromCode = Number(fromVersionCode);
  const targetCode = Number(targetVersionCode);

  const fromEntry = manifest.history?.find((h) => Number(h.versionCode) === fromCode);
  const targetEntry = manifest.history?.find((h) => Number(h.versionCode) === targetCode) ||
    (Number(manifest.latest?.versionCode) === targetCode ? manifest.latest : null);

  if (!fromEntry || !targetEntry) {
    throw new Error(`未找到指定版本号对应的历史记录 (from: ${fromCode}, target: ${targetCode})`);
  }

  const oldApk = path.join(releaseDir, `app-release-v${fromCode}.apk`);
  const newApk = path.join(releaseDir, `app-release-v${targetCode}.apk`);

  try {
    await stat(oldApk);
  } catch {
    throw new Error(`旧版本 APK 文件未在服务器找到: app-release-v${fromCode}.apk`);
  }
  try {
    await stat(newApk);
  } catch {
    throw new Error(`目标版本 APK 文件未在服务器找到: app-release-v${targetCode}.apk`);
  }

  await mkdir(patchDir, { recursive: true });
  const patchFileName = `patch-v${fromCode}-to-v${targetCode}.patch`;
  const patchFilePath = path.join(patchDir, patchFileName);

  const { size, sha256 } = await generatePatch(oldApk, newApk, patchFilePath);

  // Update manifest patches
  const patches = (manifest.patches || []).filter(
    (p) => !(Number(p.fromVersionCode) === fromCode && Number(p.targetVersionCode) === targetCode)
  );

  patches.push({
    targetVersionCode: targetCode,
    fromVersionCode: fromCode,
    patchFile: patchFileName,
    patchUrl: `/app/patches/${patchFileName}`,
    patchSha256: sha256,
    patchSize: size,
    createdAt: new Date().toISOString(),
  });

  manifest.patches = patches;
  await saveAppVersionsManifest(manifest);

  return {
    fromVersionCode: fromCode,
    targetVersionCode: targetCode,
    patchFileName,
    patchSize: size,
    patchSha256: sha256,
  };
}

/**
 * Sync latest release from GitHub and auto-generate diff patches
 */
export async function syncLatestAndroidRelease({ repository, token = "", apiUrl = "https://api.github.com" }) {
  const repo = String(repository || "").trim();
  if (!repo) throw new Error("GITHUB_REPOSITORY is required");
  const headers = {
    Accept: "application/vnd.github+json",
    "User-Agent": "tcm-android-release-sync",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
  const base = String(apiUrl || "https://api.github.com").replace(/\/$/, "");
  const releaseResponse = await fetchWithTimeout(`${base}/repos/${repo}/releases/latest`, { headers }, metadataTimeoutMs);
  if (!releaseResponse.ok) throw new Error(`GitHub request failed: ${releaseResponse.status}`);
  const release = await releaseResponse.json();
  const assets = new Map((release.assets || []).map((asset) => [asset.name, asset]));
  const metadataAsset = assets.get("app-version.android.json");
  const apkAsset = assets.get("app-release.apk");
  if (!metadataAsset || !apkAsset) throw new Error("Latest release is missing Android assets");

  const metadataResponse = await fetchWithTimeout(metadataAsset.browser_download_url, { headers }, metadataTimeoutMs);
  if (!metadataResponse.ok) throw new Error(`Metadata download failed: ${metadataResponse.status}`);
  const metadata = await metadataResponse.json();
  const versionCode = Number(metadata.versionCode);
  const versionName = String(metadata.versionName || "").trim();
  if (!Number.isInteger(versionCode) || versionCode < 1 || !versionName) throw new Error("Invalid release metadata");

  await mkdir(releaseDir, { recursive: true });
  await mkdir(patchDir, { recursive: true });

  const tempApk = path.join(releaseDir, `.app-release-${versionCode}.apk.tmp`);
  const versionedApkName = `app-release-v${versionCode}.apk`;
  const versionedApkPath = path.join(releaseDir, versionedApkName);
  const legacyApkPath = path.join(releaseDir, "app-release.apk");

  try {
    await downloadWithTimeout(apkAsset.browser_download_url, { headers }, tempApk, apkTimeoutMs);
    await rename(tempApk, versionedApkPath);
    // Maintain default app-release.apk copy for backward compatibility
    await copyFile(versionedApkPath, legacyApkPath);

    const actualSha256 = await computeFileSha256(versionedApkPath);
    const actualStat = await stat(versionedApkPath);

    const manifest = await loadAppVersionsManifest();

    const newVersionConfig = {
      versionCode,
      versionName,
      minVersionCode: Number.isInteger(Number(metadata.minVersionCode)) ? Number(metadata.minVersionCode) : 1,
      forceUpdate: Boolean(metadata.forceUpdate),
      releaseNotes: Array.isArray(metadata.releaseNotes) ? metadata.releaseNotes.map(String).filter(Boolean) : [],
      publishedAt: String(metadata.publishedAt || release.published_at || "").slice(0, 10),
      apkUrl: `/app/releases/${versionedApkName}`,
      githubUrl: apkAsset.browser_download_url,
      sha256: actualSha256 || String(metadata.sha256 || "").toLowerCase(),
      size: actualStat.size || Number(metadata.size || 0),
    };

    // Update history: prepend, ensuring no duplicate versionCode
    const history = (manifest.history || []).filter((h) => Number(h.versionCode) !== versionCode);
    history.unshift({
      versionCode,
      versionName,
      apkUrl: `/app/releases/${versionedApkName}`,
      sha256: newVersionConfig.sha256,
      size: newVersionConfig.size,
      publishedAt: newVersionConfig.publishedAt,
    });

    manifest.latest = newVersionConfig;
    manifest.history = history;

    // Check if bsdiff is available and generate patches for historical versions
    const bsdiffAvailable = await checkBsdiffAvailable();
    const patchesGenerated = [];

    if (bsdiffAvailable) {
      // Find up to 3 previous versions
      const previousVersions = history.filter((h) => Number(h.versionCode) < versionCode).slice(0, 3);
      for (const prev of previousVersions) {
        const oldApkPath = path.join(releaseDir, `app-release-v${prev.versionCode}.apk`);
        try {
          await stat(oldApkPath);
          const patchFileName = `patch-v${prev.versionCode}-to-v${versionCode}.patch`;
          const patchOutputPath = path.join(patchDir, patchFileName);
          const { size, sha256 } = await generatePatch(oldApkPath, versionedApkPath, patchOutputPath);

          // Update patch in manifest
          manifest.patches = (manifest.patches || []).filter(
            (p) => !(Number(p.fromVersionCode) === prev.versionCode && Number(p.targetVersionCode) === versionCode)
          );
          manifest.patches.push({
            targetVersionCode: versionCode,
            fromVersionCode: prev.versionCode,
            patchFile: patchFileName,
            patchUrl: `/app/patches/${patchFileName}`,
            patchSha256: sha256,
            patchSize: size,
            createdAt: new Date().toISOString(),
          });
          patchesGenerated.push({ fromVersionCode: prev.versionCode, size, patchFileName });
        } catch (patchErr) {
          // Log but continue with other patches
          console.warn(`差分补丁生成失败 (v${prev.versionCode} -> v${versionCode}):`, patchErr.message);
        }
      }
    }

    await saveAppVersionsManifest(manifest);

    return {
      versionCode,
      versionName,
      releaseUrl: release.html_url,
      patchesGenerated,
      bsdiffAvailable,
    };
  } finally {
    await rm(tempApk, { force: true });
  }
}
