import { randomUUID } from "node:crypto";
import { mkdir, readFile, unlink, writeFile } from "node:fs/promises";
import path from "node:path";
import { config } from "../config.js";
import { AppError } from "../utils/appError.js";

const MIME_EXTENSIONS = {
  "application/pdf": "pdf",
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/gif": "gif",
  "image/webp": "webp",
  "image/bmp": "bmp",
};

function resolveUploadPath(storagePath) {
  const root = path.resolve(config.uploadDir);
  const filePath = path.resolve(root, String(storagePath || ""));
  if (!filePath.startsWith(`${root}${path.sep}`))
    throw new AppError("文件存储路径无效", 500);
  return filePath;
}

export async function saveUploadFile(buffer, { category, mimeType }) {
  const extension = MIME_EXTENSIONS[mimeType];
  if (!extension) throw new AppError("文件格式不支持本地存储", 400);
  if (!/^[a-z][a-z0-9-]*$/.test(category))
    throw new AppError("文件存储分类无效", 500);

  const now = new Date();
  const directory = path.join(
    config.uploadDir,
    category,
    String(now.getFullYear()),
    String(now.getMonth() + 1).padStart(2, "0"),
  );
  const filePath = path.join(directory, `${randomUUID()}.${extension}`);
  await mkdir(directory, { recursive: true });
  await writeFile(filePath, buffer, { flag: "wx" });
  return path.relative(config.uploadDir, filePath).split(path.sep).join("/");
}

export function readUploadFile(storagePath) {
  return readFile(resolveUploadPath(storagePath));
}

export async function removeUploadFile(storagePath) {
  if (!storagePath) return;
  try {
    await unlink(resolveUploadPath(storagePath));
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }
}
