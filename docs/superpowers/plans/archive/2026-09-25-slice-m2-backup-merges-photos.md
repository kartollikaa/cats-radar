# Slice M2 — Backup merges photos by their own id Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An imported cat's photos are added only where their id is not here yet, apart from its row, and `update` never touches a cat's photos.

**Architecture:** `BackupMerge` decides each cat's row as before and, separately, which of its photos to add: those whose id this phone lacks, on a cat live after the merge. `MergeResult.encounters` carry no photos; `MergeResult.photos` are written by a new `addPhotos` after the rows. While the columns still hold photos, `update` keeps a row's photo columns and `addPhotos` restores a photo onto a row without one, leaving `updatedAt`.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § Storage (repository contract), § Backup (merging). Map: M2. Criteria: `many-photos-m2.md` in the acceptance directory.

## Global Constraints

As M1 (`docs/superpowers/plans/archive/2026-09-25-slice-m1-photos-as-a-list.md`), plus:
- No archive format change: `BACKUP_FORMAT_VERSION` stays 3.
- Importing the same archive twice writes nothing the second time.

---

### Task 1: The merge
`MergeResult.photos`; `BackupMerge` per cat: `writesRow`, live after the merge, photos gained (id not here, first listing wins); counted as updated when the row is kept but photos are gained. Tests: `BackupMergeTest` — a new cat's photos beside it; a kept row gaining the archive's photo; a photo here never replaced by a later copy; a cat that stays deleted gains none; one the archive brings back gains them; one photo on two archive cats arrives once.

### Task 2: Writing it
`EncounterRepository.update` documented as leaving photos; `addPhotos`; `ImportBackup` writes photos after rows. `EncounterDao.loadById`, `updateKeepingPhoto`, `restorePhoto(s)`; repository wiring; fakes in every module. Tests: `ImportBackupTest` (idempotent photo restore, a later copy keeps the photo here, a new cat arrives with its photo), `EncounterRepositoryImplTest`, `EncounterDaoRestorePhotoTest`.

### Task 3: Docs, check, review
`backup.md` § Photos, `data-model.md`; the map; M1's plan archived; `./gradlew check`; `/code-review`; acceptance gate.
