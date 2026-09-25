# Slice M1 — A cat's photos become a list Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Encounter` carries `photos: List<EncounterPhoto>` instead of the five photo fields, while Room and the archive keep storing them in the same columns and fields.

**Architecture:** A new `:domain` `EncounterPhoto` and `Encounter.photos` (oldest first; `cover` is the first). `:data` turns the five columns, and the five archive fields, into zero or one photo by one rule — the cat's `id`, `deviceId` and `createdAt` — in `carriedPhoto`, and writes the cover back. `attachPhoto(id, PhotoStamp)` becomes `addPhoto(EncounterPhoto)`; the gallery link moves onto the photo. Every reader of the old fields reads the cover.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § The model. Map: `docs/tbd/decompositions/2026-09-25-many-photos-per-cat.md` M1. Criteria: `many-photos-m1.md` in the acceptance directory.

## Global Constraints

As V1–V3 (`docs/superpowers/plans/archive/`), plus:
- No schema, archive format or UI change in this slice: `CatsDatabase` stays version 3, `BACKUP_FORMAT_VERSION` stays 3, no State class changes shape.
- A photo written by any use case in this slice has `id` = its cat's id and `deviceId` = its cat's `deviceId`: those are what the columns can hold, so they are what a read gives back.

---

### Task 1: The model
`domain/…/model/EncounterPhoto.kt` (the data class of the spec); `Encounter.photos: List<EncounterPhoto> = emptyList()` replacing the five fields, and `Encounter.cover`; `GalleryLink.kt`'s `galleryLink` on `EncounterPhoto`, keyed on the photo's `deviceId`; `PhotoStamp` deleted; `EncounterRepository.addPhoto(photo): Boolean` replacing `attachPhoto`. Tests: `GalleryLinkTest` on photos.

### Task 2: Use cases
`LogTally`, `LogPhoto`, `ImportPhotos`, `AttachPhoto` build their photo (`addedAt` = now); `PurgeDeleted` deletes every photo's files; `StatsCalculator` counts cats with photos; `ResolveGalleryLink(photo)`. Fakes: `FakeEncounterRepository.addPhoto` (live cat without photos), `findBySourceDigest` over photos. Tests: `LogPhotoTest`, `ImportPhotosTest`, `AttachPhotoTest`, `PurgeDeletedTest` (every photo's copy and thumbnail), `StatsCalculatorTest`, `ResolveGalleryLinkTest`, `SetCoatTest`, backup merge tests.

### Task 3: Data
`data/…/repository/CarriedPhoto.kt` (`carriedPhoto(...)`, null when `photoPath` is null); `EncounterMapper` both ways through it; `EncounterRepositoryImpl.addPhoto` onto the existing guarded `attachPhoto` query, `updatedAt` = `addedAt`; `BackupRecords` through it. Tests: `EncounterMapperTest` (a row with a photo reads as one photo with the cat's id, install and creation time; a row with a thumbnail but no copy reads as none; a cat with a cover writes its columns; round trip), `EncounterRepositoryImplTest`, backup round trip; fixtures and `FakeEncounterDao`.

### Task 4: Presentation and app
`EncountersStateMapper`, `CounterStateMapper`, `EncounterDetailStateMapper`, `PhotoViewerStateMapper` and `PhotoViewerStore` read the cover; presentation and app fixtures. State classes unchanged. Tests: the existing mapper and store tests, unchanged in what they assert.

### Task 5: Docs, check, review
`data-model.md` (photo fields as a list), `photos.md` (where the code lives); `./gradlew check`; `/code-review`; acceptance gate.
