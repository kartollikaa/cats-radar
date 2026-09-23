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
- `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/ObserveEncounters.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/DateTimeFormatter.kt`,
  `presentation/src/androidMain/kotlin/dev/catsradar/presentation/AndroidDateTimeFormatter.android.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/` — `EncountersState`
  (`EncountersRow`, and the `EncounterListItem` rows the Places area list uses),
  `EncounterGridPacker`, `EncountersIntent`, `EncountersEffect`, `EncountersStore`,
  `EncountersStateMapper`
- `ui/src/main/kotlin/dev/catsradar/ui/encounters/` — `EncountersScreen.kt`, `EncounterGridRows.kt`,
  `EncounterSingleRow.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/navigation/` — `BottomNavTab`, `CatsRadarBottomBar`
- `app/src/main/kotlin/dev/catsradar/app/navigation/` — `Encounters`, `BottomNavigation.kt`
  (`BottomNavBackStack`), `CatsRadarNavHost.kt`

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

## Not handled yet

There is no paging: the list still reads the full non-deleted table on every change, acceptable at
today's usage but not indefinitely, per the note above. Headers are inline, not sticky.
