# Counting cats

One tap on the counter screen logs an encounter. The tally is `LogTally`: it builds an
`Encounter` of `kind = TALLY`, `origin = APP`, `locationSource = NONE`, inserts it, and returns
immediately — location is attached afterward, out of band. `CounterStore` fires a haptic tick
before the insert even starts, so the tap never waits on the write, let alone on GPS. The number
on screen is the total from `ObserveStats`, every non-deleted encounter; after a tap an "Undo" chip
shows for `Tuning.UNDO_VISIBLE`, and each press of it soft-deletes the newest cat of the run of taps
it belongs to (below). The very first tally the app ever sees also fires a system location-permission
request; a later denial surfaces as a dismissible one-line hint on the counter screen with its own
"Grant" button.

## Feedback for the tap

Each tap raises a **"+N"** badge in the count block's top corner that counts the cats of the current
run of taps, the same run Undo walks back (below), and stays up for as long as that run is open.
Every tap adds one and every Undo takes one off: three taps read "+3", an Undo turns it into "+2",
and the Undo that takes back the run's last cat takes the badge with it. When the undo window runs
out, the badge goes with the chip, and the next tap starts again from one. Like the haptic, a tap's
one lands before the write rather than after it succeeds, so holding the button down still counts up
smoothly. So a tap whose write is still running is on the badge before it is in the run: it keeps
its one when the window closing, or an Undo of a newer tap, empties the run around it, and joins or
opens a run when it lands, unless an Undo takes it back first (see *At the edges*). If the write
fails, the tap takes its one back off: it added no cat, and the total, read back from the database,
does not move either. It is a badge rather than bare text because a wide number in a short block
reaches that corner; TalkBack reads the total as the block's own label, and before the total is
known the block is named by what it does.

The count sits in a large block that **is** the button. It squashes under a press and springs back,
and the number **rolls up** when a cat is added and **down** when one is undone — the screen
compares the number it had with the one it now has, so an undo, or an import finishing while the
Counter is showing, rolls the right way. It rolls like an odometer, one digit at a time: only the
digits that change move, each in its own window, and a carry ripples to the left — 49 to 50 turns
the units over at once and the tens a beat later, and 99 to 100 rolls a hundreds digit in, which an
Undo rolls back out. An Undo pressed mid-roll turns the roll back down rather than finishing it
upward. Every digit is the same width, so a rolling digit never shoves its neighbours, and the
number reads left to right under a right-to-left language too.
The roll follows the database, so it lands a moment after the burst: the burst answers the finger,
the roll answers the write. The units start rolling on the frame the new number arrives rather than
easing into motion, and settle with a small overshoot. The squash is drawn only: what a press can
land on stays the whole block.

Two changes are not rolled. The first total after the app starts is not a change, so until it has
been read the block shows no number at all, rather than a 0 that then rolls up to it. And a change
made while another tab is showing — a delete on the encounter screen, say — is already in place when
the Counter comes back, because nothing was on screen to roll it.

The number shrinks to fit the block rather than wrapping, so a short phone, a large font or a
five-digit total keeps it on one line. The block has a floor, though: when the screen cannot fit
everything — a small phone at a large font, say, or a small phone with the location hint showing —
it keeps a height at which the number still reads, and the Counter scrolls instead. Scrolling is
switched on only then, because an enabled scroll delays every press and turns a tap that drifts a
few pixels into a drag: on a screen with room to spare, a tap is only ever a tap.

**The controls do not jump.** Undo has a place of its own at the far end of the walk button's row,
outside the count block — inside the block, a follow-up tap on the same spot would land on Undo and
take a cat away instead of adding one. The walk button sits in the middle of that row, and Undo
appearing beside it does not move it. Only where the two would meet — a narrow phone, a large font,
a longer translation — does the button step aside toward the start, and past that it shortens its
label: Undo is never squeezed. A tap on the button starts a walk, but stopping one takes a press
held until a fill crosses the button ([walking-mode.md](./walking-mode.md#stopping-takes-a-hold)).
The button keeps one height whether it starts or stops a walk, and the outing line keeps its line,
empty when no outing is open, so a walk or an outing starting or ending leaves the block the same
size. The location hint and the import progress and summary (a card each, see `import.md`) appear
above the count and take their room from it, so the number shrinks and the walk button, the coat grid and the Photo button stay
where they are — unless the block is already at its floor, when the Counter scrolls instead.

## Undoing a run of taps

The undo window belongs to a run of taps, not to one tap. Every tap made while the chip is up joins
the run and restarts the window; every Undo takes back the newest tap of the run, soft-deleting its
cat and cancelling its location attach — or, for a tap still being written, deleting it the moment
it lands, before it ever gets one (see *At the edges*) — and restarts the window again. So five
mistaken taps come back off with five Undos, and the chip stays up until the last of them is gone,
the "+N" badge counting down with it. A tap after an Undo joins the same run and counts on from what
the badge has left.
Once the window runs out with nothing pressed, the run is closed: the chip goes, and nothing brings
it back except a fresh tap. The coat grid's ring follows the run as well — after an Undo it rings the
coat of the newest cat still in it (`coat.md`). The cases below that concern undo are in
`CounterStoreUndoTest`.

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
suspending insert, and keeps the run in that order rather than the order the writes finish in, so
Undo always takes back the tap that happened last (*undo follows the order of the taps, not the
order their writes finished in*). That holds while the last tap is still being written too: its cat
has no id to delete yet, so the Undo marks the tap instead. The "+N" takes one off at once, and the
cat is soft-deleted the moment its write lands, never gets a location fix and never joins the run,
so the next Undo takes the tap before it (*undo takes back the newest tap even while its write is
still running*; *a tap undone while being written never joins the run*). The total can tick up and
straight back as that write lands and is taken back. If the write fails instead, there is nothing to
delete and the older cats stay undoable (*a tap undone while being written whose write then fails
leaves the older cat undoable*). A slow write from a run that has already expired does not reopen
the window (*a tap whose write lands after the window closed does not reopen it*), and one landing
behind a newer tap's does not stretch it (*an older tap's write landing late does not stretch the
window of the newer one*). Undo takes its
cat off the run before the suspending delete runs, so two Undos pressed back to back take back two
different cats, never the same one twice (*two undos dispatched back to back take away two
different cats*); an Undo with nothing left to take back does nothing (*undo walks a run of taps back newest
first until every cat of it is gone*).

A tap or an Undo while the chip is showing restarts the window rather than stacking a second timer
(*a second tap restarts the undo window, which then expires and disables undo*; *each undo restarts
the window for the cats still left in the run*). The window is shorter than
`Tuning.LOCATION_TIMEOUT` — a fix can still be resolving after Undo has already faded from the
screen. The store itself does nothing special for that overlap; the correctness
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
the tap still ticks*); inside a run, the failed tap's one comes back off the "+N" and the rest of the
run stays undoable (*a tap whose write fails takes its one back off the burst*). A tap whose write
is still running when the window closes keeps its one on the badge until it lands and opens a run of
its own (*a tap still being written when the window closes keeps its place on the burst*), while a
tap from the closed run whose write lands late leaves nothing behind (*a tap whose write lands after
the window closed does not reopen it*).

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/LogTally.kt`, `UndoLastTally.kt`,
  `ObserveStats.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/counter/` — `CounterState`,
  `CounterIntent`, `CounterEffect`, `CounterStore`, `CounterStateMapper`
- `ui/src/main/kotlin/dev/catsradar/ui/counter/CounterScreen.kt`, `TallyBlock.kt` (the count and its
  press), `RollingCount.kt` (the digit-by-digit roll and the shrink to fit), `FillOrScroll.kt` (the
  block's floor and the scroll past it), `WalkRow.kt` (the walk button and Undo's place beside it),
  `UndoChip.kt`
- `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt`,
  `CounterEffectHandler.kt`

## Not handled yet

Logging more than one cat per tap is an explicit non-goal of v1 — several cats means several taps
or several photos. The other ways a cat is logged have their own documents: by coat (`coat.md`),
by photo and from the gallery (`photos.md`, `import.md`), from the lock screen (`walking-mode.md`)
and from the home screen (`widget.md`).
