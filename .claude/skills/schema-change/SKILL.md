---
name: schema-change
description: Use when changing Cats Radar's Room schema or backup archive shape, adding or altering persisted fields, choosing a new database or archive version, reviewing generated Room migration SQL, or preserving compatibility with older databases and backups.
---

# Change a Cats Radar schema

## Overview

Room's database version and the backup archive's `formatVersion` are independent contracts. Claim
and test only the one the change needs; a feature can require both, but one number never substitutes
for the other.

| Contract | Source of truth |
|---|---|
| Room database | `CATS_DATABASE_VERSION` in `data/src/commonMain/kotlin/dev/catsradar/data/db/CatsDatabase.kt` |
| Backup archive | `BACKUP_FORMAT_VERSION` in `data/src/commonMain/kotlin/dev/catsradar/data/backup/BackupRecords.kt` |

## Claim a version without racing another branch

Read the remote base, never the working tree. Fetch open branch heads before choosing a number:

```bash
git fetch origin main '+refs/heads/*:refs/remotes/origin/*'
DB_PATH=data/src/commonMain/kotlin/dev/catsradar/data/db/CatsDatabase.kt
BACKUP_PATH=data/src/commonMain/kotlin/dev/catsradar/data/backup/BackupRecords.kt
CURRENT_DB=$(git show "origin/main:$DB_PATH" | sed -n 's/.*CATS_DATABASE_VERSION = \([0-9][0-9]*\).*/\1/p')
CURRENT_BACKUP=$(git show "origin/main:$BACKUP_PATH" | sed -n 's/.*BACKUP_FORMAT_VERSION = \([0-9][0-9]*\).*/\1/p')
NEXT_DB=$((CURRENT_DB + 1))
NEXT_BACKUP=$((CURRENT_BACKUP + 1))
```

Run the same matcher against each current value on `origin/main` as a positive control. Search every
pushed branch on `origin`, including claims that do not have a PR yet:

```bash
git show "origin/main:$DB_PATH" | grep "CATS_DATABASE_VERSION = $CURRENT_DB"
git show "origin/main:$BACKUP_PATH" | grep "BACKUP_FORMAT_VERSION = $CURRENT_BACKUP"
git for-each-ref --format='%(refname:short)' refs/remotes/origin/ | while IFS= read -r ref; do
    [[ $ref == origin/HEAD || $ref == origin/main ]] && continue
    git show "$ref:$DB_PATH" 2>/dev/null | grep "CATS_DATABASE_VERSION = $NEXT_DB" \
      && printf 'database version claimed by %s\n' "$ref"
    git show "$ref:$BACKUP_PATH" 2>/dev/null | grep "BACKUP_FORMAT_VERSION = $NEXT_BACKUP" \
      && printf 'backup version claimed by %s\n' "$ref"
  done
```

Also inspect open fork PRs: fetch each live `.head.repo.clone_url` at `.head.sha` into a temporary
ref and run the same two matchers there. Do not assume `origin/<head.ref>` exists for a fork.

A hit means the number is already claimed: stop, refresh `origin/main`, and coordinate rather than
silently taking the same number.

## Room database change

1. Add the new column/table to the entity, bump `CATS_DATABASE_VERSION`, and add a temporary
   `AutoMigration(from = <from>, to = <to>)` declaration to the Room database annotation. It exists
   only to make Room show the migration it would generate.
2. Run `./gradlew :data:kspAndroidMain`, locate the generated
   `CatsDatabase_AutoMigration_<from>_<to>_Impl.kt` under the module's build output, and read every
   SQL statement.
3. A generated rebuild (`CREATE _new_`, copy, drop, rename) of a table with a foreign key plus
   `foreignKeyCheck` is not safe for historical orphan rows. For a nullable-column change, write an
   `ALTER TABLE ... ADD COLUMN` `Migration` in
   `data/src/commonMain/kotlin/dev/catsradar/data/db/Migrations.kt`, register it in
   `data/src/androidMain/kotlin/dev/catsradar/data/db/CatsDatabaseFactory.android.kt`, and remove the
   unsafe temporary AutoMigration declaration. If the generated SQL is safe, retain the declaration
   and cover it with the migration tests instead.
4. In `CatsDatabaseMigrationTest`, create the old schema with an orphan child row, run the explicit
   migration, and prove both the orphan and the new default survive. Also run `DatabaseSchemaTest`
   and any domain-specific migration test such as `PhotosMigrationTest`.
5. Commit the generated schema in both
   `data/schemas/dev.catsradar.data.db.CatsDatabase/` and
   `data/src/androidHostTest/assets/dev.catsradar.data.db.CatsDatabase/`; `SchemaAssetSyncTest`
   proves the copies agree.

## Backup archive change

1. Bump `BACKUP_FORMAT_VERSION` only when the serialized archive contract changes. Update the
   records and reader/writer in `data/src/commonMain/kotlin/dev/catsradar/data/backup/BackupRecords.kt`
   and `data/src/androidMain/kotlin/dev/catsradar/data/backup/ZipBackupArchive.android.kt`.
2. Add an older-format read case to `ZipBackupReaderOlderFormatTest`: absent new data receives its
   defined default, while a future format remains rejected.
3. Update `ZipBackupArchiveTest` for the new manifest and add/update `BackupMergeTest` so importing
   older and current records preserves the merge contract.
4. Update both `docs/features/backup.md` and `docs/features/data-model.md` in the same PR.

## Verification

Use `device-check` before installing any schema branch on a shared emulator; never downgrade a
device database or upgrade another session's base package. Finish with:

```bash
CI=true ./gradlew check :app:assembleRelease --console=plain
```

## Common mistakes

| Mistake | Correct action |
|---|---|
| Increment both versions automatically | Bump only the contract that changed |
| Pick the next number from the local file | Read `origin/main` and open remote heads |
| Trust generated AutoMigration by name | Read its SQL and test orphan rows |
| Test only current backup round-trip | Read an older format and exercise merge semantics |
| Install on the shared emulator blindly | Use `device-check` and isolate schema changes |
