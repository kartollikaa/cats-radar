# Browsing cats

The Encounters tab shows every non-deleted encounter, newest first, grouped into outings. Grouping
reuses `SessionSplitter.groupByOuting()` — the same gap rule `outings.md` describes, exposed as a
second entry point that returns each outing's own encounters instead of just the aggregate
`Session` `split()` returns; `split()` is now defined in terms of it, so the boundary comparison
still has exactly one implementation. `EncountersStateMapper` turns that grouping into a flat,
already-formatted `ImmutableList<EncounterListItem>` — a `DayHeader` per outing followed by a `Row`
per encounter, both ends already localized by the `DateTimeFormatter` interface (Android
implementation in `presentation/androidMain`) — so `EncountersScreen`'s `LazyColumn` only renders,
never formats or groups. Counter and Encounters sit behind a bottom `NavigationBar`; Counter is the
back-stack root (spec §2): selecting a tab rewrites the stack to `[Counter]` or `[Counter, tab]`,
back from a tab returns to Counter, and back from Counter exits.

## At the edges

An outing's header shows the local date of its *earliest* encounter (its start, per `outings.md`),
computed from that encounter's own `tzOffsetMinutes` — never the device's current zone, so an
outing logged abroad keeps the day it actually happened on. An outing that runs past midnight keeps
that one header for its whole span; it never gains a second header partway through. Soft-deleted
encounters never appear as a row and never start or extend a group, because `groupByOuting()` filters
them the same way `split()` always has. Rows within a group, and groups within the list, both come
back newest first.

**The bottom-nav hazard.** `NavEntry.contentKey` defaults to the nav key, and
`ViewModelStoreNavEntryDecorator` keys each entry's `ViewModelStore` by that key; navigation3-runtime
1.2.0-rc01 has no guard against the same key appearing twice on the back stack, and two entries
sharing a key silently share one `ViewModelStore`. Tab selection never pushes onto the existing
stack — `MutableList<NavKey>.selectBottomNavTab()` always trims down to the shared `Counter` root
first and only then conditionally appends the target, so at most one instance of any key can ever
be on the stack; a duplicate is structurally unreachable rather than merely avoided by a check
(`BottomNavigationTest`, *every tab selection in a mixed sequence leaves each key on the stack at
most once*).

`observeAll()` still loads every non-deleted row on every emission — there is no paging or limit in
this slice. Measured on an in-memory Room database on the host JVM (no device I/O, no UI): reading
and grouping 1,000 rows takes on the order of 20 ms combined, 20,000 rows about 50 ms, and 50,000
rows about 115 ms. A real device is slower than this host measurement, but the current approach
should stay comfortably interactive through the low tens of thousands of rows; past that — the same
range `outings.md` already flags for `StatsCalculator` — the list should switch to a bounded query
(a `LIMIT`/paging DAO method) rather than `observeAll()`'s full table read.

## Where the code lives

- `domain/src/commonMain/kotlin/dev/catsradar/domain/session/SessionSplitter.kt` (`groupByOuting`)
- `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/ObserveEncounters.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/DateTimeFormatter.kt`,
  `presentation/src/androidMain/kotlin/dev/catsradar/presentation/AndroidDateTimeFormatter.android.kt`
- `presentation/src/commonMain/kotlin/dev/catsradar/presentation/encounters/` — `EncountersState`,
  `EncounterListItem`, `EncountersIntent`, `EncountersEffect`, `EncountersStore`,
  `EncountersStateMapper`
- `ui/src/main/kotlin/dev/catsradar/ui/encounters/EncountersScreen.kt`
- `ui/src/main/kotlin/dev/catsradar/ui/navigation/` — `BottomNavTab`, `CatsRadarBottomBar`
- `app/src/main/kotlin/dev/catsradar/app/navigation/` — `Encounters`, `BottomNavigation.kt`
  (`selectBottomNavTab`), `CatsRadarNavHost.kt`

## Not handled yet

Tapping a row does nothing yet — `EncounterDetail` and delete-from-detail are slice 9. There are no
photo thumbnails (the photo pipeline is slices 10–11), no Statistics tab (slice 13), and no paging:
the list still reads the full non-deleted table on every change, acceptable at today's row counts
per the note above but not indefinitely. Headers are inline, not sticky, since either satisfies this
slice's requirement.
