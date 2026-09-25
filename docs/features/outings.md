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
encounters themselves — grouping the Encounters list, for instance — use `groupByOuting()` directly
rather than re-deriving the boundary, so it has exactly one implementation.

## At the edges

Both functions only ever exclude soft-deleted encounters — deleted rows count neither toward an
outing's size nor its span (*a soft-deleted encounter is excluded from count and duration*;
*groupByOuting excludes a soft-deleted encounter from its outing*). A gap of exactly `SESSION_GAP`
stays inside the same outing; one millisecond more starts a new one — the boundary is `>`, not `>=`
(*a gap exactly equal to SESSION_GAP stays one session*; *a gap one millisecond over SESSION_GAP
starts a new session*). Both sort their own input — by time, and encounters logged at the same
instant by id — so callers don't need to pre-sort, and neither pre-sorting nor the input's order
changes the result (*unsorted input yields the same sessions as sorted input*; *encounters logged at
the same instant keep one order whatever the input order*). Empty input produces an empty list, not a
single empty session.

Because outings are derived and not stored, anything that needs "which outing does this encounter
belong to" has to run the splitter over the live encounter list itself rather than reading a
foreign key — `AttachLocation`'s backfill (see `location.md`) and the Encounters list's grouping
(see `browsing-cats.md`) both do this today. That keeps the two concepts (an encounter's own
fields, and the grouping over them) from ever going stale relative to each other, at the cost of
recomputing the split from scratch on every call.

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/session/SessionSplitter.kt` (`split`,
  `groupByOuting`)
- `domain/src/commonMain/kotlin/dev/catsradar/domain/model/Session.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/Tuning.kt` (`SESSION_GAP`)
- consumed by `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AttachLocation.kt`
  (`split`) and
  `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/EncountersStateMapper.kt`
  (`groupByOuting`)

## Not handled yet

Everything statistics-facing that the design spec builds on top of outings — rate-eligible
sessions, overall/session rate, a live "current outing" on the Counter screen, best session, the
Outings count and total active time (§5) — is specified but not implemented; there is no
`StatsCalculator` and no Statistics screen yet. `SESSION_GAP` is described in the spec as "a
constant, not a setting, in v1," which the code matches: it's a default parameter on `split()`,
not read from any settings store.
