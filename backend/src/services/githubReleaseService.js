import { createWriteStream } from "node:fs";
import { mkdir, rename, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import { fileURLToPath } from "node:url";

const dataDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../data");
const releaseDir = path.join(dataDir, "releases");
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

export async function syncLatestAndroidRelease({ repository, token = "", apiUrl = "https://api.github.com" }) {
  const repo = String(repository || "").trim();
  if (!repo) throw new Error("GITHUB_REPOSITORY is required");
  const headers = { Accept: "application/vnd.github+json", "User-Agent": "tcm-android-release-sync", ...(token ? { Authorization: `Bearer ${token}` } : {}) };
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
  const tempApk = path.join(releaseDir, `.app-release-${versionCode}.apk.tmp`);
  try {
    await downloadWithTimeout(apkAsset.browser_download_url, { headers }, tempApk, apkTimeoutMs);
    await rename(tempApk, path.join(releaseDir, "app-release.apk"));
    const config = {
      versionCode, versionName,
      minVersionCode: Number.isInteger(Number(metadata.minVersionCode)) ? Number(metadata.minVersionCode) : 1,
      forceUpdate: Boolean(metadata.forceUpdate),
      releaseNotes: Array.isArray(metadata.releaseNotes) ? metadata.releaseNotes.map(String).filter(Boolean) : [],
      publishedAt: String(metadata.publishedAt || release.published_at || "").slice(0, 10),
      apkUrl: "/app/releases/app-release.apk",
      sha256: String(metadata.sha256 || "").toLowerCase(),
      size: Number(metadata.size || 0),
    };
    await writeFile(path.join(dataDir, "app-version.android.json"), `${JSON.stringify(config, null, 2)}\n`, "utf8");
    return { versionCode, versionName, releaseUrl: release.html_url };
  } finally {
    await rm(tempApk, { force: true });
  }
}
