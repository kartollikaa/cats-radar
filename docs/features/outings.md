# Outings

An "outing" (the design spec calls it a "Session", §3.5) is never stored — it's recomputed on
demand by `SessionSplitter.split()` from whatever non-deleted encounters exist. Sort by
`occurredAt`; a gap strictly greater than `Tuning.SESSION_GAP` (30 minutes) between two
consecutive encounters starts a new outing. Each resulting `Session` carries `count`, `start`
(first encounter's time), `end` (last encounter's time), and `duration = end - start` — so a
single encounter is a zero-duration, one-cat outing, and "duration" measures first-cat-to-last-cat,
not any independent notion of when a walk began or ended.

## At the edges

The split only ever excludes soft-deleted encounters — deleted rows count neither toward an
outing's size nor its span (*a soft-deleted encounter is excluded from count and duration*). A gap
of exactly `SESSION_GAP` stays inside the same outing; one millisecond more starts a new one — the
boundary is `>`, not `>=` (*a gap exactly equal to SESSION_GAP stays one session*; *a gap one
millisecond over SESSION_GAP starts a new session*). The splitter sorts its own input, so callers
don't need to pre-sort, and doing so doesn't change the result (*unsorted input yields the same
sessions as sorted input*). Empty input produces an empty list, not a single empty session.

Because outings are derived and not stored, anything that needs "which outing does this encounter
belong to" — today, that's only `AttachLocation`'s backfill (see `location.md`) — has to run
`SessionSplitter.split()` over the live encounter list itself rather than reading a foreign key.
That keeps the two concepts (an encounter's own fields, and the grouping over them) from ever
going stale relative to each other, at the cost of recomputing the split from scratch on every
call.

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/session/SessionSplitter.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/model/Session.kt`
- `domain/src/commonMain/kotlin/dev/catsradar/domain/Tuning.kt` (`SESSION_GAP`)
- consumed today only by
  `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/AttachLocation.kt`

## Not handled yet

Everything statistics-facing that the design spec builds on top of outings — rate-eligible
sessions, overall/session rate, a live "current outing" on the Counter screen, best session, the
Outings count and total active time (§5) — is specified but not implemented; there is no
`StatsCalculator` and no Statistics screen yet. `SESSION_GAP` is described in the spec as "a
constant, not a setting, in v1," which the code matches: it's a default parameter on `split()`,
not read from any settings store.
