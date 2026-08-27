import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { getAndroidAppVersion } from "../services/appVersionService.js";
import { syncLatestAndroidRelease } from "../services/githubReleaseService.js";
import { config } from "../config.js";
import { ok } from "../utils/response.js";

const releaseDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../data/releases");

export async function androidVersionController(_request, reply) {
  return ok(reply, await getAndroidAppVersion());
}

export async function syncAndroidVersionController(_request, reply) {
  const result = await syncLatestAndroidRelease({
    repository: config.githubRepository,
    token: config.githubToken,
  });
  return ok(reply, result, "Android版本已同步");
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
    .header("Content-Disposition", `attachment; filename=\"${filename}\"`);
  return reply.send(createReadStream(filePath));
}
