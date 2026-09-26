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

A fix is dated on the phone's own clock, the one every encounter is stamped with. Android dates a
fix by its source, which for satellites is their clock, and a phone set by hand disagrees with it;
the fix's age on the uptime clock is what places it (*a fix is dated on the phone's clock by how long
ago it was taken*).

A fix that does not say how precise it is carries no accuracy, rather than the 0 Android reports for
it, which would read as a perfect one (*a location that does not say how precise it is has no
accuracy rather than a perfect one*). A cat stamped with such a fix gets its coordinates and no
accuracy; a walk's route leaves it out.

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

The same write lands only on a row still at `NONE`, so the cat's location can arrive another way
during that wait too — set by hand (below) — and the fix leaves it alone. `AttachLocation` then
backfills nothing either, since its own write did not land (*a fix landing after the cat was placed
by hand neither replaces the point nor backfills the outing*; `EncounterDaoAttachLocationTest`,
*attachLocationLeavesACatThatAlreadyHasALocationAsItWas*). The check is the write itself rather than
the read before it, because the read comes before a wait of up to `LOCATION_TIMEOUT`.

Backfill only widens within a single outing. An encounter far enough before or after the
target to be past `SESSION_GAP` belongs to a different outing, and is left at `NONE` even though
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

## Following a walk

While a walk records its route, the same provider streams fixes rather than answering once:
`trackFixes` asks for high-accuracy updates every `Tuning.TRACK_FIX_INTERVAL` for as long as it is
collected, and gives none without permission. A permission revoked between the check and the request
ends the stream rather than the app. Which fixes join the route is `RecordTrackPoint`'s decision
(`data-model.md`), and when the stream runs is walking mode's (`walking-mode.md`).

## Fields stamped

`locationSource` records which rung produced the value: `CURRENT_FIX`, `LAST_KNOWN`,
`BACKFILLED`, or `NONE` from this code path; `EXIF` belongs to the photo flow, where a photo's own
metadata beats anything the phone could measure later; `MANUAL` is a point set by hand. `locationFixedAt` is the fix's own
timestamp — when GPS actually produced the reading — and is deliberately separate from
`occurredAt`, the tally's own timestamp; the two diverge whenever a fix resolves late or is
backfilled from a different tap's fix. `geohash` is encoded at `Tuning.GEOHASH_PRECISION`
(8 characters); `placeCellId` is a coarser, independent prefix at `Tuning.PLACE_CELL_PRECISION`
(6 characters) — the two precisions are asserted as distinct in `AttachLocationTest` (*geohash and
placeCellId use their own distinct precisions*).

## Set by hand

A cat with no location can be given one: `SetLocationByHand(encounterId, lat, lon)` stamps the point
as `MANUAL` (*a cat with no location gets the point as set by hand, with no accuracy, at the time it
was set*). Nothing measured it, so it carries no accuracy; `locationFixedAt` is when it was saved,
the moment the app learned where the cat was. Its place cell is remembered like any other point's,
so the geocoder names it (*the point's place cell is remembered for the geocoder to name*).

It is only ever a first location. A cat that already has one keeps it, a deleted cat gets nothing,
and a point off the globe is refused, each with `false` and nothing written. The point is this cat's
alone: no other cat of its outing is backfilled from it. Nothing on screen offers it yet.

`closestLocatedInTime` picks the live located cat logged nearest in time to a given one, the earlier
on a tie — the place a cat with no location was most likely seen (`ClosestLocatedTest`).

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/location/LocationPolicy.kt`, `LocationFix.kt`,
  `ClosestLocated.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AttachLocation.kt`, `SetLocationByHand.kt`,
  `RecordWalk.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/geo/Geohash.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/platform/LocationProvider.kt`
- `data/src/androidMain/kotlin/dev/catsradar/data/platform/FusedLocationProvider.android.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt` (`attachLocation`)
- `app/src/main/kotlin/dev/catsradar/app/worker/AttachLocationWorker.kt`,
  `WorkManagerLocationAttachScheduler.kt`

## Turning it into a place name

A fix is coordinates, and coordinates are not a place. `AttachLocation` hands its geohash to
`PlaceCells.remember`, which creates the cell the point falls in so a geocoder can name it later —
see `places.md`. Every other path that produces coordinates does the same, so the fix is not special
here.
