# Outings

An "outing" (the design spec calls it a "Session", §3.5) is never stored — it's recomputed on
demand by `SessionSplitter` from whatever non-deleted encounters exist. Sort by `occurredAt`; a gap
strictly greater than `Tuning.SESSION_GAP` between two consecutive encounters starts a
new outing. `groupByOuting()` is where that boundary is actually computed — it returns each
outing's own encounters (oldest first within each outing); `split()` is defined in terms of it,
mapping each group to a `Session` (`count`, `start` — first encounter's time, `end` — last
encounter's time, `duration = end - start`), so a single encounter is a zero-duration, one-cat
outing, and "duration" measures first-cat-to-last-cat, not any independent notion of when a walk
began or ended. Callers that only need the aggregate use `split()`; callers that need the
encounters themselves — grouping the Encounters list, for instance — use `groupByOuting()`
directly; `outingOf()` returns the one outing holding a given live encounter, built on
`groupByOuting()` rather than re-deriving the boundary, so it has exactly one implementation. What
statistics build on outings — rates, the best outing, the outing in progress, the Outings count and
active time — is in [statistics.md](./statistics.md).

## The window around a set of cats

`outingWindow(encounters, shown)` answers "which outings are these cats in, and what is on either side".
Its `cats` are every live cat from the oldest to the newest outing that holds a cat in `shown`, newest
first — so an outing a delete split in two stays whole for as long as a cat of each half is in `shown`,
while `groupByOuting()` itself keeps splitting it. A cat logged into one of those outings, or one that
merges the next outing into them, is in the window too. `newer` and `older` are the outings just outside
it, oldest first, or null at either end of history; `newerLanding` and `olderLanding` are the cats of
each nearest to the window. Cats logged at the same instant keep one order — by id — whatever
order the input list gives them. No live cat in `shown` means no window (`OutingWindowTest`).

## At the edges

`split()` and `groupByOuting()` only ever exclude soft-deleted encounters — deleted rows count
neither toward an outing's size nor its span (*a soft-deleted encounter is excluded from count and
duration*; *groupByOuting excludes a soft-deleted encounter from its outing*). A gap of exactly
`SESSION_GAP` stays inside the same outing; one millisecond more starts a new one — the boundary is
`>`, not `>=` (*a gap exactly equal to SESSION_GAP stays one session*; *a gap one millisecond over
SESSION_GAP starts a new session*). `split()` and `groupByOuting()` sort their own input, so
callers don't need to pre-sort, and doing so doesn't change the result (*unsorted input yields the
same sessions as sorted input*). Empty input produces an empty list, not a single empty session.

Because outings are derived and not stored, anything built on outings — the outings themselves, or
which one a cat is in — has to run the splitter over the live encounter list itself rather than
reading a foreign key — `AttachLocation`'s backfill (`split`, see `location.md`), `StatsCalculator`
(`groupByOuting`, `split`), the Encounters list's grouping and headers (`groupByOuting`, see
`browsing-cats.md`), the map's outing focus (`outingOf`, in `MapStateMapper`) and walk tracks
(`outingOf`, in `ObserveOutingTracks`) all do this today. That keeps the two concepts (an
encounter's own fields, and the grouping over them) from ever going stale relative to each other,
at the cost of recomputing the split from scratch on every call.

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/session/SessionSplitter.kt` (`split`,
  `groupByOuting`, `outingOf`)
- `domain/src/commonMain/kotlin/dev/catsradar/domain/session/OutingWindow.kt` (`outingWindow`)
- `domain/src/commonMain/kotlin/dev/catsradar/domain/model/Session.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/Tuning.kt` (`SESSION_GAP`)
- consumed by `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AttachLocation.kt`
  (`split`), `domain/src/commonMain/kotlin/dev/catsradar/domain/stats/StatsCalculator.kt`
  (`groupByOuting`, `split`),
  `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/EncountersStateMapper.kt`
  (`groupByOuting`),
  `presentation/src/commonMain/kotlin/dev/catsradar/presentation/map/MapStateMapper.kt`
  (`outingOf`) and
  `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/ObserveOutingTracks.kt` (`outingOf`)

## Not handled yet

`SESSION_GAP` is a constant, not a setting, as the design spec has it for v1: a default parameter on
`split()` and `groupByOuting()`, read from no settings store.
