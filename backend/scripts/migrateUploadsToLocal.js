import { PrismaClient } from "@prisma/client";
import {
  removeUploadFile,
  saveUploadFile,
} from "../src/services/localUploadStorage.js";

const prisma = new PrismaClient();

async function migrateModel(model, category) {
  let migrated = 0;
  while (true) {
    const rows = await model.findMany({
      where: { storagePath: null, data: { not: null } },
      select: { id: true, mimeType: true, data: true },
      take: 20,
      orderBy: { id: "asc" },
    });
    if (!rows.length) return migrated;

    for (const row of rows) {
      const storagePath = await saveUploadFile(row.data, {
        category,
        mimeType: row.mimeType,
      });
      try {
        const updated = await model.updateMany({
          where: { id: row.id, storagePath: null },
          data: { storagePath, data: null },
        });
        if (!updated.count) {
          await removeUploadFile(storagePath);
          continue;
        }
        migrated += 1;
      } catch (error) {
        await removeUploadFile(storagePath);
        throw error;
      }
    }
  }
}

async function main() {
  const prescriptionCount = await migrateModel(
    prisma.prescriptionAttachment,
    "prescriptions",
  );
  const processingPhotoCount = await migrateModel(
    prisma.processingPhoto,
    "processing-photos",
  );
  console.log(
    `Local upload migration complete: ${prescriptionCount} prescription attachment(s), ${processingPhotoCount} processing photo(s).`,
  );
}

try {
  await main();
} finally {
  await prisma.$disconnect();
}
