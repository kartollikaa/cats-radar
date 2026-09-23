# Data model

`Encounter` is the one row per logged cat; `PlaceCell` is a reverse-geocode cache keyed by a
coarser geohash prefix, modeled but not yet populated by any code path (see `location.md`). Both
are plain, Room-agnostic `data class`es in `:domain`; `:data` mirrors each with an `@Entity`
(`EncounterEntity`, `PlaceCellEntity`) and a pair of mapper functions (`toDomain()`/`toEntity()`)
so the persistence shape and the domain shape can diverge without either layer leaking into the
other.

`Encounter`'s fields fall into a few groups: identity (`id`, `deviceId`); when and how it happened
(`occurredAt`, `tzOffsetMinutes`, `kind`, `origin`); the cat (`coat`, the only field editable after
creation, though nothing yet edits it); photo fields (`photoPath`, `thumbPath`, `galleryUri`,
`sourceDigest`) that stay `null` until the photo flow exists; location (`lat`, `lon`,
`accuracyMeters`, `locationSource`, `locationFixedAt`, `geohash`, `placeCellId`, covered in
`location.md`); and row lifecycle (`createdAt`, `updatedAt`, `deletedAt`). `tzOffsetMinutes` is
the UTC offset at the moment the encounter happened, not the device's offset now — it is what lets
"today" and streak calculations stay correct for an encounter logged while travelling (see
`docs/rules/date-time.md`).

## At the edges

Deletion is soft: `deletedAt` is a nullable timestamp, and every read of live rows (`observeAll`,
`observeById`, `findBySourceDigest`) filters `WHERE deletedAt IS NULL`. Only two reads see
soft-deleted rows: `loadEvery` for the backup merge and `loadDeletedBefore` for the purge.
`softDelete` itself is guarded the same way in reverse — its `UPDATE` only fires
`WHERE deletedAt IS NULL`, so calling it twice cannot restart a row's purge clock by overwriting
an earlier `deletedAt` with a later one (`EncounterDaoResilienceTest`,
*reSoftDeletingAnAlreadyDeletedRowDoesNotRestartItsPurgeClock*). `undoDelete` sets `deletedAt`
back to `null` unconditionally — it is the one place a `deletedAt` write isn't guarded, because
undo is meant to resurrect the row.

Every enum column (`EncounterKind`, `EncounterOrigin`, `LocationSource`, `CatCoat`, `PlaceStatus`)
is stored by its `name`, never its ordinal — reordering the enum's declaration must never change
what a stored row means. Reading back a name the current app version doesn't recognize (a
hand-edited row, or one written by a newer app version with a new member) degrades to a specific
fallback per enum rather than throwing: `EncounterKind` → `TALLY`, `EncounterOrigin` → `APP`,
`LocationSource` → `NONE`, `CatCoat` → `null`, `PlaceStatus` → `FAILED` (`EnumConvertersTest`, one
`unrecognized*NameDegradesTo*` test per enum). `PlaceStatus` in particular falls back to `FAILED`
rather than `PENDING`, so that an unrecognized status can't make the (not-yet-built) geocode
worker retry it forever.

A corrupt row doesn't take the rest of the list down with it, either. `tzOffsetMinutes` outside
±18 hours — `Encounter`'s own `init` block enforces that range and throws — is clamped at the
mapper (`EncounterEntity.toDomain()`), not rejected, specifically because throwing there would
fail every row read alongside the bad one, not just the bad one
(*toDomainClampsAnOutOfRangeTzOffsetInsteadOfThrowing*; end-to-end through the repository:
*aRowWithACorruptTzOffsetOrEnumIsDegradedNotLostAndDoesNotBlockOtherRows*). Both mapper directions
(`toEntity().toDomain()` and `toDomain().toEntity()`) are asserted to round-trip every field
exactly for a healthy row.

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/model/Encounter.kt`, `PlaceCell.kt`,
  `LocationStamp.kt`, `CatCoat.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterEntity.kt`, `PlaceCellEntity.kt`,
  `EnumConverters.kt`, `InstantConverters.kt`, `CatsDatabase.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterMapper.kt`,
  `PlaceCellMapper.kt`, `EncounterRepositoryImpl.kt`, `PlaceCellRepositoryImpl.kt`

Schema is exported to `data/schemas/dev.catsradar.data.db.CatsDatabase/1.json`;
`CatsDatabaseMigrationTest` opens that committed v1 baseline to prove the migration-test harness
itself works — there is no v2 yet, so no actual migration path exists to test.

## Not handled yet

Backup export and import (§3.1, §4.7 of the design spec) are specified but unbuilt; nothing reads
or writes an `Encounter` outside the app's own database yet.

## Purging

A soft-deleted cat is not kept forever. `PurgeDeletedWorker` runs daily, **while the device is
idle**, and removes rows whose `deletedAt` is older than `Tuning.PURGE_AFTER`, along with their
photo files.

The files go **before** the rows: a row deleted first would leave photos nothing points at, and
nothing would ever look for them again.

This needed its own query. `observeAll()` filters soft-deleted rows out — correctly, for every other
caller — so the purge cannot find its own targets through it. `loadDeletedBefore` selects them
directly; without it the rows would vanish and the photos would stay on disk forever.
