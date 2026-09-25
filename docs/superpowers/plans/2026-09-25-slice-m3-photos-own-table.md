# Slice M3 — Photos move to their own table Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Database v4 keeps every photo as a row of `encounter_photos`; a hand-written migration moves each cat's photo there and drops the five columns; the repository reads and writes the table.

**Architecture:** `EncounterPhotoEntity` with a cascading foreign key to `encounters`; reads return `EncounterWithPhotos` (`@Embedded` + `@Relation`), mapped to `Encounter.photos` ordered by `addedAt`, `id`. `Migration(3, 4)` creates the table with Room's exported SQL, copies by the `carriedPhoto` rule, drops the digest index and then each column with `ALTER TABLE … DROP COLUMN` (a rebuild's `DROP TABLE` would cascade into the copied rows while foreign keys are on). The builder `createCatsDatabase` uses is shared with the test that opens a migrated file.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § Storage, § The migration. Map: M3. Criteria: `many-photos-m3.md` in the acceptance directory.

## Global Constraints

As M1/M2 (`docs/superpowers/plans/archive/`), plus:
- `CatsDatabase` version 4; `data/schemas/…/4.json` exported and copied byte-for-byte into the test assets.
- Archive format stays 3: the records still carry the cover's five fields.
- No behaviour a user can see changes.

---

### Task 1: The table and its reads
`EncounterPhotoEntity`, `EncounterWithPhotos`; `EncounterEntity` without the photo columns and digest index; `EncounterDao` reads returning the relation (`@Transaction`), `insertWithPhotos`, `addPhoto` (live cat without photos, stamps `updatedAt`), `addPhotos` (ignore ids already here), digest lookup through the photos, plain `@Update`; `EncounterMapper` both ways, ordering; repository wiring; `FakeEncounterDao`. Tests: DAO tests for AC-9…AC-14, repository tests.

### Task 2: The migration
`CatsDatabase` v4 with `EncounterPhotoEntity`; `Migrations.kt` with `MIGRATION_3_4`; shared builder in `CatsDatabaseFactory`; schema 4 exported and copied. Tests: `CatsDatabaseMigrationTest` (v3 matrix, v1/v2 chains, the real builder, the cascade), `DatabaseSchemaTest`, `SchemaAssetSyncTest`; mutations recorded.

### Task 3: Docs, check, review
`data-model.md` (§ Photos, version 4), `photos.md`; `./gradlew check`; `/code-review`; acceptance gate.
