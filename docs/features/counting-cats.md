# Counting cats

One tap on the counter screen logs an encounter. The tally is `LogTally`: it builds an
`Encounter` of `kind = TALLY`, `origin = APP`, `locationSource = NONE`, inserts it, and returns
immediately — location is attached afterward, out of band. `CounterStore` fires a haptic tick
before the insert even starts, so the tap never waits on the write, let alone on GPS. The number
on screen is the total from `ObserveStats`, every non-deleted encounter; after each tap an "Undo"
chip shows for `Tuning.UNDO_VISIBLE` (5 seconds) that soft-deletes the encounter that tap
created. The very first tally the app ever sees also fires a system location-permission
request; a later denial surfaces as a dismissible one-line hint on the counter screen with its own
"Grant" button.

## Feedback for the tap

Each tap raises a **"+N"** in the count block's top corner that grows while you keep tapping and
fades `Tuning.TAP_BURST_VISIBLE` after the last one — the window restarts on every tap, so a run of
taps is one burst rather than a flicker per tap, and a later run starts again from one. Like the
haptic, it lands before the write rather than after it succeeds, so holding the button down still
counts up smoothly. If the write then fails the total does not move: the burst is feedback for the
*tap*, and the number is read back from the database.

The count sits in a large block that **is** the button. It squashes under a press and springs back,
and the number **rolls up** when a cat is added and **down** when one is undone — the screen
compares the number it had with the one it now has, so an undo, or an import finishing while the
Counter is showing, rolls the right way. The roll follows the database, so it lands a moment after
the burst: the burst answers the finger, the roll answers the write. The springs are tuned stiff
enough that the roll starts on the frame the new number arrives rather than easing into motion. The
squash is drawn only: what a press can land on stays the whole block.

Two changes are not rolled. The first total after the app starts is not a change, so until it has
been read the block shows no number at all, rather than a 0 that then rolls up to it. And a change
made while another tab is showing — a delete on the encounter screen, say — is already in place when
the Counter comes back, because nothing was on screen to roll it.

The number shrinks to fit the block, so a short phone, a large font or a five-digit total never
wraps or clips it.

**The controls never move.** Undo has a place of its own at the far end of the walk-chip row,
outside the count block: inside it, a follow-up tap on the same spot would land on Undo and take a
cat away instead of adding one. The outing line keeps its line, empty when no outing is open, so an
outing starting or ending leaves the block the same size. The location hint and the import progress
and summary appear above the count and take their room from it; the number shrinks, and the walk
chip, the coat grid and the Photo button stay where they are.

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

The total is counted from the live list of encounters the repository emits, not kept by the
store — an encounter written by anything else (the widget, the walking notification, an import)
shows up the same way a tap does (*the total tracks the repository flow rather than a store-local
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
  `ObserveStats.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/counter/` — `CounterState`,
  `CounterIntent`, `CounterEffect`, `CounterStore`, `CounterStateMapper`
- `ui/src/main/kotlin/dev/catsradar/ui/counter/CounterScreen.kt`, `TallyBlock.kt` (the count, its
  press and its roll)
- `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt`,
  `CounterEffectHandler.kt`

## Not handled yet

Logging more than one cat per tap is an explicit non-goal of v1 — several cats means several taps
or several photos. The other ways a cat is logged have their own documents: by coat (`coat.md`),
by photo and from the gallery (`photos.md`, `import.md`), from the lock screen (`walking-mode.md`)
and from the home screen (`widget.md`).
