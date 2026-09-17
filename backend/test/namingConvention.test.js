import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import test from "node:test";

const schemaPath = new URL("../prisma/schema.prisma", import.meta.url);
const migrationsPath = new URL("../prisma/migrations/", import.meta.url);

test("camelCase Prisma scalar fields map to snake_case database columns", async () => {
  const schema = await readFile(schemaPath, "utf8");
  const camelScalarLines = schema
    .split(/\r?\n/)
    .filter((line) =>
      /^\s{2}[a-z]\w*[A-Z]\w*\s+(?:Int|String|DateTime|Boolean|Float|Decimal|BigInt|Json|Bytes)/.test(
        line,
      ),
    );

  assert.ok(camelScalarLines.length > 0);
  for (const line of camelScalarLines) {
    const mappedName = line.match(/@map\("([a-z0-9_]+)"\)/)?.[1];
    assert.ok(mappedName, `missing @map(): ${line.trim()}`);
    assert.match(mappedName, /^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$/);
  }
});

test("snake_case migration covers every mapped scalar column", async () => {
  const [schema, migrationEntries] = await Promise.all([
    readFile(schemaPath, "utf8"),
    readdir(migrationsPath, { withFileTypes: true }),
  ]);
  const migrations = await Promise.all(
    migrationEntries
      .filter((entry) => entry.isDirectory())
      .map((entry) =>
        readFile(new URL(`${entry.name}/migration.sql`, migrationsPath), "utf8"),
      ),
  );
  const migration = migrations.join("\n");
  const mappedColumns = schema
    .split(/\r?\n/)
    .filter((line) =>
      /^\s{2}[a-z]\w*\s+(?:Int|String|DateTime|Boolean|Float|Decimal|BigInt|Json|Bytes)/.test(
        line,
      ),
    )
    .map((line) => line.match(/@map\("([a-z0-9_]+)"\)/)?.[1])
    .filter(Boolean);

  for (const column of new Set(mappedColumns)) {
    assert.match(
      migration,
      new RegExp("(?:TO|COLUMN) `" + column + "`|^\\s*`" + column + "`\\s+", "m"),
    );
  }
});

test("migration identifiers fit the MySQL 64-character limit", async () => {
  const migrationEntries = await readdir(migrationsPath, { withFileTypes: true });
  const migrations = await Promise.all(
    migrationEntries
      .filter((entry) => entry.isDirectory())
      .map(async (entry) => ({
        name: entry.name,
        sql: await readFile(
          new URL(`${entry.name}/migration.sql`, migrationsPath),
          "utf8",
        ),
      })),
  );

  for (const migration of migrations) {
    const identifiers = [...migration.sql.matchAll(/`([^`]+)`/g)].map(
      (match) => match[1],
    );
    for (const identifier of identifiers) {
      assert.ok(
        identifier.length <= 64,
        `${migration.name}: MySQL identifier exceeds 64 characters: ${identifier}`,
      );
    }
  }
});

test("processing cancellation uses the documented numeric value", async () => {
  const constants = await readFile(
    new URL("../src/constants/processing.js", import.meta.url),
    "utf8",
  );
  assert.match(constants, /CANCELLED:\s*5/);
  assert.doesNotMatch(constants, /\bCANCEL:\s*\d/);
});
