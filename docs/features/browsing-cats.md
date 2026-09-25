# Browsing cats

The Encounters tab shows every non-deleted encounter, newest first, grouped into outings. Grouping
reuses `SessionSplitter.groupByOuting()` — the same gap rule `outings.md` describes, exposed as a
second entry point that returns each outing's own encounters instead of just the aggregate
`Session` `split()` returns; `split()` is now defined in terms of it, so the boundary comparison
still has exactly one implementation. `EncountersStateMapper` turns that grouping into a flat,
already-formatted `ImmutableList<EncountersRow>` — an `OutingHeader` per outing followed by its
cats' rows, packed into a grid or one per row (see *What the grid shows* and *Grid or list*), every
label already localized by the `DateTimeFormatter` interface (Android implementation in
`presentation/androidMain`) — so `EncountersScreen`'s `LazyColumn` only renders, never formats,
groups or packs. Counter and Encounters sit behind a bottom `NavigationBar`; Counter is the
back-stack root (spec §2): selecting a tab rewrites the stack to `[Counter]` or `[Counter, tab]`,
back from a tab returns to Counter, and back from Counter exits.

## At the edges

An outing header shows the local date and start time of its *earliest* encounter (its start, per
`outings.md`), computed from that encounter's own `tzOffsetMinutes` — never the device's current
zone, so an outing logged abroad keeps the day it actually happened on. The start time is there
specifically so two outings landing on the same day still read as two sections: the header is the
one place a reader can see where one outing ends and the next begins, so it names the outing rather
than just the day. An outing that runs past midnight keeps that one header for its whole span; it
never gains a second header partway through. Soft-deleted encounters never appear as a cat and
never start or extend a group, because `groupByOuting()` filters them the same way `split()`
always has — so a deleted cat between two photos leaves them free to pair. Cats within an outing,
and outings within the list, both come back newest first.

**Back from a cat returns to the same place in the list.** The list's scroll position is saved with
the Encounters entry while a cat's screen covers it, and it comes back when that screen closes
(`EncountersListPositionTest`, *back from a cat returns to the list scrolled where it was*); a
rotation restores it the same way. The position is kept by row index, not by row, so a cat logged
from the walking notification meanwhile shifts the view by the rows it adds above. The pull
to the top described under *Rows coming back above the screen* checks that restored position, not
whether the list can scroll back: a returning list is not laid out yet and reports it cannot, so it
would look like it was resting at the top and jump there.

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
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/` — `EncountersState`
  (`EncountersRow`, and the `EncounterListItem` rows the Places area list uses),
  `EncounterGridPacker`, `EncountersIntent`, `EncountersEffect`, `EncountersStore`,
  `EncountersStateMapper`, `EncountersSelection.kt` (`withSelection`)
- `ui/src/main/kotlin/dev/catsradar/ui/encounters/` — `EncountersScreen.kt`, `EncounterGridRows.kt`,
  `EncounterSingleRow.kt`, `CellSelection.kt`, `SelectionBar.kt`, `UndoBar.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/navigation/` — `BottomNavTab`, `CatsRadarBottomBar`
- `app/src/main/kotlin/dev/catsradar/app/navigation/` — `Encounters`, `BottomNavigation.kt`
  (`BottomNavBackStack`), `CatsRadarNavHost.kt`, `Destinations.kt` (`EncountersDestination`, which
  owns the `BackHandler` that ends a selection)

## Showing an outing on the map

An outing's header ends in "On the map" when at least one of its cats has a location. The mapper
decides, by giving the header the id of the outing's first cat. Choosing it opens the Map tab on
that outing alone (`map.md`). The map's spot sheet and the cats of a place (`places.md`) use the same
rows and offer the same action.

## What the grid shows

Under each outing header, `EncounterGridPacker` lays the outing's cats out newest first, never
reordering them and never carrying a row across a header:

- **Two photos in a row share a pair row** — two square tiles side by side, each loading the app's
  full-size copy (the thumbnail is too small at half the screen's width), with its time on a chip.
  A pair tile whose full copy is missing or unreadable falls back to the thumbnail. Pairing is
  greedy from the newest cat, so a third photo in a row is left without a partner.
- **Everything between pairs is a run**, and a lone photo stays in it. A run of at least
  `MIN_TILES` cats is split into **tile rows** of `MIN_TILES` to `MAX_TILES` (both in
  `EncounterGridPacker`), using as few rows as that allows, as even as they can be, and the longer
  rows first; a tile is a square showing the photo's thumbnail, the coat's face or a paw, with the
  time under it, shrunk to stay on one line. A shorter run is a **card row**: full cards showing
  the time and location, sharing the width.

"Has a photo" means its thumbnail exists: a photo whose thumbnail failed to write packs, and leads,
like a cat without one. What a tile or card leads with is the mapper's choice — the thumbnail, else
the coat's face, else a paw. Tiles and pair tiles have no room for the location, so each announces
itself to accessibility services as its subject (the photo, the coat's name, or "a cat"), time and
location; a card's own text says the same. With nothing logged, the tab says so and points at the
Counter.

## Grid or list

The grid is optional: **Settings → Encounters → Grid of cats** (`SettingsRepository.encountersGrid()`, on
unless turned off). Off, the tab goes back to one full row per cat, and each outing reads as one card:
its rows are cards of their own with a hairline gap, round at the outing's outer corners and tight where
they meet. The mapper marks each row as the first, a middle, the last or the only one of its outing
(`GroupPosition`), and states the layout (`EncountersLayout`) so the screen only picks the gaps and the
header's inset. `EncountersStore` combines the setting with the encounters, so flipping the switch
re-lays an open tab without waiting for a new cat. The Places area list is the same either way.

## Selecting and deleting several

A long press on a cat — a pair tile, a tile, a card or a list row — starts a selection with that cat
in it. While selecting, a tap adds or removes a cat instead of opening it, and so does a long press;
a bar at the top shows how many are selected, a ✕ and a Delete. A selected cat carries a check
badge and an outline; cards and list rows also turn to a tinted background. Deselecting the last
cat, the ✕ and system back all end the selection; back ends it without leaving the tab. Whether a
tap opens or selects is the Store's call, not the screen's: every tap reaches `EncountersStore` as
`EncounterClicked`, and only outside a selection does it answer with `OpenEncounter` (*a tap outside
selection opens the encounter and selects nothing*). A selection survives switching between the
grid and the list (*a selection survives turning the grid off*).

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
  out of the selection; when none is left, the selection ends. The mapper does this: an id naming no
  cat on screen is never reported as selected.
- **A failed write** leaves the selection as it was and shows no undo. A failed undo reopens the
  window rather than stranding the cats (*a failed undo keeps the undo bar and a fresh window to try
  again*), unless another batch was deleted while it ran: that batch owns the bar then, and the
  failed one stays deleted (*a failed undo leaves a batch deleted meanwhile as the one to undo*).
- **Delete tapped twice** while the write is in flight writes once.
- **A tap while selecting costs no re-mapping.** `withSelection` re-marks the cats already on screen;
  only a new emission from the database groups, packs and formats the list again.
- **Rows coming back above the screen.** A keyed `LazyColumn` keeps its first visible row in place
  when rows are inserted above it, so undoing the delete of the top outing would bring it back out
  of sight. A list resting at the very top, and not being scrolled, asks to stay at the top on every
  change, so the restored outing is what the user sees (*an outing coming back above a list resting
  at the top is shown*); a drag that has just started is left alone.
- **Screen readers** hear the removed count when the bar appears and the selected count as it
  changes; a row's actions are read as Select or Deselect while selecting.
- **The bar covers the bottom of the list** for its window, as a Material snackbar does; the list
  gains no extra padding under it.
- **Leaving the tab during the window** takes the undo with it and the deletion stands, the same
  trade the detail screen makes. A selection does not survive a tab switch either: both live in the
  Store, which is scoped to the tab's entry.

## Not handled yet

There is no paging: the list still reads the full non-deleted table on every change, acceptable at
today's usage but not indefinitely, per the note above. Headers are inline, not sticky. There is no
"select all" and no way to select an outing from its header.
