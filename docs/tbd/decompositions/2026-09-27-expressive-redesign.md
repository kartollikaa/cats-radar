# Expressive redesign of the Counter and a cat's detail — PR Decomposition Map

- **Created:** 2026-09-27
- **Epic reference:** [docs/superpowers/specs/2026-09-27-expressive-redesign-design.md](../../superpowers/specs/2026-09-27-expressive-redesign-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, string resources.
- **Integration strategy:** every slice is **naturally safe**. Each changes one surface in place and ships
  whole; the two behaviour changes, E6's Remove and E7's coat sheet, are complete inside their slices. E1
  goes first because every other slice draws on its theme.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| E1 | The Expressive theme | `CatsRadarTheme` draws with `MaterialExpressiveTheme` and the expressive motion scheme, and `app-shell.md` stops saying it cannot. | safe | ~150 | — | in-review |
| E2 | The count in a cookie, with its milestone | The Counter's number sits in a twelve-sided cookie with an arc toward the next milestone; the goal and the outing ride tags on the ring, and nothing sits under the count. | safe | ~450 | E1 | in-review |
| E3 | The walk row | The walk button as an extended FAB and Undo as a tonal button. | safe | ~250 | E2 | in-review |
| E4 | Coat faces in Material shapes | Every coat grid draws its faces in a shape per column, with the ring on the shape; Photo is the filled split button. | safe | ~350 | E1 | in-review |
| E5 | The detail's photos as a carousel | A cat's photos become a multi-browse carousel ending in the two add items, and a cat without one opens on its face and a button group. | safe | ~600 | E1 | planned |
| E6 | The detail names its cat | The title from the coat, the facts row, More in the bar, and Remove this cat in place of Delete. | safe | ~500 | E1 | planned |
| E7 | The coat card and the coat sheet | The detail's coat is a card that opens a sheet destination, with the cat's face in its header and a *No coat* tile, which replaces the inline picker; the coat question after a photo takes the same header. | safe | ~550 | E4, E6 | planned |
| E8 | Where you met | The Where card restyled around its map, with the fix's accuracy drawn to scale around the dot; a cat with no location gets the notice-card alert under its facts instead of the card. | safe | ~450 | E6 | planned |
| E9 | The cat's number | Each cat's place in the live log, oldest first, opens the facts row. | safe | ~400 | E6 | planned |
| E10 | Statistics as a dashboard | The Stats tab gets the per-day chart with its range pill, stat tiles, coat share bars and an outings grid. | safe | ~600 | E1 | in-review |
| E11 | Reaching a rung | The cookie bounces and the ring glows when a tally lands on a milestone, and the bottom tag says "100 cats!" until the run closes. | safe | ~250 | E2 | planned |
| E12 | Encounters in outing cards | Each outing becomes a card headed by its day, count, span and walk, with On the map as a pill; tiles take the coat shapes; the headline totals the tab. | safe | ~550 | E4 | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice E1 — The Expressive theme
- **In scope:** `CatsRadarTheme` on `MaterialExpressiveTheme` with `MotionScheme.expressive()`; the opt-in;
  `app-shell.md` § Look.
- **Out of scope:** any screen's layout.
- **Ships safely because:** same colours, shapes and type; components only take their Expressive defaults.
  Renders of every tab, light and dark, before and after, go in the PR.
- **Cleanup owed:** none.

### Slice E2 — The count in a cookie
- **In scope:** `Milestone.reached`; the Counter state's milestone; the cookie, the arc and the milestone
  line; `*Emphasized` for the number; `counting-cats.md`, and `statistics.md` for `reached`.
- **Out of scope:** the tiles and the walk row (E3).
- **Ships safely because:** the block stays the button, with the same roll, badge and label.
- **Cleanup owed:** none.

### Slice E3 — The walk row
- **In scope:** the walk button's look; Undo as a tonal button; `counting-cats.md`, `walking-mode.md` where
  it describes the button's look.
- **Out of scope:** what the walk button does, and where Undo sits; stat tiles (dropped, see the log).
- **Ships safely because:** the walk row keeps its behaviour and its no-jump rules.
- **Cleanup owed:** none.

### Slice E4 — Coat faces in Material shapes
- **In scope:** the coat grid's tiles and ring, shared by the Counter, the coat question and the map's
  filter; the filled split button; `coat.md`, `map.md` where it describes the filter's cells.
- **Out of scope:** the detail's picker (E7 replaces it).
- **Ships safely because:** the same taps reach the same intents; only the ring's outline moves.
- **Cleanup owed:** none.

### Slice E5 — The detail's photos as a carousel
- **In scope:** the carousel, its add items, the position label, the progress indicator, the no-photo
  block with its button group; `encounter-detail.md`, `photos.md`.
- **Out of scope:** the viewer; the outing pager around the page.
- **Ships safely because:** the same taps open the same viewer, camera and picker, for the same cat.
- **Cleanup owed:** `AddPhotoCard` and `DetailPhotoPager` go if nothing else uses them.

### Slice E6 — The detail names its cat
- **In scope:** a title token per coat; the facts row; More and its menu in the bar; the Remove button;
  EN and RU strings; `encounter-detail.md`.
- **Out of scope:** the cat's number (E9); the coat card (E7); the Where card (E8).
- **Ships safely because:** Remove sends the Delete intent; the removed state and its Undo are unchanged.
- **Cleanup owed:** none.

### Slice E7 — The coat card and the coat sheet
- **In scope:** the coat card; a coat-sheet key with `bottomSheet()` metadata; the header with the face or the
  photo; the *No coat* tile; set, clear and dismiss;
  `coat.md` § Changing it later; retiring `EncounterDetailCoatPickerTest`.
- **Out of scope:** the map's filter (E4 styled it); the coat question after a photo takes only the header.
- **Ships safely because:** the same `SetCoat` intent, reached from a sheet instead of a strip.
- **Cleanup owed:** `CoatPicker` goes if nothing else uses it.

### Slice E8 — Where you met
- **In scope:** the Where card; the accuracy circle on the spot map; the no-location alert under the facts,
  with the notice card moved to `ui/components`; `encounter-detail.md` § Its map and § Where it was found; a
  device check of the circle.
- **Out of scope:** the Map tab.
- **Ships safely because:** the card still opens the Map tab or the location picker as it does today.
- **Cleanup owed:** none.

### Slice E9 — The cat's number
- **In scope:** `ObserveEncounterNumber` and its DAO count; the number in the page's state; the label;
  EN and RU strings; `encounter-detail.md`.
- **Out of scope:** numbers anywhere but the detail.
- **Ships safely because:** additive; a read-only count.
- **Cleanup owed:** none.

### Slice E10 — Statistics as a dashboard
- **In scope:** `Stats.byDay` in `StatsCalculator`; the day series in `StatisticsState`; the chart card with
  its pill, the tiles, the coat share bars, the outings grid; `statistics.md`.
- **Out of scope:** Places; the empty state; the Counter.
- **Ships safely because:** the same numbers on the same tab; the chart is additive.
- **Cleanup owed:** none.

### Slice E11 — Reaching a rung
- **In scope:** the celebration when a tally lands on a rung of `Tuning.MILESTONES`: the cookie's bounce, the
  ring's glow, the "100 cats!" tag until the undo window closes, and its undo; `counting-cats.md`.
- **Out of scope:** the tags at rest (E2) and the walk row (E3).
- **Ships safely because:** it adds motion and a tag on a state the Counter already carries; nothing under the
  count moves.
- **Cleanup owed:** none.

### Slice E12 — Encounters in outing cards
- **In scope:** the headline and its totals; each outing as a card with its header (the day, the count and
  span labels, the walk chip, On the map as a pill); the grid's tiles in the coat shapes and the pair tiles'
  corners; the list's cards; the selection's ring, check and bar; the mapper's new labels and the walk
  overlap; `browsing-cats.md`.
- **Out of scope:** the packing rules, the selection's behaviour, delete and its undo, the Places list.
- **Ships safely because:** the same rows in the same order doing the same things; the labels are additive.
- **Cleanup owed:** none.

## Decision log

- 2026-09-27: the owner asked for an audit of the screens, mostly a cat's detail, to make them more
  expressive, with Airbnb and Drinkit as references, and a prototype before proposals. Six directions were
  prototyped over the Counter and the detail. The owner kept Listing, Night walk and Expressive, then chose
  Expressive as it stood in version 5 of the prototype.
- 2026-09-27: the outing pager's P3a-2 was in progress in another session, and P3b to P5b change the same
  detail screen. The Counter slices, E1 to E4, go first; a detail slice starts only when no outing pager
  branch is open, so the two epics never edit `EncounterDetailScreen.kt` or `encounter-detail.md` in
  parallel.
- 2026-09-27: the owner found the Counter's cookie "very small" in the prototype once the prototype drew
  it honestly, with only the room the controls leave. Version 5's stat tiles, separate milestone line and
  62 dp coat shapes left 140 px; the Roomy layout (no tiles, the milestone in the outing line's slot,
  52 dp shapes) leaves about 300. E3 drops the tiles, E2 shares the slot, E4 keeps the grid no taller.
- 2026-09-27: the owner found the Immersive direction's stats "really convenient for the Stats page" and
  asked for the Stats tab to be redesigned too; prototype version 12 draws it, and the spec's section 5 and
  E10 record it.
- 2026-09-27: the owner said "work in autonomous mode": the spec's open details stand as decided, and each
  slice is planned, built, reviewed and gated in turn.
- 2026-09-27: the owner found the milestone line under the cookie ("38 more to reach 100") foreign, and the
  slot it kept empty. Five treatments were prototyped (Line, Ring, Cookie, Chip, Moments; prototype version 13)
  and the owner chose **Ring**: the goal pinned where the ring closes with a dot at the arc's head, the outing on
  a tag at the ring's bottom, and no line under the count. Folded into E2 while its gate was still open; the
  celebration on reaching a rung is slice E11.
- 2026-09-27: the owner asked for the coat sheet (from the coat card's Change, and after a photo) to be restyled
  and reviewed; prototype version 14 draws it with the cat's face in the header and E4's shaped tiles. Between
  "tap the current coat again" and a *No coat* tile, the owner chose the tile. E7 carries it.
- 2026-09-27: the owner asked for a no-location alert on a cat's page like the Counter's import notice, and a
  prototype of Encounters. Prototype version 15 drew both; the owner put the alert under the title ("under")
  rather than at the top, and approved the Encounters drawing ("looks ok"). The alert joins E8; Encounters is
  E12.
