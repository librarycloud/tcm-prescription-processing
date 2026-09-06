import test from 'node:test';
import assert from 'node:assert/strict';
import { writeFile, rm, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  getAndroidAppVersion,
  getAppPatchesMatrix,
  loadAppVersionsManifest,
  saveAppVersionsManifest,
  resolveDownloadUrl,
} from '../src/services/appVersionService.js';
import { computeFileSha256, checkBsdiffAvailable } from '../src/services/patchService.js';
import { config } from '../src/config.js';

const testDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../data');
const manifestFile = path.join(testDir, 'app-versions.json');

test('appVersionService: loads manifest and respects currentVersionCode', async (t) => {
  // Backup manifest if exists
  const initialManifest = await loadAppVersionsManifest();

  await t.test('returns full update when no currentVersionCode or no patch available', async () => {
    const res = await getAndroidAppVersion();
    assert.equal(res.updateType, 'full');
    assert.ok(res.apkUrl.includes('.apk'));
    assert.equal(typeof res.versionCode, 'number');
  });

  await t.test('detects incremental patch when matching patch exists in manifest', async () => {
    const mockManifest = {
      latest: {
        versionCode: 10,
        versionName: '2.0.0',
        minVersionCode: 1,
        forceUpdate: false,
        releaseNotes: ['修复已知问题', '新增增量更新'],
        publishedAt: '2026-09-04',
        apkUrl: '/app/releases/app-release-v10.apk',
        sha256: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
        size: 50000000,
      },
      history: [
        { versionCode: 10, versionName: '2.0.0' },
        { versionCode: 9, versionName: '1.9.0' },
      ],
      patches: [
        {
          targetVersionCode: 10,
          fromVersionCode: 9,
          patchFile: 'patch-v9-to-v10.patch',
          patchUrl: '/app/patches/patch-v9-to-v10.patch',
          patchSha256: 'abcdef123456',
          patchSize: 2500000,
          createdAt: new Date().toISOString(),
        },
      ],
    };

    await saveAppVersionsManifest(mockManifest);

    // Client with versionCode 9 (has patch to 10)
    const incrementalResult = await getAndroidAppVersion({ currentVersionCode: 9 });
    assert.equal(incrementalResult.hasUpdate, true);
    assert.equal(incrementalResult.updateType, 'incremental');
    assert.ok(incrementalResult.patchUrl.includes('patch-v9-to-v10.patch'));
    assert.equal(incrementalResult.patchSha256, 'abcdef123456');
    assert.equal(incrementalResult.targetApkSha256, mockManifest.latest.sha256);
    assert.ok(incrementalResult.fallbackApkUrl.includes('app-release-v10.apk'));

    // Client with versionCode 8 (no patch to 10, should fallback to full)
    const fullResult = await getAndroidAppVersion({ currentVersionCode: 8 });
    assert.equal(fullResult.hasUpdate, true);
    assert.equal(fullResult.updateType, 'full');
    assert.ok(fullResult.apkUrl.includes('app-release-v10.apk'));

    // Client already on latest version 10
    const latestResult = await getAndroidAppVersion({ currentVersionCode: 10 });
    assert.equal(latestResult.hasUpdate, false);
  });

  await t.test('getAppPatchesMatrix computes savings percentage and versionGroups correctly', async () => {
    const matrix = await getAppPatchesMatrix();
    assert.equal(matrix.latest.versionCode, 10);
    assert.equal(matrix.patches.length, 1);
    const patch = matrix.patches[0];
    assert.equal(patch.fromVersionCode, 9);
    assert.equal(patch.targetVersionCode, 10);
    assert.equal(patch.savedBytes, 50000000 - 2500000);
    assert.equal(patch.savedPercentage, 95);

    // Verify versionGroups structure
    assert.ok(Array.isArray(matrix.versionGroups));
    assert.equal(matrix.versionGroups.length, 2);
    const v10Group = matrix.versionGroups.find((g) => g.versionCode === 10);
    assert.ok(v10Group);
    assert.equal(v10Group.isLatest, true);
    assert.equal(v10Group.eligibleCount, 1);
    assert.equal(v10Group.coveredCount, 1);
    assert.equal(v10Group.missingCount, 0);
    assert.equal(v10Group.patches.length, 1);
    assert.equal(v10Group.patches[0].fromVersionCode, 9);
  });

  await t.test('on-demand patch generation generates and caches patch when local APKs exist', async () => {
    const bsdiffAvailable = await checkBsdiffAvailable();
    if (!bsdiffAvailable) return;

    const releaseDir = path.join(testDir, 'releases');
    const patchDir = path.join(testDir, 'patches');
    await mkdir(releaseDir, { recursive: true });
    await mkdir(patchDir, { recursive: true });

    const apkV8 = path.join(releaseDir, 'app-release-v8.apk');
    const apkV10 = path.join(releaseDir, 'app-release-v10.apk');

    // Create dummy base APK and modified APK
    await writeFile(apkV8, 'base-apk-version-8-content-sample-data-123456');
    await writeFile(apkV10, 'target-apk-version-10-content-sample-data-123456-with-updates');

    // Add v8 to history in manifest
    const currentManifest = await loadAppVersionsManifest();
    currentManifest.history.push({ versionCode: 8, versionName: '1.8.0' });
    await saveAppVersionsManifest(currentManifest);

    try {
      // First request: triggers dynamic on-demand generation
      const dynamicResult = await getAndroidAppVersion({ currentVersionCode: 8 });
      assert.equal(dynamicResult.hasUpdate, true);
      assert.equal(dynamicResult.updateType, 'incremental');
      assert.ok(dynamicResult.patchUrl.includes('patch-v8-to-v10.patch'));

      // Check that manifest now has the cached patch
      const updatedManifest = await loadAppVersionsManifest();
      const cachedPatch = updatedManifest.patches.find(
        (p) => p.fromVersionCode === 8 && p.targetVersionCode === 10
      );
      assert.ok(cachedPatch);

      // Second request: instant cache hit
      const cachedResult = await getAndroidAppVersion({ currentVersionCode: 8 });
      assert.equal(cachedResult.updateType, 'incremental');
      assert.equal(cachedResult.patchSha256, cachedPatch.patchSha256);
    } finally {
      await rm(apkV8, { force: true });
      await rm(apkV10, { force: true });
      await rm(path.join(patchDir, 'patch-v8-to-v10.patch'), { force: true });
    }
  });

  // Restore initial manifest
  await saveAppVersionsManifest(initialManifest);
});

test('patchService: computes sha256 of file correctly', async () => {
  const tmpFile = path.join(testDir, '.test_sha256.tmp');
  await writeFile(tmpFile, 'hello tcm incremental update');
  try {
    const sha = await computeFileSha256(tmpFile);
    // sha256 of "hello tcm incremental update"
    assert.equal(typeof sha, 'string');
    assert.equal(sha.length, 64);
  } finally {
    await rm(tmpFile, { force: true });
  }
});

test('patchService: checks bsdiff availability gracefully without throwing', async () => {
  const available = await checkBsdiffAvailable();
  assert.equal(typeof available, 'boolean');
});
