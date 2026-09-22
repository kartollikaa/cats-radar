# Location

Every encounter gets its coordinates asynchronously, after the row already exists. `CounterStore`
emits an `AttachLocation` effect right after `LogTally` returns; `CounterEffectHandler` hands the
encounter id to `WorkManagerLocationAttachScheduler`, which enqueues `AttachLocationWorker` — a
`CoroutineWorker` that calls the `AttachLocation` domain use case. That use case asks
`LocationPolicy` to resolve one of two rungs: a fresh GPS fix
(`FusedLocationProvider.getCurrentFix`, capped at `Tuning.LOCATION_TIMEOUT` = 8 s) or, failing
that, the last known fix if it is no older than `Tuning.LAST_KNOWN_MAX_AGE` (6 h); nothing usable
leaves the encounter at `NONE`. Running this as a WorkManager job rather than inline in the Store
means the update survives the app process dying mid-flight.

A resolved *current* fix does more than stamp its own encounter: it also backfills every other
`NONE` encounter that falls in the same outing (the session concept in `outings.md`), so cats
logged earlier in a walk — before GPS had a chance to settle — end up with the location the walk
eventually produced. A last-known fix never does this; it only updates the encounter it was
requested for.

## At the edges

A retry of the same worker (process death, WorkManager's own re-run policy) is idempotent:
`AttachLocation` re-reads the target first and returns immediately unless its `locationSource` is
still `NONE`, so an already-located row is neither re-stamped nor used to trigger another backfill
pass (*an already-located target is left untouched and never re-triggers backfill*).

Undo can race the fix: `getCurrentFix` may still be waiting when the user undoes the tap that
started it. The design spec describes undo as cancelling the worker outright, but the code does
not do that — `UndoLastTally` only soft-deletes the row, and the worker keeps running to
completion. What actually stops the encounter from being wrongly resurrected is the
`UPDATE ... WHERE deletedAt IS NULL` guard on `EncounterDao.attachLocation`: once a row is
soft-deleted, a fix that resolves afterward writes nothing (*a fix that resolves after the target
was undone does not resurrect it*).

Backfill only widens within a single outing. An encounter 45 minutes before or after the target —
past the 30-minute `SESSION_GAP` — belongs to a different outing and is left at `NONE` even though
a current fix was just obtained (*a current fix backfills NONE encounters in the same outing but
not an earlier one*; *an encounter from a later outing is never backfilled*). It also never
overwrites an encounter that already carries any non-`NONE` location, including one from an
earlier `LAST_KNOWN` resolution (*backfill never overwrites an encounter that already has
coordinates*), and it skips a soft-deleted encounter that would otherwise fall inside the outing's
time span even though `SessionSplitter` never sees it — deletion is filtered separately at the
candidate-list stage (*a soft-deleted encounter inside the outing is never backfilled*).

No permission and no Play Services both degrade the same way: `FusedLocationProvider` checks
`ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` up front and returns `null` without throwing when
neither is granted; a genuine Play Services failure (missing module, a permission revoked
mid-call) is caught and also returns `null`, with only `CancellationException` allowed through.
Either way `LocationPolicy` simply falls through its rungs to `NONE` — a tally never shows a
location error.

## Fields stamped

`locationSource` records which rung produced the value: `CURRENT_FIX`, `LAST_KNOWN`,
`BACKFILLED`, or `NONE` from this code path (`EXIF` exists in the domain enum for the photo flow,
which isn't built yet, so nothing currently writes it). `locationFixedAt` is the fix's own
timestamp — when GPS actually produced the reading — and is deliberately separate from
`occurredAt`, the tally's own timestamp; the two diverge whenever a fix resolves late or is
backfilled from a different tap's fix. `geohash` is encoded at `Tuning.GEOHASH_PRECISION`
(8 characters); `placeCellId` is a coarser, independent prefix at `Tuning.PLACE_CELL_PRECISION`
(6 characters) — the two precisions are asserted as distinct in `AttachLocationTest` (*geohash and
placeCellId use their own distinct precisions*).

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/location/LocationPolicy.kt`, `LocationFix.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AttachLocation.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/geo/Geohash.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/platform/LocationProvider.kt`
- `data/src/androidMain/kotlin/dev/catsradar/data/platform/FusedLocationProvider.android.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt` (`attachLocation`)
- `app/src/main/kotlin/dev/catsradar/app/worker/AttachLocationWorker.kt`,
  `WorkManagerLocationAttachScheduler.kt`

## Not handled yet

Nothing yet creates or resolves a `PlaceCell` from an attached location — the model, its Room
entity/DAO, and `PlaceCellRepository` all exist and are unit-tested, but they are not bound in
`app/di/DataModule.kt` and no use case calls `upsert`. Reverse geocoding (§4.4 of the design spec)
and the region hierarchy built on it are entirely unbuilt. EXIF-derived location, part of the
photo flow, is likewise not implemented.
