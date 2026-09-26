# Slice S2b — Every photo names its shot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `EncounterPhoto.shotId` is the shot's id on every photo and never null; a photo of one cat names itself.

**Architecture:** The domain field becomes `shotId: String` (no default) and the computed `shot` goes. Writers set it to the photo's own id. `EncounterPhotoEntity.shotId` becomes NOT NULL; database v6 by a hand-written `MigrationFrom5To6` that rebuilds `encounter_photos` with `COALESCE(shotId, id)` — SQLite cannot add NOT NULL to an existing column — and recreates its three indices, with no foreign key check. The archive record keeps `shotId: String? = null`: absent reads as the photo's own id, which is what every format 6 and older archive already meant, so the format number stays 6.

**Spec:** `docs/superpowers/specs/2026-09-25-several-cats-per-photo-design.md` § The model, § Storage, § Backup (amended in this slice). Map: S2b. Criteria: `several-cats-s2b.md` in the acceptance directory.

## Global Constraints

- `CATS_DATABASE_VERSION` = 6; `6.json` exported and copied to the test assets; `SchemaAssetSyncTest` lists it.
- `BACKUP_FORMAT_VERSION` stays 6.
- No photo row is edited except by the migration.
- Fresh evidence: `--no-build-cache` on filtered runs, `check --rerun-tasks` for the gate.

---

### Task 1: The field is the shot's id

- [ ] Tests first: `LogPhotoTest` / `ImportPhotosTest` start-its-own-shot tests assert `shotId == photo.id`; `AttachPhotoTest`, `CarriedPhotoTest` whole-photo expectations name the photo's own id; `EncounterPhotoShotTest` is deleted with `shot`.
- [ ] `EncounterPhoto.shotId: String`; writers (`LogPhoto`, `ImportPhotos`, `AttachPhoto`, `carriedPhoto`) pass the photo's id; `EncounterPhotoRecord.toDomain()` passes `shotId ?: id`; fixtures default to the photo's id; `.shot` uses become `.shotId`.
- [ ] Domain, data, presentation, app host tests green. Commit. Mutants: a writer naming another id; the record reading an absent key as anything but the photo's id.

### Task 2: Database v6

- [ ] Tests first (`CatsDatabaseMigrationTest`): a v5 database holding a photo with a null `shotId`, one naming its own id, one naming another photo, and an orphan photo, reaches v6 with every row, every value unchanged, `shotId` filled with the row's id where it was null, the column NOT NULL, and all three indices; the app's own builder opens a v5 file with an orphan photo and reads its cats (`PhotosMigrationTest`), and brings v1/v2/v3 files to v6 with each photo naming itself. `DatabaseSchemaTest`: `shotId` not null.
- [ ] Entity `shotId: String`; `CATS_DATABASE_VERSION = 6`; `TestCatsDatabase` 6; `MigrationFrom5To6` with the SQL of the exported `6.json`; builder registers it; `6.json` exported and copied.
- [ ] Data tests green. Commit. Mutants: the rebuild without `COALESCE`; an index not recreated; a row lost.

### Task 3: Docs, check, device, review

- [ ] `data-model.md` (§ Photos, version 6), `backup.md` (every record names its shot; an absent one is the photo's own), the spec's § The model / § Storage / § Backup, the map.
- [ ] `./gradlew check --rerun-tasks`.
- [ ] Device: a `main` build with a seeded v5 database (single photos with a null `shotId`, a shot of three) upgraded in place to this build: v6, every photo names its shot, the cats and files are all there.
- [ ] `/code-review`; acceptance gate; PR.
