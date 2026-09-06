import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { getAndroidAppVersion, getAppPatchesMatrix } from "../services/appVersionService.js";
import {
  generateAllMissingPatchesForVersion,
  generatePatchBetweenVersions,
  syncLatestAndroidRelease,
} from "../services/githubReleaseService.js";
import { config } from "../config.js";
import { ok } from "../utils/response.js";

const dataDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../data");
const releaseDir = path.join(dataDir, "releases");
const patchDir = path.join(dataDir, "patches");

export async function androidVersionController(request, reply) {
  reply.header("Cache-Control", "no-store, no-cache, must-revalidate").header("Pragma", "no-cache");
  const currentVersionCode = request.query.versionCode || request.query.currentVersionCode;
  return ok(reply, await getAndroidAppVersion({ currentVersionCode }));
}

export async function syncAndroidVersionController(_request, reply) {
  const result = await syncLatestAndroidRelease({
    repository: config.githubRepository,
    token: config.githubToken,
  });
  return ok(reply, result, "Android版本已同步");
}

export async function appPatchesController(_request, reply) {
  return ok(reply, await getAppPatchesMatrix());
}

export async function generatePatchController(request, reply) {
  const { fromVersionCode, targetVersionCode } = request.body || {};
  if (!fromVersionCode || !targetVersionCode) {
    return reply.code(400).send({ code: 400, message: "fromVersionCode 和 targetVersionCode 必填" });
  }
  const result = await generatePatchBetweenVersions(fromVersionCode, targetVersionCode);
  return ok(reply, result, "差分补丁已生成");
}

export async function generateAllPatchesController(request, reply) {
  const { targetVersionCode } = request.body || {};
  if (!targetVersionCode) {
    return reply.code(400).send({ code: 400, message: "targetVersionCode 必填" });
  }
  const result = await generateAllMissingPatchesForVersion(targetVersionCode);
  return ok(reply, result, `已生成 ${result.generatedCount} 个增量补丁`);
}

export async function androidReleaseController(request, reply) {
  const filename = String(request.params.filename || "");
  if (!/^[A-Za-z0-9._-]+\.apk$/.test(filename)) {
    return reply.code(404).send({ code: 404, message: "文件不存在" });
  }
  const filePath = path.join(releaseDir, filename);
  try {
    const file = await stat(filePath);
    if (!file.isFile()) return reply.code(404).send({ code: 404, message: "文件不存在" });
  } catch {
    return reply.code(404).send({ code: 404, message: "文件不存在" });
  }
  reply
    .type("application/vnd.android.package-archive")
    .header("Cache-Control", "no-store, no-cache, must-revalidate")
    .header("Pragma", "no-cache")
    .header("Content-Disposition", `attachment; filename=\"${filename}\"`);
  return reply.send(createReadStream(filePath));
}

export async function androidPatchController(request, reply) {
  const filename = String(request.params.filename || "");
  if (!/^[A-Za-z0-9._-]+\.patch$/.test(filename)) {
    return reply.code(404).send({ code: 404, message: "补丁文件不存在" });
  }
  const filePath = path.join(patchDir, filename);
  try {
    const file = await stat(filePath);
    if (!file.isFile()) return reply.code(404).send({ code: 404, message: "补丁文件不存在" });
  } catch {
    return reply.code(404).send({ code: 404, message: "补丁文件不存在" });
  }
  reply
    .type("application/octet-stream")
    .header("Cache-Control", "no-store, no-cache, must-revalidate")
    .header("Pragma", "no-cache")
    .header("Content-Disposition", `attachment; filename=\"${filename}\"`);
  return reply.send(createReadStream(filePath));
}
