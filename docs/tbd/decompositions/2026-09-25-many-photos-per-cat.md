# Many photos per cat — PR Decomposition Map

- **Created:** 2026-09-25
- **Epic reference:** [docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md](../../superpowers/specs/2026-09-25-many-photos-per-cat-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, exported Room schemas (`data/schemas/**` and their test-asset copies), screenshot baselines.
- **Integration strategy:** every slice is **naturally safe**. A cat can only gain a second photo once M7
  offers it, and by then the storage, the backup and the viewer all hold many.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| M1 | A cat's photos become a list | `Encounter` carries `photos: List<EncounterPhoto>` instead of five columns' worth of fields, still stored in those columns; nothing changes on screen or on disk. | safe | ~700 | — | merged |
| M2 | Backup merges photos by their own id | An imported cat's photos are added only where absent, independently of its row, and `update` never touches photos. | safe | ~300 | M1 | merged |
| M3 | Photos move to their own table | Database v4: `encounter_photos`, a hand-written migration moving every photo, and the repository reading and writing the table. | safe | ~650 | M2 | in-review |
| M4 | Backup format 4 carries every photo | `encounter_photos.json` in the archive; older formats read by the migration's rule. | safe | ~450 | M3 | planned |
| M5 | Attaching a photo to a cat that has one | `AttachPhoto` adds to any live cat, skips a photo already on it, keeps links on every cat. | safe | ~350 | M3 | planned |
| M6 | The viewer pages through a cat's photos | `PhotoViewer(encounterId, photoId)` with a pager and a per-photo gallery link. | safe | ~550 | M3 | planned |
| M7 | Adding photos from the detail screen | A pager of the cat's photos and an always-on *Add photo* with a multi-select gallery pick. | safe | ~650 | M4, M5, M6 | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice M1 — A cat's photos become a list
- **In scope:** `EncounterPhoto`; `Encounter.photos` replacing the five fields; the data mapper turning the
  columns into zero or one photo (`id` = the cat's, `deviceId` = the cat's, `addedAt` = `createdAt`) and
  back; `attachPhoto(id, PhotoStamp)` becoming `addPhoto(EncounterPhoto)` (still only on a cat without
  one); `LogPhoto`, `ImportPhotos`, `AttachPhoto`, `PurgeDeleted`, `StatsCalculator`, `galleryLink` on a
  photo, `ResolveGalleryLink`; the four presentation mappers reading the cover; backup records mapped
  through the list (format unchanged); fakes and fixtures; `data-model.md`, `photos.md`.
- **Out of scope:** any schema, archive or screen change.
- **Ships safely because:** a refactor — the same columns, the same archive, the same state.
- **Size:** over target because every fixture building an `Encounter` changes with the model; splitting
  domain from presentation would leave one of them not compiling.
- **Cleanup owed:** none.

### Slice M2 — Backup merges photos by their own id
- **In scope:** `MergeResult.photos`; `BackupMerge` adding an archive photo only when its `id` is absent
  and its cat is live here after the merge; a cat gaining photos counted as updated; `update` preserving
  the photo columns; `addPhotos`; `ImportBackup` writing photos after rows; `backup.md`.
- **Out of scope:** the archive format.
- **Ships safely because:** it changes one edge only — a later archive copy of a cat no longer replaces a
  photo already here, which is what `backup.md` already promised for files.
- **Cleanup owed:** M3 replaces the column-preserving `update` with a plain `@Update`.

### Slice M3 — Photos move to their own table
- **In scope:** `EncounterPhotoEntity`, its DAO queries and the `@Relation` read; database v4 by
  `Migration(3, 4)` registered in the builder; exported schema 4 and its asset copy; the migration tests
  of the spec (v1/v2/v3 → v4, the real builder, the cascade, each seen failing); DAO tests for
  `addPhoto`, `addPhotos`, `findBySourceDigest` and the purge; `data-model.md`, `photos.md`.
- **Out of scope:** the archive format; more than one photo per cat through any use case.
- **Ships safely because:** no behaviour changes; the only risk is the migration, which is its whole
  test surface.
- **Cleanup owed:** none.

### Slice M4 — Backup format 4 carries every photo
- **In scope:** `EncounterPhotoRecord`; `encounter_photos.json`; `BACKUP_FORMAT_VERSION` 4; the cut-off
  guard per format; reading formats 1–3 into photos by the migration's rule; path checks for every photo;
  files for every photo; `backup.md`.
- **Out of scope:** merge rules (M2).
- **Ships safely because:** until M7 every cat has at most one photo, which both the old and new formats
  hold.
- **Cleanup owed:** none.

### Slice M5 — Attaching a photo to a cat that has one
- **In scope:** `addPhoto` guarded by a live cat only; `AttachPhoto` without the no-photo check, with
  `AttachResult.AlreadyThere` for a digest already on the cat, and links kept on another install's cat;
  `photos.md`, `photo-viewer.md` (the install rule).
- **Out of scope:** any screen.
- **Ships safely because:** the detail screen still offers attaching only on a cat without a photo.
- **Cleanup owed:** none.

### Slice M6 — The viewer pages through a cat's photos
- **In scope:** the key's `photoId` (optional for restored keys); `PhotoViewerState` holding the photos
  with their link flags; `GalleryClicked(photoId)`; `ResolveGalleryLink` per photo; a `HorizontalPager` of
  Telephoto images with a position in the top bar; the detail screen passing its cover's id; EN/RU
  strings; `photo-viewer.md`.
- **Out of scope:** the detail screen's pager (M7).
- **Ships safely because:** every cat has at most one photo, so the pager is a single page.
- **Cleanup owed:** none.

### Slice M7 — Adding photos from the detail screen
- **In scope:** detail state with the photo list and the add section on every live cat; the pager and its
  position; `PhotoClicked(photoId)`; the multi-select picker capped by `Tuning.ATTACH_BATCH_MAX`; the
  sequential batch with progress, the wait for every attached photo, and the messages; EN/RU strings;
  `encounter-detail.md`, `photos.md`.
- **Out of scope:** removing or reordering photos; a count on Encounters tiles.
- **Ships safely because:** it is the feature; everything under it is in place.
- **Cleanup owed:** none.

## Decision log

- 2026-09-25: **M2 merged** as #153. Until the table exists, `update` kept a row's photo columns and a
  restore filled them only where empty; M3 replaces both.

- 2026-09-25: **M1 merged** as #151 (~910 reviewable lines, fixture churn). A row with a thumbnail but no
  copy now reads as a cat without a photo, and the import's digest lookup skips it too; no writer makes
  such a row. The gate's round 2 caught that its criteria contradicted each other on that shape.

- 2026-09-25: owner asked for many photos per cat, added from the detail screen, and chose **every
  photo in one table** over keeping the first on the cat's row. The migration moves data only phones
  hold, so it gets its own slice (M3), a model refactor ahead of it (M1) keeps that diff to storage, and
  the merge rule that the table makes necessary lands first (M2) so the storage move changes no
  behaviour.
