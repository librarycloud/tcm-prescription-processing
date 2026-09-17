import assert from "node:assert/strict";
import {
  access,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { config } from "../src/config.js";
import {
  deletePrescriptionAttachment,
  getPrescriptionAttachment,
  uploadPrescriptionAttachment,
} from "../src/services/prescriptionService.js";

const actor = { id: 12, role: 2, storeId: 3, phone: "13800000000" };

function prescription() {
  return {
    id: 21,
    storeId: 3,
    prescriptionNo: "RX001",
    customerName: "测试顾客",
    plans: [],
  };
}

test("replacing a prescription attachment stores the new file and removes the old one", async (t) => {
  const previousUploadDir = config.uploadDir;
  const uploadDir = await mkdtemp(path.join(tmpdir(), "tcm-prescription-file-"));
  config.uploadDir = uploadDir;
  t.after(async () => {
    config.uploadDir = previousUploadDir;
    await rm(uploadDir, { recursive: true, force: true });
  });

  const oldStoragePath = "prescriptions/2026/08/old.jpg";
  const oldFilePath = path.join(uploadDir, ...oldStoragePath.split("/"));
  await mkdir(path.dirname(oldFilePath), { recursive: true });
  await writeFile(oldFilePath, Buffer.from("old"));
  let attachment = {
    id: 5,
    prescriptionId: 21,
    originalName: "旧处方.jpg",
    mimeType: "image/jpeg",
    fileSize: 3,
    storagePath: oldStoragePath,
    data: null,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  const prisma = {
    prescription: { findFirst: async () => prescription() },
    prescriptionAttachment: {
      findUnique: async () => attachment,
      upsert: async ({ update }) => {
        attachment = { ...attachment, ...update, updatedAt: new Date() };
        return attachment;
      },
    },
    operationLog: { create: async () => ({ id: 1 }) },
    $transaction: async (work) => work(prisma),
  };
  const image = Buffer.from([0xff, 0xd8, 0xff, 0x00]);

  await uploadPrescriptionAttachment(prisma, actor, 21, {
    filename: "新处方.jpg",
    buffer: image,
  });

  assert.match(attachment.storagePath, /^prescriptions\/\d{4}\/\d{2}\//);
  assert.equal(attachment.data, null);
  await assert.rejects(access(oldFilePath), { code: "ENOENT" });
  const downloaded = await getPrescriptionAttachment(prisma, actor, 21);
  assert.deepEqual(downloaded.data, image);
  assert.deepEqual(
    await readFile(path.join(uploadDir, ...attachment.storagePath.split("/"))),
    image,
  );
});

test("legacy prescription attachment data remains readable before backfill", async () => {
  const legacyData = Buffer.from("legacy prescription");
  const prisma = {
    prescription: { findFirst: async () => prescription() },
    prescriptionAttachment: {
      findUnique: async () => ({
        id: 5,
        originalName: "旧处方.pdf",
        mimeType: "application/pdf",
        fileSize: legacyData.length,
        storagePath: null,
        data: legacyData,
        createdAt: new Date(),
        updatedAt: new Date(),
      }),
    },
  };

  const attachment = await getPrescriptionAttachment(prisma, actor, 21);
  assert.deepEqual(attachment.data, legacyData);
});

test("deleting a prescription attachment removes its record and local file", async (t) => {
  const previousUploadDir = config.uploadDir;
  const uploadDir = await mkdtemp(path.join(tmpdir(), "tcm-prescription-delete-"));
  config.uploadDir = uploadDir;
  t.after(async () => {
    config.uploadDir = previousUploadDir;
    await rm(uploadDir, { recursive: true, force: true });
  });

  const storagePath = "prescriptions/2026/08/delete.jpg";
  const filePath = path.join(uploadDir, ...storagePath.split("/"));
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, Buffer.from("delete me"));
  let deletedId = null;
  let operation = null;
  const prisma = {
    prescription: { findFirst: async () => prescription() },
    prescriptionAttachment: {
      findUnique: async () => ({ id: 5, storagePath }),
      delete: async ({ where }) => {
        deletedId = where.id;
        return { id: where.id };
      },
    },
    operationLog: {
      create: async ({ data }) => {
        operation = data;
        return { id: 1 };
      },
    },
    $transaction: async (work) => work(prisma),
  };

  const result = await deletePrescriptionAttachment(prisma, actor, 21);

  assert.deepEqual(result, { id: 5 });
  assert.equal(deletedId, 5);
  assert.equal(operation.action, "delete_attachment");
  await assert.rejects(access(filePath), { code: "ENOENT" });
});

test("a failed prescription attachment transaction removes the new local file", async (t) => {
  const previousUploadDir = config.uploadDir;
  const uploadDir = await mkdtemp(path.join(tmpdir(), "tcm-prescription-failed-"));
  config.uploadDir = uploadDir;
  t.after(async () => {
    config.uploadDir = previousUploadDir;
    await rm(uploadDir, { recursive: true, force: true });
  });
  const prisma = {
    prescription: { findFirst: async () => prescription() },
    prescriptionAttachment: { findUnique: async () => null },
    $transaction: async () => {
      throw new Error("database write failed");
    },
  };

  await assert.rejects(
    uploadPrescriptionAttachment(prisma, actor, 21, {
      filename: "处方.jpg",
      buffer: Buffer.from([0xff, 0xd8, 0xff, 0x00]),
    }),
    /database write failed/,
  );
  const entries = await readdir(uploadDir, { recursive: true });
  assert.equal(entries.some((entry) => entry.endsWith(".jpg")), false);
});
