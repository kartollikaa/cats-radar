# Data model

`Encounter` is the one row per logged cat; `PlaceCell` is a reverse-geocode cache keyed by a coarser
geohash prefix (see `places.md`). Both are plain, Room-agnostic `data class`es in `:domain`; `:data`
mirrors each with an `@Entity` (`EncounterEntity`, `PlaceCellEntity`) and a pair of mapper functions
(`toDomain()`/`toEntity()`) so the persistence shape and the domain shape can diverge without either
layer leaking into the other.

`Encounter`'s fields fall into a few groups: identity (`id`, `deviceId`); when and how it happened
(`occurredAt`, `tzOffsetMinutes`, `kind`, `origin`); the cat (`coat`, which the user can change
after creation, see `coat.md`); its photos (`photos`, covered in `photos.md` and `import.md`);
location (`lat`, `lon`, `accuracyMeters`, `locationSource`, `locationFixedAt`, `geohash`,
`placeCellId`, covered in `location.md`); and row lifecycle (`createdAt`, `updatedAt`, `deletedAt`).
`tzOffsetMinutes` is the UTC offset at the moment the encounter happened, not the device's offset
now — it is what lets "today" and streak calculations stay correct for an encounter logged while
travelling (see `docs/rules/date-time.md`). `id` must be unique across devices, not just on this
one: a backup import reconciles rows by it (see `backup.md`).

## Photos

`photos` is a list of `EncounterPhoto`, oldest first; the first is the cat's **cover**, what a tile, a
pair row and the coat prompt show, and a cat has a photo exactly when the list is not empty. Each photo
carries the app's copy and thumbnail (`photoPath`, `thumbPath`), the camera original the app saved to
the gallery (`galleryUri`), the gallery item a picked photo came from (`sourceMediaUri`), the digest of
the bytes the source handed over (`sourceDigest`), the install that recorded those two links
(`deviceId`), and when it joined the cat (`addedAt`).

Every photo also names its **shot** (`shotId`), so the cats of one photo can be found together: it is
never null, and it is the same on every row of one shot — the id of the shot's first photo row. A photo
of one cat is a shot of its own and names itself. A row joining a shot takes the shot's id and never
edits the rows already in it. Every photo taken, attached or imported names itself, and one read from a
backup keeps the shot it was written with (see `backup.md`); nothing writes a shot of several cats yet
(`LogPhotoTest`, `ImportPhotosTest`, *… starts a shot of its own*; *eachWayAPhotoIsWrittenKeepsItsShot*).

Each photo is a row of `encounter_photos`, keyed by its own `id`, with a foreign key to its cat that
deletes the photo rows with the cat. Every read returns a cat with its photos (`EncounterWithPhotos`),
ordered as above whatever order they were written in (`EncounterDaoPhotosTest`). Inserting a cat writes
it and all its photos in one transaction, so a photo that cannot be written leaves no cat either
(*aCatWhosePhotoCannotBeWrittenIsNotStoredEither*). A full-row `update` touches only the cat's own
table, so it can never drop or replace a photo (`EncounterDaoRestorePhotoTest`).

An archive's encounter record still carries at most one photo in five fields; it is read by `carriedPhoto`,
the rule the migration below uses: the photo takes the cat's id, install and creation time, and a record
without a copy carries none, whatever its other four fields hold (`CarriedPhotoTest`).

## At the edges

Deletion is soft: `deletedAt` is a nullable timestamp, and every read of live rows (`observeAll`,
`observeById`, `findBySourceDigest`) filters `WHERE deletedAt IS NULL`. Only two reads see
soft-deleted rows: `loadEvery` for the backup merge (see `backup.md`) and `loadDeletedBefore` for
the purge. `softDelete` itself is guarded the same way in reverse — its `UPDATE` only fires
`WHERE deletedAt IS NULL`, so calling it twice cannot restart a row's purge clock by overwriting
an earlier `deletedAt` with a later one (`EncounterDaoResilienceTest`,
*reSoftDeletingAnAlreadyDeletedRowDoesNotRestartItsPurgeClock*). `addPhoto` is guarded both ways
at once: it adds the photo row and stamps only `updatedAt`, and only on a live cat, checked in the same
transaction, so giving a cat a photo can neither bring back a deleted one nor touch the photos it has
(`EncounterDaoAttachPhotoTest`). A backup's photos are added only where their id
is not here yet, leaving every cat's `updatedAt` alone (`EncounterDaoRestorePhotoTest`). `setCoat`
writes only the `coat` column and `updatedAt`,
`WHERE deletedAt IS NULL`, so changing a cat's coat can neither resurrect a deleted row nor undo a
photo or a location attached a moment earlier (`EncounterDaoSetCoatTest`). Two writes clear
`deletedAt` on purpose, unguarded:
`undoDelete`, because undo is meant to resurrect the row, and a backup import whose copy of a cat
deleted here was edited after the deletion (see `backup.md`).

Undoing a batch is guarded, unlike `undoDelete`. `softDeleteAll` stamps every row of a batch with one
`deletedAt`, and `undoDeleteAll` clears only rows still carrying exactly that instant, so undoing a
batch never resurrects a row that was deleted some other time, even if its id was in the batch
(`EncounterDaoTest`, *undoDeleteAllRestoresOnlyTheRowsDeletedAtTheBatchInstant*). Both run as one
Room transaction of per-row statements rather than one `IN (:ids)`: SQLite before 3.32, which is
what API 29 and 30 ship, allows at most 999 bound variables in a statement, and a transaction still
makes the batch all-or-nothing and invalidates `observeAll` once rather than once per row.

Every enum column (`EncounterKind`, `EncounterOrigin`, `LocationSource`, `CatCoat`, `PlaceStatus`)
is stored by its `name`, never its ordinal — reordering the enum's declaration must never change
what a stored row means. Reading back a name the current app version doesn't recognize (a
hand-edited row, or one written by a newer app version with a new member) degrades to a specific
fallback per enum rather than throwing: `EncounterKind` → `TALLY`, `EncounterOrigin` → `APP`,
`LocationSource` → `NONE`, `CatCoat` → `null`, `PlaceStatus` → `FAILED` (`EnumConvertersTest`, one
`unrecognized*NameDegradesTo*` test per enum). `PlaceStatus` in particular falls back to `FAILED`
rather than `PENDING`, so that an unrecognized status can't make the geocode worker retry it
forever.

A corrupt row doesn't take the rest of the list down with it, either. `tzOffsetMinutes` outside
±18 hours — `Encounter`'s own `init` block enforces that range and throws — is clamped at the
mapper (`EncounterEntity.toDomain()`), not rejected, specifically because throwing there would
fail every row read alongside the bad one, not just the bad one
(*toDomainClampsAnOutOfRangeTzOffsetInsteadOfThrowing*; end-to-end through the repository:
*aRowWithACorruptTzOffsetOrEnumIsDegradedNotLostAndDoesNotBlockOtherRows*). Both mapper directions
(`toEntity().toDomain()` and `toDomain().toEntity()`) are asserted to round-trip every field
exactly for a healthy row.

## Walks

A `Walk` is a stretch of time the user chose to be out walking: a start, and an end once it is over.
Its route is a list of `TrackPoint`s, each a fix with its time and accuracy, kept in `track_points`
with a foreign key to its walk that deletes the points with it. Walking mode opens and ends walks,
and records their routes while location is allowed (`walking-mode.md`).

- **One walk at a time.** Starting a walk while one is on returns that walk rather than opening a
  second: the check and the insert are one transaction (`WalkDao.startIfNoneOpen`), so two starts
  racing each other still make one walk.
- **Ending is final.** Ending only touches a walk that is on, so ending twice keeps the first end,
  and a clock set back before the start ends the walk at its start rather than before it. The walk's
  `updatedAt` records when it was changed, which is not always the moment it ended.
- **A route keeps only fixes that say something.** `RecordTrackPoint` leaves out a fix that is too
  rough to trust (`Tuning.TRACK_MAX_ACCURACY_METERS`) or does not say how rough it is, older than the
  walk or than the route's last point, or nearer that point than `Tuning.TRACK_MIN_STEP_METERS` or
  than either fix's accuracy. A phone standing still wanders by about its accuracy, and would
  otherwise pile up points in one spot.
  Fixes take turns, since each is measured against the point the one before it kept.
- **Distance is great-circle** (`trackLengthMeters`), on the Earth's mean radius: within half a
  percent of the Earth's real shape, which on a step is far less than a phone fix's own error.
- **A walk does not define an outing.** Outings stay derived from the cats alone (`outings.md`).
- **Walks travel in backups**, merged so that no import shortens a route (`backup.md`).

The database went from version 1 to 2 for these two tables, by an automatic migration that only adds
them; `CatsDatabaseMigrationTest` opens a version-1 database with a cat in it, migrates, and checks
the cat is still there.

Version 3 adds `sourceMediaUri`, the gallery item a picked photo came from, by the same kind of
migration: a nullable column, so every cat already stored has none
(`CatsDatabaseMigrationTest.versionTwoBecomesThreeKeepingEveryCatWithNoPickedGalleryItem`).

Version 4 moves photos out of the cat's row, by a hand-written migration (`MigrationFrom3To4`), since an
automatic one cannot move data before it drops a column. In one transaction it creates
`encounter_photos`, copies each cat's photo into it by the `carriedPhoto` rule (a cat without a copy gets
no row), drops the digest index, then drops the five columns with `ALTER TABLE … DROP COLUMN`. It does
not rebuild the table the way Room's own migrations do: a rebuild drops `encounters`, and wherever
foreign keys are on that `DROP TABLE` first deletes every cat and the cascade takes every photo just
copied. Room runs migrations before it turns foreign keys on, so that never fires today, and
`PhotosMigrationTest` runs the migration with them on to keep it so
(*theMigrationKeepsEveryPhotoOnAConnectionWithForeignKeysOn*). The same class migrates a version 3 database
holding every kind of photo a cat could have — a camera original, a picked item, no thumbnail, a deleted
cat, another install's cat — and opens the result with the app's own builder, and brings a photographed
cat from versions 1 and 2 through every migration in between.

Version 5 adds `shotId` and its index to `encounter_photos`, by a hand-written migration
(`MigrationFrom4To5`) that runs only those two statements, so every photo already stored starts a shot of
its own. Room's own auto-migration would rebuild the table and then check its foreign keys, which throws on
a photo row whose cat is gone and would stop the app at start-up. Such a row, which no read reaches, comes
through as it was (`CatsDatabaseMigrationTest.aPhotoWhoseCatIsGoneDoesNotStopTheMigrationToFive`); the purge
never finds it, since it looks for photos through their deleted cats.
`CatsDatabaseMigrationTest.versionFourBecomesFiveKeepingEveryCatAndPhotoWithNoShot` migrates every kind of
photo a cat can have and finds each cat and photo unchanged, with no shot.

Version 6 makes `shotId` NOT NULL and fills it with the row's own id wherever it was null, by a
hand-written migration (`MigrationFrom5To6`). SQLite cannot add NOT NULL to an existing column, so this one
rebuilds `encounter_photos` — copying every row into a new table, dropping the old, renaming the new and
recreating its three indices — without the foreign key check Room's own rebuild ends in, so a photo row
whose cat is gone still comes through. Nothing references `encounter_photos`, so dropping it takes no other
row with it (`CatsDatabaseMigrationTest.versionFiveBecomesSixNamingEveryPhotosShot`). The app's own builder
opens a version 5 file with such a row, and brings a photographed cat from versions 1, 2 and 3 to 6 naming
its own shot (`PhotosMigrationTest`).

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/model/Encounter.kt`, `EncounterPhoto.kt`,
  `PlaceCell.kt`, `LocationStamp.kt`, `CatCoat.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterEntity.kt`, `EncounterPhotoEntity.kt`,
  `Migrations.kt`, `PlaceCellEntity.kt`,
  `EnumConverters.kt`, `InstantConverters.kt`, `CatsDatabase.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterMapper.kt`, `CarriedPhoto.kt`,
  `PlaceCellMapper.kt`, `EncounterRepositoryImpl.kt`, `PlaceCellRepositoryImpl.kt`
- Walks: `domain/.../model/Walk.kt`, `domain/.../geo/Distance.kt`, the use cases `StartWalk.kt`,
  `EndWalk.kt`, `RecordTrackPoint.kt`; `data/.../db/WalkEntity.kt`, `WalkDao.kt`, `TrackPointDao.kt`, and
  `data/.../repository/WalkRepositoryImpl.kt`

Each schema version is exported to `data/schemas/dev.catsradar.data.db.CatsDatabase/<version>.json`,
with a copy in the test assets that `SchemaAssetSyncTest` keeps identical to the export.

## Purging

A soft-deleted cat is not kept forever. `PurgeDeletedWorker` runs periodically, **while the device
is idle**, and removes rows whose `deletedAt` is older than `Tuning.PURGE_AFTER`, along with their
photo files.

The files go **before** the rows: a row deleted first would leave photos nothing points at, and
nothing would ever look for them again. The photo rows go with their cats, by the foreign key's cascade
(`EncounterDaoPhotosTest`, *aPurgeTakesEveryPhotoRowOfItsCatsWithThem*).

This needed its own query. `observeAll()` filters soft-deleted rows out — correctly, for every other
caller — so the purge cannot find its own targets through it. `loadDeletedBefore` selects them
directly; without it the rows would vanish and the photos would stay on disk forever.
