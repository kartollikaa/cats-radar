# Slice S2 — Backup format 5 carries shots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every photo record in the archive carries its `shotId`, the archive says format 5, older formats read as one shot per photo, and export → import keeps a shot of several cats whole.

**Architecture:** `EncounterPhotoRecord` gains `shotId: String? = null` (the default is what reads a format 4 or older record); `toRecord()`/`toDomain()` carry it. `BACKUP_FORMAT_VERSION` becomes 5, so a format 4 reader — which ignores unknown keys — refuses the archive as too new instead of dropping the field. `BackupMerge` already moves photos as whole rows by id, so it gains tests, not code.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization (`ignoreUnknownKeys`, `encodeDefaults` off), Robolectric host tests, Room in-memory databases.

**Spec:** `docs/superpowers/specs/2026-09-25-several-cats-per-photo-design.md` § Backup, § Keeping export, import and the migration whole. Map: S2 in `docs/tbd/decompositions/2026-09-25-several-cats-per-photo.md`. Criteria: `several-cats-s2.md` in the acceptance directory. Stacked on S1 (`tech/photos-know-their-shot`, #174).

## Global Constraints

- `BACKUP_FORMAT_VERSION` = 5; no other archive change; no merge rule changes.
- A photo that starts its shot writes no `shotId` key, so its record is byte-for-byte what format 4 wrote.
- Fixtures for a shot: three cats, one with no coat; first photo `shotId = null`, the other two point at it.
- Fresh evidence: `--rerun --no-build-cache` per task, or `check --rerun-tasks`; read JUnit XML timestamps.
- Comments: default none.

---

### Task 1: The record carries the shot, and the archive says 5

**Files:** `data/src/commonMain/kotlin/dev/catsradar/data/backup/BackupRecords.kt`; tests `data/src/androidHostTest/…/backup/ZipBackupPhotoListTest.kt`, `ZipBackupArchiveTest.kt`, `ZipBackupReaderOlderFormatTest.kt`.

- [ ] Tests first:
  - `ZipBackupPhotoListTest.aShotOfThreeCatsSurvivesTheRoundTripAsOneShot` — write three cats of one shot (one uncoated) and read them back equal, `shotId` included.
  - `ZipBackupPhotoListTest.aPhotoThatStartsItsShotWritesNoShotKeyAndTheOthersNameTheFirst` — in `encounter_photos.json` the first photo's object has no `shotId` key; the other two have `"shotId":"<first>"`.
  - `ZipBackupArchiveTest.anArchiveSaysItIsFormatFiveSoAnAppBeforeShotsRefusesIt` replaces the format-4 test.
  - `ZipBackupReaderOlderFormatTest.aFormatFourArchiveReadsEveryPhotoAsStartingItsOwnShot` — a hand-written format 4 archive whose photo record has no `shotId` reads as a whole `EncounterPhoto` with `shotId = null`.
- [ ] Run: red (compile: no `shotId` on the record; the manifest says 4).
- [ ] `EncounterPhotoRecord.shotId: String? = null`; both mappings carry it; `BACKUP_FORMAT_VERSION = 5`.
- [ ] Run `:data:testAndroidHostTest --rerun --no-build-cache`: green. Commit.
- [ ] Mutants (commit first; restore with `git checkout`): `toRecord()` drops `shotId`; `toDomain()` drops it; the constant left at 4. Each named test red.

### Task 2: A shot across the merge and a real restore

**Files:** `domain/src/commonTest/…/testing/Fixtures.kt` and `data/src/commonTest/…/repository/EncounterFixtures.kt` (`Encounter.inShotOf(firstPhotoId)` — a sixth `withPhoto` parameter trips detekt's `LongParameterList`), `domain/src/commonTest/…/backup/BackupMergeTest.kt`, `data/src/androidHostTest/…/backup/BackupRestoreTest.kt`.

- [ ] `BackupMergeTest`:
  - `a cat of a shot that is not here yet joins its shot` — here: the first two cats of a shot; archive: all three, the third logged on another install. The third and its photo arrive with `shotId` = the first photo's id.
  - `a first cat deleted here after the export stays deleted and the others keep their shot` — here: the first cat deleted later than the archive's copy; archive: all three live. The first stays deleted and gains nothing; the other two keep their photos' `shotId`.
  - `a later cat whose shot's first row is not in the archive keeps its shot` — archive: only the third cat; it arrives with its `shotId`.
- [ ] `BackupRestoreTest`: `fillHere()` also writes a shot of three cats with their own files (one uncoated). `aBackupRestoredOnAnEmptyDevice…` then covers it through the snapshot; a new `aShotOfThreeCatsComesBackAsOneShotWithEveryCoatAndItsOwnFiles` asserts the three `shot` values are equal, the coats, and each cat's file bytes. `importingTheSameBackupAgain…` counts the three new cats as unchanged.
- [ ] Run domain and data host tests fresh: green. Commit.
- [ ] Mutants: `toRecord()` drops `shotId` → the restore test red; a merge that strips `shotId` from the photos it adds → the join test red.

### Task 3: Docs, check, device, review

- [ ] `docs/features/backup.md`: the photo record carries its shot; the first photo of a shot writes none; the manifest's list of versions gains "5 since photos carry their shot"; the refusal bullet names shots; an archive from before shots reads every photo as its own shot. The map: S2 status and decision log.
- [ ] `./gradlew check --rerun-tasks` green.
- [ ] Device (`.shot` suffix builds, `emulator-5554`): the `main` build's export imports into this build; this build's export is refused by the `main` build as too new; this build's export imports into a cleared install of this build with every cat and photo. The same export → clear → import on the **release** build (suffix init script for `release`, `CI=true`).
- [ ] `/code-review` on the PR; acceptance gate against `several-cats-s2.md`; PR stacked on #174.
