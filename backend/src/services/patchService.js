import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { stat, mkdir } from 'node:fs/promises';
import path from 'node:path';

const execFileAsync = promisify(execFile);

/**
 * Check if the bsdiff CLI is installed and available in PATH.
 * @returns {Promise<boolean>}
 */
export async function checkBsdiffAvailable() {
  try {
    await execFileAsync('which', ['bsdiff']);
    return true;
  } catch {
    return false;
  }
}

/**
 * Compute the SHA-256 hash of a file.
 * @param {string} filePath
 * @returns {Promise<string>}
 */
export async function computeFileSha256(filePath) {
  return new Promise((resolve, reject) => {
    const hash = createHash('sha256');
    const stream = createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('end', () => resolve(hash.digest('hex').toLowerCase()));
    stream.on('error', reject);
  });
}

/**
 * Generate a binary patch using bsdiff from oldApk to newApk.
 * @param {string} oldApkPath
 * @param {string} newApkPath
 * @param {string} patchOutputPath
 * @returns {Promise<{ size: number, sha256: string }>}
 */
export async function generatePatch(oldApkPath, newApkPath, patchOutputPath) {
  const isAvailable = await checkBsdiffAvailable();
  if (!isAvailable) {
    throw new Error('系统未安装 bsdiff 工具，请在服务器执行 apt-get install -y bsdiff (Linux) 或 brew install bsdiff (macOS)');
  }

  await mkdir(path.dirname(patchOutputPath), { recursive: true });

  await execFileAsync('bsdiff', [oldApkPath, newApkPath, patchOutputPath], {
    maxBuffer: 10 * 1024 * 1024,
  });

  const fileStat = await stat(patchOutputPath);
  const sha256 = await computeFileSha256(patchOutputPath);

  return {
    size: fileStat.size,
    sha256,
  };
}
