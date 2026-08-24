# Prisma migrations

`migrations/00000000000000_baseline` is a complete schema snapshot of GitHub
commit `2c653bb` (`feat: allow store staff miniprogram access`). A new, empty
database runs that baseline first, then any migrations that follow it.

Earlier incremental migration files are preserved in
`migrations_archive_20260824/` for audit only. That directory is not loaded by
Prisma and must not be copied back into `migrations/`.

## New database

From `backend/`, configure `DATABASE_URL` and run:

```sh
npm run prisma:deploy
```

Prisma creates the schema from the baseline.

## Existing server database

The server database at commit `2c653bb` already has everything in the new
baseline, but its `_prisma_migrations` rows refer to the archived migration
files. Before deploying this migration reset, take a database backup and back
up the metadata table:

```sql
CREATE TABLE `_prisma_migrations_backup_20260824` LIKE `_prisma_migrations`;
INSERT INTO `_prisma_migrations_backup_20260824`
  SELECT * FROM `_prisma_migrations`;
DELETE FROM `_prisma_migrations`;
```

Then, from the deployed `backend/` directory, mark only the new baseline as
already applied. This does not execute its table-creation SQL:

```sh
npx prisma migrate resolve --applied 00000000000000_baseline
npx prisma migrate status
npm run prisma:deploy
```

The final deploy does not execute baseline DDL. Do not run `prisma migrate
reset` against a populated server database. Before the metadata change, confirm
the live schema matches commit `2c653bb`; if it does not, stop and reconcile
that difference first.
