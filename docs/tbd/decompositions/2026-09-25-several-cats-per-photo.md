# Several cats on one photo — PR Decomposition Map

- **Created:** 2026-09-26
- **Epic reference:** [docs/superpowers/specs/2026-09-25-several-cats-per-photo-design.md](../../superpowers/specs/2026-09-25-several-cats-per-photo-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, exported Room schemas (`data/schemas/**` and their test-asset copies), screenshot baselines.
- **Integration strategy:** every slice is **naturally safe**. Nothing creates a shot of several cats until S5,
  and by then storage, backup, the use case and Encounters all handle one.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| S1 | Photos know their shot | `EncounterPhoto.shotId`, stored in database v5 by a hand-written migration proven on every kind of photo; nothing sets it yet. | safe | ~600 | — | merged |
| S2 | Backup format 6 carries shots | Photo records carry `shotId`, the archive says format 6, older formats read as one shot per photo, and export → import keeps a shot whole. | safe | ~450 | S1 | in-review |
| S3 | Adding cats to a photo | `AddCatsToPhoto` copies the files and inserts every new cat in one transaction, with the location and analytics rules. | safe | ~550 | S1 | planned |
| S4 | Encounters shows one entry per shot | A shot packs as one entry with a cat-count badge, opens its first cat, and is selected and deleted as a whole, in the grid and the list. | safe | ~550 | S1 | planned |
| S5 | Counting cats in the coat sheet | **Several** turns the coat sheet into counting mode — tray, paw, **Save N cats** — and saves the shot through S3. | safe | ~600 | S2, S3, S4 | planned |
| S6 | On this photo on the detail screen | The row of a photo's cats, switching between them without stacking screens, and **+** to add a cat. | safe | ~550 | S3, S4 | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice S1 — Photos know their shot
- **In scope:** `EncounterPhoto.shotId: String?` with no default value, and `shot` (`shotId ?: id`); every
  construction site stating it (`LogPhoto`, `AttachPhoto`, `ImportPhotos`, `carriedPhoto`, the backup
  record mapping, test doubles and fixtures); `EncounterPhotoEntity.shotId` with an index; the entity
  mapper both ways; database v5 by `MigrationFrom4To5`; exported schema 5 and its asset copy; the
  migration tests of the spec (v4 → v5 on every kind of photo, with foreign keys on, v1/v2/v3 → v5 through
  the app's builder, the purge cascade at v5), each seen failing once; `data-model.md`.
- **Out of scope:** the archive format (S2); anything that writes a non-null `shotId`.
- **Ships safely because:** every row reads and writes `shotId = null`, which is what every photo is until S3.
- **Verified on a device:** a build of `main` with real data upgraded in place to this build; every cat and
  photo reads back.
- **Cleanup owed:** none.

### Slice S2 — Backup format 6 carries shots
- **In scope:** `EncounterPhotoRecord.shotId` (default null, for older records); `BACKUP_FORMAT_VERSION` 5;
  record mapping both ways; round trips carrying a non-null `shotId` (records, the zip, `BackupRestoreTest`
  export → wipe → import, twice); format 3, 4 and 5 archives reading as one shot per photo; a format 5 reader
  refusing format 6; `BackupMerge` tests for shots across phones; each seen failing once; `backup.md`.
- **Out of scope:** merge rules — none change.
- **Ships safely because:** until S5 every `shotId` is null, which a format 6 record writes exactly as format 5.
- **Verified on a device:** export and import in both directions between `main` and this build, the older
  build refusing the new archive; the same on the release APK.
- **Cleanup owed:** none.

### Slice S3 — Adding cats to a photo
- **In scope:** a `PhotoStorage` capability copying a stored photo and its thumbnail under a new name, with
  its Android implementation; the repository inserting several cats with their photos in one transaction
  while the source cat is live; `AddCatsToPhoto(sourceEncounterId, photoId, coats)` — what a new cat
  copies, `shotId` = the source photo's `shot`, files removed on any failure, the result saying which cats
  still need a fix; `cat_logged` per cat; purging one cat of a shot keeps the others' files; an undone
  gallery import keeps a cat added to its photo; `photos.md`, `location.md`, `analytics.md`.
- **Out of scope:** any screen.
- **Ships safely because:** nothing calls it until S5.
- **Cleanup owed:** none.

### Slice S4 — Encounters shows one entry per shot
- **In scope:** `EncounterGridPacker` treating the cats whose cover shares a shot as one photo; the entry
  state carrying its cats' ids, the first cat to open, and the count; the badge in the pair tile, the tile,
  the card and the list row; a tap opening the first cat; selection and delete by entry, the bar counting
  cats; accessibility naming the count; EN/RU strings; `browsing-cats.md`.
- **Out of scope:** the Places area list and the map, which stay per cat.
- **Ships safely because:** with no shot of several cats yet, every entry is one cat and looks as it does now.
- **Cleanup owed:** none.

### Slice S5 — Counting cats in the coat sheet
- **In scope:** **Several** in the coat sheet; the counting mode in `CounterState` — the tray, counts per
  face, the paw, **Save N cats**, `Tuning.SHOT_MAX_CATS`; saving the first coat on the photographed cat and
  the rest through `AddCatsToPhoto`, scheduling a fix for cats still without one; one message for a failed
  save; the sheet's existing edge rules in the new mode; accessibility; EN/RU strings; `coat.md`.
- **Out of scope:** the detail screen (S6).
- **Ships safely because:** it is the feature; storage, backup and Encounters already hold a shot.
- **Verified on a device:** a real shot of three cats exported, the app's data cleared, imported, and back as
  one tile with its badge.
- **Cleanup owed:** none.

### Slice S6 — On this photo on the detail screen
- **In scope:** the **On this photo** row for the photo on screen, its faces with the current cat ringed, or
  **Another cat on this photo** on a single cat's photo; tapping a cat replacing the screen; **+** adding one
  uncoated cat through `AddCatsToPhoto` and replacing the screen with it; the row following deletes; EN/RU
  strings; `encounter-detail.md`.
- **Out of scope:** moving a cat between shots.
- **Ships safely because:** it adds a control over data S3 already writes safely.
- **Cleanup owed:** none.

## Decision log

- 2026-09-26: **S1 merged** as #174. Merging main into it found a photo built without `shotId` in a test that
  had landed meanwhile (#177): the missing default did its job at compile time.

- 2026-09-26: **S2's archive format is 6, not 5.** While S1 waited to merge, #170 (location set by hand) took
  format 5. An app on format 5 would read a shot's archive and drop every `shotId`, so shots need their own
  number. The database version was untouched there, so S1's v5 stands.

- 2026-09-26: **S2 in review** as #176. No merge rule changed: `BackupMerge` already carries photos as whole
  rows. On the emulator, before the renumbering, the `main` build's format 4 archive imported into this build,
  this build's archive with a real shot came back whole after clearing the app, the `main` build refused it as
  too new, and the R8 release build exported and re-imported it with every `shotId`.

- 2026-09-26: **S1 in review** as #174 (~255 reviewable lines, 23 production). `/code-review` found 5, fixed 4; the
  gate's first round was green on all 14 mechanical criteria, the on-device upgrade awaits the owner's look.

- 2026-09-26: **S1's migration is hand-written.** The plan's `AutoMigration(4, 5)` made Room generate a rebuild of
  `encounter_photos` ending in a foreign key check, which throws on a photo row whose cat is gone and would stop
  the app at start-up (`aPhotoWhoseCatIsGoneDoesNotStopTheMigrationToFive` red against it). `MigrationFrom4To5`
  runs only `ADD COLUMN` and `CREATE INDEX`.

- 2026-09-26: owner approved the design and asked that export, import and the migration be proven not to
  break. The spec's five slices became six: storage (S1) and the archive format (S2) are separate, as in the
  many-photos epic, so each has its own review and its own on-device check. S4 lands before S5 so Encounters
  shows a shot as one entry before anything can create one.
