# Counting cats

One tap on the counter screen logs an encounter. The tally is `LogTally`: it builds an
`Encounter` of `kind = TALLY`, `origin = APP`, `locationSource = NONE`, inserts it, and returns
immediately — location is attached afterward, out of band. `CounterStore` fires a haptic tick
before the insert even starts, so the tap never waits on the write, let alone on GPS. The number
on screen is `ObserveEncounterCount`, a live count of every non-deleted encounter; after each tap
it also shows an "Undo" chip for `Tuning.UNDO_VISIBLE` (5 seconds) that soft-deletes the encounter
that tap created. The very first tally the app ever sees also fires a system location-permission
request; a later denial surfaces as a dismissible one-line hint on the counter screen with its own
"Grant" button.

## Feedback for the tap

Each tap raises a **"+N"** above the counter that grows while you keep tapping and fades
`Tuning.TAP_BURST_VISIBLE` after the last one — the window restarts on every tap, so a run of taps
is one burst rather than a flicker per tap, and a later run starts again from one. Like the haptic,
it lands before the write rather than after it succeeds, so holding the button down still counts up
smoothly. If the write then fails the total does not move: the burst is feedback for the *tap*, and
the number is read back from the database.

## The outing in progress

While an outing is open the counter shows how many cats it holds and how long it has been running,
with its rate once there is enough to measure one. It is derived, not tracked: see
[statistics.md](./statistics.md). The numbers advance on a ticker as well as on each cat, so the
elapsed time moves while nothing is being logged.

## Milestones

Crossing a milestone raises a toast, once. The milestone reached is persisted **before** the toast
is emitted, so a process death between the two does not celebrate the same milestone again on the
next launch. The first cat is a milestone — it is the one most worth marking.

## At the edges

Tapping rapidly logs one encounter per tap, with no debounce — three fast taps produce three rows
and three haptic ticks (`CounterStoreTest`, *three rapid taps log three cats with no debounce, one
haptic tick each*). Because inserts are async, a later tap's write can complete before an earlier
one's; `CounterStore` tracks a monotonically increasing `tapSequence`, captured before the
suspending insert, so only a strictly higher sequence number may move the undo target — the chip
always refers to the tap that happened last, not the write that finished last (*undo targets the
most recently created encounter and a second undo is a no-op*). Undo itself is guarded the same
way in miniature: the target id is read and cleared before the suspending delete call runs, so a
second, near-simultaneous "Undo" dispatch sees nothing to do.

A second tap while the chip is already showing restarts the 5-second window rather than stacking a
second timer; once it does expire, nothing brings it back except a fresh tap (*a second tap
restarts the undo window, which then expires and disables undo*). That 5-second window is shorter
than `Tuning.LOCATION_TIMEOUT` (8 seconds) — a fix can still be resolving after Undo has already
faded from the screen. The store itself does nothing special for that overlap; the correctness
guarantee that a late fix can't resurrect an undone row lives one layer down, in how
`AttachLocation` and the DB write are shaped (see `location.md`).

The total is read from a dedicated Room `Flow<Int>` backed by
`SELECT COUNT(*) ... WHERE deletedAt IS NULL`, not from a store-local counter or from counting a
loaded list in memory — an encounter inserted by anything else (a future widget, an import) would
show up the same way a tap does (*the total tracks the repository flow rather than a store-local
counter*). Location permission is requested at most once ever: the flag lives in
`SharedPreferences`, not in the store, so it survives process death, and a fresh `CounterStore`
after a kill-and-relaunch does not re-open the system dialog (*a fresh Store after process death
does not re-request an already-requested permission*). The explicit "Grant" button on the hint
bypasses that flag and always re-requests. A failed insert (disk full, for instance) is swallowed:
the tap still ticks and, on the very first tap, still asks for location permission, but no row is
written and no undo chip appears (*a failed insert is swallowed instead of crashing the store, but
the tap still ticks*).

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/LogTally.kt`, `UndoLastTally.kt`,
  `ObserveEncounterCount.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/counter/` — `CounterState`,
  `CounterIntent`, `CounterEffect`, `CounterStore`, `CounterStateMapper`
- `ui/src/main/kotlin/dev/catsradar/ui/counter/CounterScreen.kt`
- `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt`,
  `CounterEffectHandler.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt` (`observeActiveCount`)

## Not handled yet

Logging more than one cat per tap is an explicit non-goal of v1 — several cats means several taps
or several photos. The photo path (F2), the coat picker strip the design spec has appearing after
a tally (F1), gallery import (F3), and the home-screen widget (F4) are all specified in
`docs/superpowers/specs/2026-09-21-cats-radar-design.md` but none exist in the code yet:
`CounterState` carries no coat field, and `EncounterOrigin.WIDGET` and `CatCoat` are unused
outside the domain model and its own tests.
