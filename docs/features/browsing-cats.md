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

## The outing card

The tab opens on its title and its totals, "147 cats · 38 outings": every cat on screen, a shot's cats each,
and every outing. They are the list's first item and scroll away with it.

Each outing is **one card** on the low surface. Its header names the day in large type — Today, Yesterday
or the date — and under it the start, the count and the span from its first cat to its last: "4:12 PM · 6
cats · 48 min". An outing whose cats are under a minute apart, a lone cat among them, shows no span, since a
span in whole minutes would read "0 min". **On the map** is a tonal pill beside the day, on the rule under
*Showing an outing on the map*.

An outing a walk met wears an **On a walk** chip. A walk meets an outing when the walk's window — its start
to its end, or with no end while it is still on — touches the outing's first-to-last span, ends included;
the stored walks come from `WalkRepository.observeAll()`, so a walk starting over an open tab marks its
outings without a new cat.

**Inside the card**, a run's tiles take each coat's own shape (see `coat.md`), stretched to fill their
square, with the face, the paw or the photo's thumbnail clipped to it. A shot of several cats, and a cat with
no coat, takes no coat's shape. A pair's times sit on an opaque `inverseSurface` chip — dark in the light
theme, light in the dark one — so they read on any photo. Every other cat inside the card — a short run's,
and each row of the list — is a card of its own on the surface colour, the list's 6 dp apart with `medium`
corners: it leads with the tile's shape, names the coat or "A cat" (a shot: "Photo of 3 cats", with its
badge), and reads "time · place" under that, "No location yet" in the tertiary container's text colour. A
screen reader hears the card's name, time and place; its lead stays silent, since the name beside it says
the same. A chosen cat's ring follows its shape, round a tile and round a card's lead alike, and while
selecting the bar at the top is `primaryContainer`.

The card is drawn row by row: the header's piece carries the card's top corners, the outing's last row its
bottom ones (the mapper marks it), and the gaps between rows sit inside the card, so the card stays
unbroken while the list keeps one item per row and its scroll position by index.

The Places list and the map's spot sheet use the same rows without the card, the headline or the new header
(`EncountersStateMapper.catRows`).

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
and outings within the list, both come back newest first; cats sharing a time — a burst of photos
imported from one second — keep the order they were recorded in, however the database returns them.

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
- `ui/src/main/kotlin/dev/catsradar/ui/encounters/` — `EncountersScreen.kt`, `OutingCard.kt` (the headline,
  the card's pieces, its header, the walk chip and the pill), `EncounterGridRows.kt`, `EncounterSingleRow.kt`,
  `CellSelection.kt`, `SelectionBar.kt`, `UndoBar.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/navigation/` — `BottomNavTab`, `CatsRadarBottomBar`
- `app/src/main/kotlin/dev/catsradar/app/navigation/` — `Encounters`, `BottomNavigation.kt`
  (`BottomNavBackStack`), `CatsRadarNavHost.kt`, `Destinations.kt` (`EncountersDestination`, which
  owns the `BackHandler` that ends a selection)

## Showing an outing on the map

An outing's header offers "On the map" when at least one of its cats has a location. The mapper
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
- **A lone photo keeps a large tile.** A tile row that holds a photo holds exactly `MIN_TILES` cats, so
  the photo is never drawn at the smallest, `MAX_TILES`-across size (owner decision, 2026-09-27). The run is split so
  that every photo lands in such a row with its neighbours, using the fewest rows and then the fewest
  card rows; the cats left over pack as any run does, so a card row can follow a photo's tile row
  (`EncounterGridPackerTest`, *a lone photo's tile row holds three cats, so the photo stays large*).
  A photo never drops into a card row for it: when three-cat rows would push one there, as in a
  photo, three cats and a photo, the run keeps its old wider tile row (*a photo that three-cat rows
  would push into a card row keeps a wider tile row instead*).

"Has a photo" means its thumbnail exists: a photo whose thumbnail failed to write packs, and leads,
like a cat without one. What a tile or card leads with is the mapper's choice — the thumbnail, else
the coat's face, else a paw. Tiles and pair tiles have no room for the location, so each announces
itself to accessibility services as its subject (the photo, the coat's name, or "a cat"), time and
location; a card's own text says the same. With nothing logged, the tab says so and points at the
Counter.

## A photo of several cats

A photo can show several cats, each its own encounter with its own coat, all sharing one shot (see
`data-model.md`). The tab shows such a photo **once**: the cats whose cover is a photo of the same shot are one
entry, in the grid and in the list alike (`EncountersStateMapperTest`, *a shot of three cats is one entry holding
all three*). The entry shows that photo with a **badge** — a paw and the number of cats — when it holds more
than one: in the top corner of a pair tile, in the bottom corner of a tile, which is too narrow to hold it
beside the selection check, and beside the name on a card inside an outing's card (beside the time in the
Places list and the spot sheet). For a screen
reader the entry is a "Photo of 3 cats", with its time and location; the badge itself is hidden from it. The
time and location are the first cat's, which every other cat copied when it joined the shot; a place set by hand
on one of them later shows on that cat's own screen, not here. A shot without a thumbnail leads with a paw, since
one coat's face would misname cats that may have several.

- **It packs like one photo.** In the grid a shot pairs, runs and tiles as a single photo would (*a shot packs
  as one photo and pairs with the photo beside it*); in the list it is one row, and the outing's rounded corners
  count it once.
- **A tap opens the shot's first cat** — the oldest by `createdAt`, the one the camera or the import saved
  (*a shot's entry opens its oldest cat*).
- **Selection is by shot.** A long press or a selecting tap adds or removes every cat of the entry, and the bar
  counts cats, not entries. Delete soft-deletes them all as one batch with one undo (`EncountersStoreTest`,
  *deleting a selected shot removes its cats as one batch and one undo restores them*). A selection holding only
  some cats of a shot — a cat added to a shot that is already selected — selects the whole entry.
- **A cat whose cover is another photo keeps its own entry**, even when it also has the shot's photo; the shot's
  entry holds only the cats it shows.
- **Only this tab groups them.** The Places area list and the map's spot list, which use the same rows, keep one
  row per cat, and a shot's cats are separate dots on the map (`MapSpotStateMapperTest`,
  `RegionsStateMapperTest`, *the cats of a shot stay separate rows*).

## Grid or list

The grid is optional: **Settings → Encounters → Grid of cats** (`SettingsRepository.encountersGrid()`, on
unless turned off). Off, the tab goes back to one full row per cat, each a card of its own inside its
outing's card. The Places list and the map's spot sheet keep the older rows: cards with a hairline gap,
round at the outing's outer corners and tight where they meet, the time as the title. The mapper marks
each row as the first, a middle, the last or the only one of its outing
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
selection opens the encounter and selects nothing*). The cat opens among the other cats of its outing, and a
swipe there moves between them (see [encounter-detail.md](./encounter-detail.md#paging-through-the-outing)). A selection survives switching between the
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
