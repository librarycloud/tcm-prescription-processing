# Prisma migrations

`migrations/00000000000000_baseline` is the current schema baseline. It is the
only migration Prisma loads for new databases.

The previous incremental migrations and recovery SQL are preserved under
`migrations_archive_20260726/` for audit and rollback reference. The archive is
outside Prisma's active migrations directory and must not be copied back into
`migrations/` alongside the baseline.

## Existing database

The existing database already contains the schema represented by the baseline.
Before marking it as baselined, make a backup of Prisma's migration metadata
and clear only that metadata table:

```sql
CREATE TABLE `_prisma_migrations_backup_20260726` LIKE `_prisma_migrations`;
INSERT INTO `_prisma_migrations_backup_20260726`
  SELECT * FROM `_prisma_migrations`;
DELETE FROM `_prisma_migrations`;
```

From `backend/`, mark the baseline as applied without executing its DDL:

```powershell
$env:DATABASE_URL = 'mysql://user:password@host:3306/database'
npx prisma migrate resolve --applied 00000000000000_baseline
npx prisma migrate status
```

Only run this procedure after verifying that the live schema matches
`schema.prisma`. The backup table is retained so the metadata can be restored
if the migration directory is reverted together with the archived migrations.
