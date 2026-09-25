# Statistics

Every number the app will show about your cats, computed in one pure function from the encounter
list. Nothing is stored and nothing is aggregated in SQL: `StatsCalculator.calculate` takes the
non-deleted encounters, today's date and the current instant, and returns a `Stats`.

The **Stats** tab shows them. This document describes what the numbers **mean**, because that is the
part that is easy to get subtly wrong and expensive to discover later.

Before the first cat the tab shows a single line inviting one, rather than a wall of zeroes: a
screen full of "0" reads like a broken app, not an empty one.

## The counts

- **Total** — every non-deleted encounter.
- **Today / 7 days / 30 days** — by the encounter's **own** local date, so a cat logged abroad stays
  on the day it was logged. The windows **include today**: "7 days" is today plus the six before it.
- **With photo** — cats that have a photo of their own, whether it was taken, imported, or given
  later to a cat logged without one.

## Streaks

A streak is consecutive calendar days with at least one cat. Several cats in one day do not make it
longer — it counts days, not cats.

The **current** streak may end today *or yesterday*. Ending it strictly at today would mean the
streak breaks at midnight, before the user has had a chance to go out; a streak you lose while
asleep is a streak nobody would trust.

The **longest** streak is the longest run anywhere in the history, which is often not the current
one.

## Milestones

`Tuning.MILESTONES` is a fixed ladder. The next milestone is the first rung **strictly above** the
total, with the distance to it — a total sitting exactly on a rung points at the next one, never at
itself, so the screen never says "0 to go". Past the last rung there is nothing left to reach and
the field is empty.

## Outings and rates

An outing is derived, never stored — see [outings.md](./outings.md).

A **rate-eligible** outing has at least two cats and lasts at least `Tuning.MIN_RATE_DURATION`. Both
conditions exist to stop a meaningless number: two cats seen four seconds apart is not "1800 cats an
hour", it is one moment.

The **overall rate pools cats and time** — all the cats from eligible outings divided by all their
duration — rather than averaging each outing's rate. Averaging would let a brief lucky outing
dominate a long ordinary one. Two cats over half an hour and ten over eighteen minutes pool to
15/h, where the mean of the two rates would claim 18.67/h.

A `Rate` is kept as cats per hour and can give cats per minute; which one to *show* is a
presentation decision, not a domain one. The screen switches at exactly one a minute: above it
"73 cats/h" is hard to read, below it "0.2 cats/min" is worse, and the boundary itself belongs to
the minute side. Values are rounded to one decimal. With no measurable rate the row shows "—"
rather than a zero, because none-measured and zero-cats-an-hour are different claims.

The **best outing** is the fastest eligible one, not the one with the most cats.

**Outings** and **active time** count *every* outing, including ones too short to rate. A lone cat
is an outing of zero duration: it adds to the count and nothing to the time.

## Walks: distance and cats per km

**Distance walked** is the sum of every walk's recorded route — the great-circle length between its
points, added across every walk there is, not only the ones long enough to rate. A walk still
recording counts what it has logged so far, from its start.

**Cats per km pools cats and kilometres**, the same way the overall rate pools cats and time: cats
and distance are summed across every walk at least `Tuning.MIN_RATE_DISTANCE_METERS` long, then
divided, rather than averaging each walk's own rate. A walk under that length, and its cats, are left
out of the pool entirely. A walk that reaches it but saw no cat still counts its kilometres and so
pulls the rate down; a cat counts toward it if it was logged while the walk covered that moment,
which for a walk still on reaches back to its start with no end to stop at. Cats per km has one
display only — there is no per-metre variant the way the overall rate switches between cats per hour
and cats per minute.

Distance shows as whole metres below one kilometre, kilometres to one decimal from there. Both rows
are left off the screen when nothing has been walked, so a user who has never allowed location, or
never started a walk, never reads "0 m walked"; cats per km reads "—" instead of a number when
nothing measured reaches the threshold.

## The outing in progress

The current outing is open while another cat would still join it — that is, while the last cat is no
more than `Tuning.SESSION_GAP` old. Its **elapsed time runs to now**, not to its last cat, so a walk
where nothing has happened for ten minutes shows a falling rate rather than a frozen one. It reports
its count immediately and its rate only once it is eligible.

**Elapsed time never goes below zero.** An encounter can be dated *ahead* of now — a photo carries
its own EXIF timestamp, and the device that wrote it may have had a clock running fast — and an
outing that began in the future would otherwise report a negative length, which is what "1 cat ·
-26 min" on the Counter was. It reads as zero instead, which also keeps it below the minimum
duration, so no rate is computed over no time.

Only this one figure needs the guard. Every other duration comes from a `Session`, whose start and
end are the first and last of a list the splitter has already sorted, so its span cannot be
negative however wrong the clock was.

## Where the code lives

- `domain/…/stats/StatsCalculator.kt` — the calculation
- `domain/…/stats/Streaks.kt` — runs of consecutive days
- `domain/…/stats/Stats.kt` — `Stats`, `Rate`, `Milestone`, `RatedOuting`, `CurrentOuting`
- `domain/…/stats/WalkStats.kt` — `WalkStats`, `WalkStatsCalculator` — distance and cats per km
- `domain/…/stats/CatTimes.kt` — the cats a walk's cats-per-km window counts, from its start to its
  end, both included, and with no end for a walk still on

## Where the screen lives

- `presentation/…/statistics/` — `StatisticsState`, `StatisticsStateMapper`, `StatisticsStore`
- `ui/…/statistics/StatisticsScreen.kt`
- `domain/…/usecase/ObserveStats.kt`, `ObserveWalkStats.kt` — combined by `StatisticsStore`, which
  reads the encounter list once and hands the same list to both
- `domain/…/usecase/ObserveWalkTracks.kt` — every walk with its route, which `ObserveWalkStats` sums
- `domain/…/stats/WalkMeasures.kt`, `CatTimes.kt` — each walk's length and cats, kept from one
  recompute to the next

The numbers recompute whenever the encounter list changes **and on a ticker**, because the outing in
progress is measured against "now" and goes stale on its own between cats; the walk rows recompute on
top of that whenever a walk starts, ends, or gains a point. `ObserveStats` takes the ticker as a
constructor parameter so a test can drive it instead of waiting.

The walk rows are worked out off the main thread, and when the screen falls behind — a recording walk
keeps adding points — it gets only the latest result. Each recompute redoes only what changed. A
walk's length is measured again only when its route has gained points, which a walk that has ended
can still do when a backup brings more of its route. Its cats are counted again only when the cats
change or the walk starts or ends somewhere else. Counting them is a binary search over the cats'
times, sorted once each time the encounter list changes, not a pass over every cat for every walk.
Every point is still read from the database on each change.

## Not built yet

Everything is computed from the full list in memory; the spec puts the revisit point at tens of
thousands of encounters. Distance is metric only — there is no imperial unit, and none is planned.
