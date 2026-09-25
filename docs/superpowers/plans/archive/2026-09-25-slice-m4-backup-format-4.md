# Slice M4 — Backup format 4 carries every photo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The archive lists every photo of every cat in `encounter_photos.json` (format 4), and archives before it still import by the migration's rule.

**Architecture:** `EncounterPhotoRecord` and the list entry sit beside the other lists, written before the photo files. The reader takes photos from the list for format 4 and from each cat's own record before it (`carriedPhoto`, now in `data/backup`), checks every photo's paths, and hands each cat the photos that name it, oldest first; a photo naming no cat in the archive is dropped. The cut-off guard asks for the lists each format writes.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § Backup. Map: M4. Criteria: `many-photos-m4.md` in the acceptance directory.

## Global Constraints

As M1–M3 (`docs/superpowers/plans/archive/`), plus:
- `BACKUP_FORMAT_VERSION` 4; an encounter record keeps its photo fields only to read older archives.

---

### Task 1: Records and the writer
`EncounterPhotoRecord` both ways; `ENCOUNTER_PHOTOS_ENTRY`; format 4; `Encounter.toRecord()` without photo fields; the writer's list and every photo's files. Tests: `ZipBackupPhotoListTest` (listed with every field, none on the cat, two photos round trip, every photo's files), `ZipBackupArchiveTest` (format 4).

### Task 2: The reader
Photos from the list or the record by format; paths checked for every photo; photos grouped onto their cats; `listsOfFormat`. Tests: `ZipBackupReaderOlderFormatTest` (format 3 carried photo), `ZipBackupPhotoListTest` (orphan dropped, paths refused, list missing refused), `BackupRestoreTest` with a two-photo cat.

### Task 3: Docs, check, review
`backup.md`; the map; M3's plan archived; `./gradlew check`; `/code-review`; acceptance gate.
