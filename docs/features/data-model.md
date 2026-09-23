# Data model

`Encounter` is the one row per logged cat; `PlaceCell` is a reverse-geocode cache keyed by a coarser
geohash prefix (see `places.md`). Both are plain, Room-agnostic `data class`es in `:domain`; `:data`
mirrors each with an `@Entity` (`EncounterEntity`, `PlaceCellEntity`) and a pair of mapper functions
(`toDomain()`/`toEntity()`) so the persistence shape and the domain shape can diverge without either
layer leaking into the other.

`Encounter`'s fields fall into a few groups: identity (`id`, `deviceId`); when and how it happened
(`occurredAt`, `tzOffsetMinutes`, `kind`, `origin`); the cat (`coat`, the only field the user can
edit after creation, see `coat.md`); photo fields (`photoPath`, `thumbPath`, `galleryUri`,
`sourceDigest`, covered in `photos.md`); location (`lat`, `lon`, `accuracyMeters`, `locationSource`,
`locationFixedAt`, `geohash`, `placeCellId`, covered in `location.md`); and row lifecycle
(`createdAt`, `updatedAt`, `deletedAt`). `tzOffsetMinutes` is the UTC offset at the moment the
encounter happened, not the device's offset now — it is what lets "today" and streak calculations
stay correct for an encounter logged while travelling (see `docs/rules/date-time.md`). `id` must be
unique across devices, not just on this one: a backup import reconciles rows by it (see
`backup.md`).

## At the edges

Deletion is soft: `deletedAt` is a nullable timestamp, and every read of live rows (`observeAll`,
`observeById`, `findBySourceDigest`) filters `WHERE deletedAt IS NULL`. Only two reads see
soft-deleted rows: `loadEvery` for the backup merge (see `backup.md`) and `loadDeletedBefore` for
the purge. `softDelete` itself is guarded the same way in reverse — its `UPDATE` only fires
`WHERE deletedAt IS NULL`, so calling it twice cannot restart a row's purge clock by overwriting
an earlier `deletedAt` with a later one (`EncounterDaoResilienceTest`,
*reSoftDeletingAnAlreadyDeletedRowDoesNotRestartItsPurgeClock*). Two writes clear `deletedAt` on
purpose, unguarded: `undoDelete`, because undo is meant to resurrect the row, and a backup import
whose copy of a cat deleted here was edited after the deletion (see `backup.md`).

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
with a foreign key to its walk that deletes the points with it. Nothing records walks yet; the walk
mode that will is its own slice of the Map epic.

- **One walk at a time.** Starting a walk while one is on returns that walk rather than opening a
  second: the check and the insert are one transaction (`WalkDao.startIfNoneOpen`), so two starts
  racing each other still make one walk.
- **Ending is final.** Ending only touches a walk that is on, so ending twice keeps the first end,
  and a clock set back before the start ends the walk at its start rather than before it. The walk's
  `updatedAt` records when it was changed, which is not always the moment it ended.
- **A route keeps only fixes that say something.** `RecordTrackPoint` leaves out a fix that is too
  rough to trust (`Tuning.TRACK_MAX_ACCURACY_METERS`), older than the walk or than the route's last
  point, or nearer that point than `Tuning.TRACK_MIN_STEP_METERS` or than either fix's accuracy. A
  phone standing still wanders by about its accuracy, and would otherwise pile up points in one spot.
  Fixes take turns, since each is measured against the point the one before it kept.
- **Distance is great-circle** (`trackLengthMeters`), on the Earth's mean radius: within half a
  percent of the Earth's real shape, which on a step is far less than a phone fix's own error.
- **A walk does not define an outing.** Outings stay derived from the cats alone (`outings.md`).
- **Walks travel in backups**, merged so that no import shortens a route (`backup.md`).

The database went from version 1 to 2 for these two tables, by an automatic migration that only adds
them; `CatsDatabaseMigrationTest` opens a version-1 database with a cat in it, migrates, and checks
the cat is still there.

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/model/Encounter.kt`, `PlaceCell.kt`,
  `LocationStamp.kt`, `CatCoat.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterEntity.kt`, `PlaceCellEntity.kt`,
  `EnumConverters.kt`, `InstantConverters.kt`, `CatsDatabase.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterMapper.kt`,
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
nothing would ever look for them again.

This needed its own query. `observeAll()` filters soft-deleted rows out — correctly, for every other
caller — so the purge cannot find its own targets through it. `loadDeletedBefore` selects them
directly; without it the rows would vanish and the photos would stay on disk forever.
