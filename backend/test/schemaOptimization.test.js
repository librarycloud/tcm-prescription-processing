import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { ROBOT_DELIVERY_STATUS } from "../src/constants/robotNotification.js";
import {
  TRANSFER_OUTBOUND_STATUS,
  TRANSFER_RETURN_STATUS,
  TRANSFER_STATUS,
} from "../src/constants/storeTransfer.js";
import { createProduct } from "../src/services/productDifferenceService.js";
import {
  createPrintTemplate,
  getPrintTemplateSettings,
  updatePrintTemplate,
} from "../src/services/printTemplateService.js";
import {
  PRINT_TEMPLATE_TYPES,
  defaultEquipmentFields,
  defaultPickupFields,
} from "../src/constants/printTemplate.js";

const schemaPath = fileURLToPath(
  new URL("../prisma/schema.prisma", import.meta.url),
);
const migrationPath = fileURLToPath(
  new URL("../prisma/migrations/00000000000000_baseline/migration.sql", import.meta.url),
);

test("workflow states use stable numeric values", () => {
  assert.deepEqual(ROBOT_DELIVERY_STATUS, {
    PENDING: 0,
    SENDING: 1,
    RETRYING: 2,
    SUCCESS: 3,
    FAILED: 4,
  });
  assert.deepEqual(TRANSFER_STATUS, {
    BORROWING: 0,
    PART_RETURNED: 1,
    RETURNED: 2,
    CANCELLED: 3,
  });
  assert.deepEqual(TRANSFER_OUTBOUND_STATUS, { PENDING: 0, CONFIRMED: 1 });
  assert.deepEqual(TRANSFER_RETURN_STATUS, { PENDING: 0, CONFIRMED: 1 });
});

test("schema contains the new integrity constraints and workload indexes", async () => {
  const schema = await readFile(schemaPath, "utf8");
  assert.match(schema, /@@unique\(\[locationId, slotNo\]\)/);
  assert.match(schema, /@@index\(\[packageId, createdAt\]\)/);
  assert.match(schema, /@@index\(\[storeId, deletedAt, status, createdAt\]\)/);
  assert.match(schema, /print_templates_one_default_per_scope/);
  assert.doesNotMatch(
    schema,
    /status\s+String\s+@default\("(?:PENDING|BORROWING)"\)/,
  );
});

test("baseline contains the package workload index without the superseded index", async () => {
  const migration = await readFile(migrationPath, "utf8");
  assert.match(
    migration,
    /INDEX `packages_store_id_deleted_at_status_created_at_idx`/,
  );
  assert.doesNotMatch(
    migration,
    /INDEX `packages_store_id_idx`/,
  );
});

test("print template default scope uses an application-maintained nullable column", async () => {
  const migration = await readFile(migrationPath, "utf8");

  assert.doesNotMatch(migration, /GENERATED ALWAYS/i);
  assert.match(migration, /`default_scope` VARCHAR\(64\) NULL/);
  assert.match(
    migration,
    /UNIQUE INDEX `print_templates_one_default_per_scope`/,
  );
});

test("default print template seeding populates default scope", async () => {
  let seeded;
  const prisma = {
    store: {
      findUnique: async () => ({ id: 3, status: 1, deletedAt: null }),
    },
    printTemplate: {
      findMany: async ({ select }) => (select ? [] : []),
      createMany: async ({ data }) => {
        seeded = data;
        return { count: data.length };
      },
    },
  };

  await getPrintTemplateSettings(prisma, { id: 1, role: 0 }, { storeId: 3 });

  assert.ok(seeded.length > 0);
  for (const template of seeded) {
    assert.equal(
      template.defaultScope,
      template.isDefault ? `3:${template.templateType}` : null,
    );
  }
});

test("existing stores receive the 75mm thermal processing template", async () => {
  let seeded = [];
  let findManyCall = 0;
  const prisma = {
    store: {
      findUnique: async () => ({ id: 3, status: 1, deletedAt: null }),
    },
    printTemplate: {
      findMany: async () => {
        findManyCall += 1;
        if (findManyCall === 1) {
          return [{ templateType: PRINT_TEMPLATE_TYPES.PROCESSING, widthMm: 70, heightMm: 50 }];
        }
        return [];
      },
      createMany: async ({ data }) => {
        seeded = data;
        return { count: data.length };
      },
    },
  };

  await getPrintTemplateSettings(prisma, { id: 1, role: 0 }, { storeId: 3 });

  const thermal = seeded.filter(
    (item) =>
      item.templateType === PRINT_TEMPLATE_TYPES.PROCESSING &&
      Number(item.widthMm) === 75 &&
      Number(item.heightMm) === 50,
  );
  assert.equal(thermal.length, 1);
  assert.equal(thermal[0].isDefault, 0);
});

test("equipment labels identify the QR code as fixed to the equipment", () => {
  const notice = defaultEquipmentFields().find(
    (field) => field.id === "custom_equipment_notice",
  );
  assert.equal(notice?.text, "固定设备码");
});

test("print template writes populate and clear default scope transactionally", async () => {
  const fields = defaultPickupFields();
  let cleared;
  let createdData;
  let updatedData;
  const current = {
    id: 8,
    storeId: 3,
    templateType: PRINT_TEMPLATE_TYPES.PACKAGE_PICKUP,
    name: "Default",
    widthMm: 70,
    heightMm: 50,
    layoutJson: JSON.stringify({ version: 1, fields }),
    enabled: 1,
    isDefault: 1,
    defaultScope: "3:PACKAGE_PICKUP",
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  const prisma = {
    store: {
      findUnique: async () => ({ id: 3, status: 1, deletedAt: null }),
    },
    printTemplate: {
      findUnique: async () => current,
      updateMany: async (args) => {
        cleared = args;
        return { count: 1 };
      },
      create: async ({ data }) => {
        createdData = data;
        return { ...current, ...data, id: 9, store: { id: 3 } };
      },
      update: async ({ data }) => {
        updatedData = data;
        return { ...current, ...data, store: { id: 3 } };
      },
    },
    $transaction: async (work) => work(prisma),
  };
  const actor = { id: 1, role: 0 };

  await createPrintTemplate(prisma, actor, {
    storeId: 3,
    templateType: PRINT_TEMPLATE_TYPES.PACKAGE_PICKUP,
    name: "Default",
    widthMm: 70,
    heightMm: 50,
    fields,
    isDefault: true,
  });
  assert.deepEqual(cleared.data, { isDefault: 0, defaultScope: null });
  assert.equal(createdData.defaultScope, "3:PACKAGE_PICKUP");

  await updatePrintTemplate(prisma, actor, 8, { isDefault: false });
  assert.equal(updatedData.defaultScope, null);
});

test("deleted product codes remain reserved and produce a recovery hint", async () => {
  const prisma = {
    store: {
      findUnique: async () => ({ id: 3, status: 1, deletedAt: null }),
    },
    product: {
      findFirst: async () => ({ id: 9, deletedAt: new Date() }),
      create: async () =>
        assert.fail("reserved product code must not be created"),
    },
  };

  await assert.rejects(
    createProduct(
      prisma,
      { id: 1, role: 0 },
      { storeId: 3, productCode: "P001" },
    ),
    /已删除，请先恢复原商品/,
  );
});
