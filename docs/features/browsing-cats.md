# Browsing cats

The Encounters tab shows every non-deleted encounter, newest first, grouped into outings. Grouping
reuses `SessionSplitter.groupByOuting()` — the same gap rule `outings.md` describes, exposed as a
second entry point that returns each outing's own encounters instead of just the aggregate
`Session` `split()` returns; `split()` is now defined in terms of it, so the boundary comparison
still has exactly one implementation. `EncountersStateMapper` turns that grouping into a flat,
already-formatted `ImmutableList<EncounterListItem>` — an `OutingHeader` per outing followed by a
`Row` per encounter, both ends already localized by the `DateTimeFormatter` interface (Android
implementation in `presentation/androidMain`) — so `EncountersScreen`'s `LazyColumn` only renders,
never formats or groups. Counter and Encounters sit behind a bottom `NavigationBar`; Counter is the
back-stack root (spec §2): selecting a tab rewrites the stack to `[Counter]` or `[Counter, tab]`,
back from a tab returns to Counter, and back from Counter exits.

## At the edges

An outing header shows the local date and start time of its *earliest* encounter (its start, per
`outings.md`), computed from that encounter's own `tzOffsetMinutes` — never the device's current
zone, so an outing logged abroad keeps the day it actually happened on. The start time is there
specifically so two outings landing on the same day still read as two sections: the header is the
one place a reader can see where one outing ends and the next begins, so it names the outing rather
than just the day. An outing that runs past midnight keeps that one header for its whole span; it
never gains a second header partway through. Soft-deleted encounters never appear as a row and
never start or extend a group, because `groupByOuting()` filters them the same way `split()`
always has. Rows within a group, and groups within the list, both come back newest first.

**The bottom-nav hazard.** `NavEntry.contentKey` defaults to the nav key, and
`ViewModelStoreNavEntryDecorator` keys each entry's `ViewModelStore` by that key; navigation3-runtime
1.2.0-rc01 has no guard against the same key appearing twice on the back stack, and two entries
sharing a key silently share one `ViewModelStore`. `CatsRadarNavHost` never holds the raw back
stack at all — `rememberBottomNavBackStack()` returns a `BottomNavBackStack`, which implements
`List<NavKey>` but not `MutableList<NavKey>`, so a bare push is not something the host can even
write, let alone land unreviewed. `selectTab()`, its only way to change tabs, always trims down to
the shared `Counter` root before conditionally appending the target, so at most one instance of any
key can ever exist; a duplicate is structurally unreachable rather than merely avoided by a check
(`BottomNavigationTest`, *every tab selection in a mixed sequence leaves each key on the stack at
most once*). `popOrNull()` is the only way back navigation can shrink the stack.

`observeAll()` still loads every non-deleted row on every emission — there is no paging or limit in
this slice. That is fine at the row counts the app produces today, but it does not scale
indefinitely: the same point `outings.md` makes about `StatsCalculator` applies here too, and a
bounded query (a `LIMIT`/paging DAO method) should replace `observeAll()`'s full table read once
row counts stop being trivial to read and group on every emission.

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/session/SessionSplitter.kt` (`groupByOuting`)
- `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/ObserveEncounters.kt`,
  `DeleteEncounters.kt`, `UndoDeleteEncounters.kt`; `domain/…/model/DeletedBatch.kt`
- `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt` (`softDeleteAll`,
  `undoDeleteAll`)
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/DateTimeFormatter.kt`,
  `presentation/src/androidMain/kotlin/dev/catsradar/presentation/AndroidDateTimeFormatter.android.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/` — `EncountersState`,
  `EncounterListItem`, `EncountersIntent`, `EncountersEffect`, `EncountersStore`,
  `EncountersStateMapper`
- `ui/src/main/kotlin/dev/catsradar/ui/encounters/` — `EncountersScreen.kt`, `SelectionBar.kt`,
  `UndoBar.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/navigation/` — `BottomNavTab`, `CatsRadarBottomBar`
- `app/src/main/kotlin/dev/catsradar/app/navigation/` — `Encounters`, `BottomNavigation.kt`
  (`BottomNavBackStack`), `CatsRadarNavHost.kt`, `Destinations.kt` (`EncountersDestination`, which
  owns the `BackHandler` that ends a selection)

## What a row shows

Each outing reads as one card: its rows are cards of their own with a hairline gap, round at the
outing's outer corners and tight where they meet — the mapper tells each row whether it is the
first, a middle, the last or the only one of its outing, so the screen only picks a shape. The
mapper also decides what a row leads with: the photo when there is one, else the cat's coat as its
face, else a paw, so every row shows the most telling thing known about that cat; the face is
announced by its coat's name, the paw is decorative. With nothing logged, the tab says so and points
at the Counter.

## Selecting and deleting several

A long press on a row starts a selection with that row in it. While selecting, a tap adds or removes
a row instead of opening it; a bar at the top shows how many are selected, a ✕ and a Delete. The
selected rows swap their lead for a check mark. Deselecting the last row, the ✕ and system back all
end the selection; back ends it without leaving the tab. Whether a tap opens or selects is the
Store's call, not the screen's: every tap reaches `EncountersStore` as `RowClicked`, and only
outside a selection does it answer with `OpenEncounter` (*a tap outside selection opens the
encounter and selects nothing*).

Delete is a soft delete with an undo, like the detail screen's, and asks nothing first. The selected
cats leave the list at once, the selection ends, and a bar at the bottom says how many went, with
Undo, for `Tuning.UNDO_VISIBLE`. The window lives in the Store's state rather than in a
`SnackbarHost`, so its length is a unit test under virtual time. The detail screen keeps its own
undo for the opposite reason: there the deletion is another screen's, and showing it here would
need two Stores to talk. Here the list's own Store made it.

### At the edges

- **Undo brings back exactly that batch.** Every cat in it gets one `deletedAt`, and undo restores
  only rows still carrying it, so a cat deleted some other way stays deleted (`data-model.md`).
- **A second delete inside the window replaces the undo.** The bar shows the new count, undo
  restores only the new batch, the first one stands, and the new batch gets a full window of its own
  (*a second delete inside the window replaces the undo with its own batch*).
- **A selected cat deleted elsewhere** — from its detail screen, or by the Counter's undo — drops
  out of the selection; when none is left, the selection ends. The mapper does this: an id with no
  row on the list is never reported as selected.
- **A failed write** leaves the selection as it was and shows no undo. A failed undo reopens the
  window rather than stranding the cats (*a failed undo keeps the undo bar and a fresh window to try
  again*).
- **Delete tapped twice** while the write is in flight writes once.
- **Rows coming back above the screen.** A keyed `LazyColumn` keeps its first visible row in place
  when rows are inserted above it, so undoing the delete of the top outing would bring it back out
  of sight. A list resting at the very top asks to stay at the top on every change, so the restored
  outing is what the user sees.
- **Leaving the tab during the window** takes the undo with it and the deletion stands, the same
  trade the detail screen makes. A selection does not survive a tab switch either: both live in the
  Store, which is scoped to the tab's entry.

## Not handled yet

There is no paging: the list still reads the full non-deleted table on every change, acceptable at
today's usage but not indefinitely, per the note above. Headers are inline, not sticky. There is no
"select all" and no way to select an outing from its header.
