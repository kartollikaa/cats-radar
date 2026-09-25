# Slice V3 — Gallery link for imported and picked photos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A photo imported or attached from the gallery remembers the MediaStore item it came from, and the viewer's gallery button opens it.

**Architecture:** A `:domain` `GalleryItemLocator` turns a picked URI into its MediaStore item, answered in `:data/androidMain` by parsing the picker's URI shape (and `MediaStore.getMediaUri` for DocumentsUI). The item travels as `sourceMediaUri` through `Encounter`, `PhotoStamp`, Room (v3 by auto-migration) and backups (format 3). `galleryLink` gains the picked kind; a picked link opens without an existence check, and the shell's grant fallback opens it with the gallery's own access.

**Spec:** `docs/superpowers/specs/2026-09-24-photo-viewer-and-gallery-link-design.md` § Deriving a picked photo's MediaStore item, § Schema and backups. Criteria: `photo-viewer-v3.md` in the acceptance directory.

## Global Constraints

As V1/V2 (`docs/superpowers/plans/archive/`), plus:
- Room schema changes go through an `AutoMigration`, the exported `data/schemas/…/3.json`, and its copy in the test assets.
- A backup field the previous format lacks raises `BACKUP_FORMAT_VERSION` (`docs/features/backup.md`).
- `Encounter.sourceMediaUri` defaults to null: only picks set it.

---

### Task 1: The locator
`domain/…/platform/GalleryItemLocator.kt` (`suspend fun locate(pickedUri: String): String?`); `data/…/androidMain/platform/MediaStoreItemLocator.android.kt`; Robolectric `MediaStoreItemLocatorTest`: the two captured URIs → `content://media/external/images/media/<id>`; a `media` MediaStore URI kept; DocumentsUI `image:<id>` through `MediaStore.getMediaUri`; cloud authority, another user's picker URI, another provider, `file://`, garbage → null. Bind in `DataModule`.

### Task 2: The column
`EncounterEntity.sourceMediaUri`, `CatsDatabase` version 3 with `AutoMigration(2, 3)`, exported schema + asset copy, `CatsDatabaseMigrationTest` v2 → v3; `Encounter.sourceMediaUri = null`; `PhotoStamp.sourceMediaUri`; `EncounterMapper` both ways; `EncounterDao.attachPhoto` and the repository write it; fakes in `:domain`, `:data`, `:presentation` tests; fixtures and round-trip tests.

### Task 3: Recording it
`ImportPhotos` stores `locate(uri)`; `AttachPhoto` stores it for `PhotoSource.GALLERY` only. Tests: `ImportPhotosTest`, `AttachPhotoTest`.

### Task 4: Opening it
`GalleryLink(uri, ownedByApp)`; `galleryLink` falls back to `sourceMediaUri` as a picked link (install rule unchanged, camera original first); `ResolveGalleryLink` opens a picked link without asking `GalleryItems`. Tests: `GalleryLinkTest`, `ResolveGalleryLinkTest`, `PhotoViewerStateMapperTest`.

### Task 5: Backups
`EncounterRecord.sourceMediaUri` both ways; `BACKUP_FORMAT_VERSION = 3`; a format-2 archive still imports. Tests: backup round trip, the format-2 reader.

### Task 6: Docs, check, device
`import.md`, `photos.md`, `photo-viewer.md`, `data-model.md`, `backup.md`; `./gradlew check`; AC-14 on a throwaway AVD for both pick paths.
