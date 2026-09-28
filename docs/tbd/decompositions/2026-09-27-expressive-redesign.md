# Expressive redesign of the Counter and a cat's detail — PR Decomposition Map

- **Created:** 2026-09-27
- **Epic reference:** [docs/superpowers/specs/2026-09-27-expressive-redesign-design.md](../../superpowers/specs/2026-09-27-expressive-redesign-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, string resources.
- **Integration strategy:** every slice is **naturally safe**. Each changes one surface in place and ships
  whole; the behaviour changes, E6's Remove, E7a's way back to one tap and E7b's coat sheet, are complete
  inside their slices. E1 goes first because every other slice draws on its theme.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| E1 | The Expressive theme | `CatsRadarTheme` draws with `MaterialExpressiveTheme` and the expressive motion scheme, and `app-shell.md` stops saying it cannot. | safe | ~150 | — | merged |
| E2 | The count in a cookie, with its milestone | The Counter's number sits in a twelve-sided cookie with an arc toward the next milestone; the goal and the outing ride tags on the ring, and nothing sits under the count. | safe | ~450 | E1 | merged |
| E3 | The walk row | The walk button as an extended FAB and Undo as a tonal button. | safe | ~250 | E2 | merged |
| E4 | Coat faces in Material shapes | Every coat grid draws its faces in a shape per column, with the ring on the shape; Photo is the filled split button. | safe | ~350 | E1 | merged |
| E5 | The detail's photos as a carousel | A cat's photos become a multi-browse carousel ending in the two add items, and a cat without one opens on its face and a button group. | safe | ~600 | E1 | planned |
| E6 | The detail names its cat | The title from the coat, the facts row, More in the bar, and Remove this cat in place of Delete. | safe | ~500 | E1 | planned |
| E7a | The coat question after a photo | The sheet after a photo keeps the photo in its header through a count, names its mode with a *One cat · Several* group that also leads back to one tap, counts cats in their shapes, and calls the paw *No coat*. | safe | ~450 | E4 | merged |
| E7b | The coat card and the coat sheet | The detail's coat is a card that opens a sheet destination, with the cat's face in E7a's header and a *No coat* tile, which replaces the inline picker. | safe | ~450 | E6, E7a | planned |
| E8 | Where you met | The Where card restyled around its map, with the fix's accuracy drawn to scale around the dot; a cat with no location gets the notice-card alert under its facts instead of the card. | safe | ~450 | E6 | planned |
| E9 | The cat's number | Each cat's place in the live log, oldest first, opens the facts row. | safe | ~400 | E6 | planned |
| E10 | Statistics as a dashboard | The Stats tab gets the per-day chart with its range pill, stat tiles, coat share bars and an outings grid. | safe | ~600 | E1 | planned |
| E11 | Reaching a rung | The cookie bounces and the ring glows when a tally lands on a milestone, and the bottom tag says "100 cats!" until the run closes. | safe | ~250 | E2 | merged |
| E12a | Encounters in outing cards | Each outing becomes a card headed by its day, count, span and walk, with On the map as a pill; the headline totals the tab. | safe | ~550 | E1 | in-review |
| E12b | The cats inside the outing cards | Tiles take the coat shapes, pair tiles their chip, the short run's and the list's cards their new look, and the selection its ring and bar. | safe | ~450 | E4, E12a | in-review |
| E13 | The cookie turns with each cat | Each logged cat turns the Counter's cookie a step further and it keeps the turn; an undo turns it back. | safe | ~150 | E4 | merged |
| E14 | The walk beside Photo | Walk is a tonal button beside Photo that turns warm and reads *Hold to end* while a walk is on; the cookie wears the walk and breathes; Undo floats in the count block. | safe | ~550 | E13 | merged |
| E15 | The import notice floats | The import's progress and summary are a floating card over the count with the first photos, one muted line, Undo, × and a swipe to close; the cookie keeps its size. | safe | ~450 | E14 | merged |
| E16 | The location hint floats | The location hint is a floating card like the import's, with Grant, × and a swipe; it stacks under an import's card, and the count keeps its size. | safe | ~350 | E15 | merged |
| E17 | The running import shows its photos | While an import runs, its card fans out the picked photos as the run reaches them, and every stacked photo wears a ring in the card's colour. | safe | ~250 | E16 | merged |

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
- **Out of scope:** the detail's picker (E7b replaces it).
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
- **Out of scope:** the cat's number (E9); the coat card (E7b); the Where card (E8).
- **Ships safely because:** Remove sends the Delete intent; the removed state and its Undo are unchanged.
- **Cleanup owed:** none.

### Slice E7a — The coat question after a photo
- **In scope:** the Counter's coat prompt: the header with the photo, or the paw, through the count; the
  *One cat · Several* connected group and `OneCatClicked`, which empties the tray; the tray in coat shapes; the
  paw tile's *No coat*; `coat.md` § Asked after a photo and § Several cats on the photo.
- **Out of scope:** the detail's coat card and sheet (E7b); the map's filter keeps *Not specified*; what saving
  writes.
- **Ships safely because:** the same prompt state and writes; one new intent that only clears the tray.
- **Cleanup owed:** none.

### Slice E7b — The coat card and the coat sheet
- **In scope:** the coat card; a coat-sheet key with `bottomSheet()` metadata; E7a's header with the cat's face;
  the *No coat* tile; set, clear and dismiss;
  `coat.md` § Changing it later; retiring `EncounterDetailCoatPickerTest`.
- **Out of scope:** the map's filter (E4 styled it); the coat question after a photo (E7a).
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
- **In scope:** the celebration when the count lands on a rung of `Tuning.MILESTONES`: the cookie's bounce, the
  ring's glow, the "100 cats!" tag until the undo window closes, and its undo; the milestone toast and its effect
  go; `counting-cats.md` § Milestones.
- **Out of scope:** the tags at rest (E2) and the walk row (E3).
- **Ships safely because:** it adds motion and a tag on a state the Counter already carries; nothing under the
  count moves.
- **Cleanup owed:** none.

### Slice E12a — Encounters in outing cards
- **In scope:** the headline and its totals; each outing as a card with its header (the day, the count and
  span labels, the walk chip, On the map as a pill); the mapper's new labels, the closing row and the walk
  overlap; cards inside the card on `surface`; `browsing-cats.md`.
- **Out of scope:** the cats inside the card (E12b); the packing rules, the selection's behaviour, delete and
  its undo; the Places list and the spot sheet, which keep their rows.
- **Ships safely because:** the same rows in the same order doing the same things; the labels are additive.
- **Cleanup owed:** none.

### Slice E12b — The cats inside the outing cards
- **In scope:** the grid's tiles in the coat shapes with the face, the paw or the photo clipped to the shape;
  the pair tiles' corners and dark time chip; the short run's and the list's cards (the tile's shape, the
  coat's name or "A cat", time and place, "No location yet" in `onTertiaryContainer`); the selection's ring,
  check and `primaryContainer` bar; `browsing-cats.md`.
- **Out of scope:** the card and its header (E12a); what selecting and deleting do.
- **Ships safely because:** the same cells doing the same things; only their drawing changes.
- **Cleanup owed:** none.

### Slice E13 — The cookie turns with each cat
- **In scope:** the cookie's turn from the count (`CookieTurn.kt`), in place of the press's turn that sprang back;
  `counting-cats.md`, spec § 2 *The count*.
- **Out of scope:** the squash, the roll, the tags.
- **Ships safely because:** drawing only; the block, its taps and its label are unchanged.
- **Cleanup owed:** none.

### Slice E14 — The walk beside Photo
- **In scope:** the Walk button beside Photo with its hold and its hint; the cookie's warm fill and breath
  during a walk; Undo in the count block's corner; the walk row and the walk's own time on the Counter go;
  `counting-cats.md`, `walking-mode.md`, spec § 2 *The walk*.
- **Out of scope:** the notification and its chronometer, the outing tag, the coat grid, Photo's halves.
- **Ships safely because:** the walk's start and hold reach the same intents; the outing tag already carries the
  outing; a walk started on the old build shows on the new button.
- **Cleanup owed:** `WalkRow.kt` and its width rules go with the row; `counter_walk_start_hint`,
  `counter_walk_stop_hint` and `counter_walk_stop_hint_timed` go with the two-line button.

### Slice E15 — The import notice floats
- **In scope:** the floating import card with the photo stack, the wavy progress, the one-line summary, Undo, ×
  and the swipe; OK goes; the run is recorded as dealt with on close; `import.md`, `counting-cats.md`, spec § 2
  *The import notice*.
- **Out of scope:** the location hint card, what an import writes, the imported-photos manager the tap is
  reserved for.
- **Ships safely because:** the same run, summary and undo behind a new surface; the timeout is unchanged.
- **Cleanup owed:** `ImportStatus.kt`'s two cards; `counter_import_ok` if nothing else uses it.

### Slice E16 — The location hint floats
- **In scope:** the location hint as a floating card sharing the import's `IslandCard`; Grant, × and the swipe;
  the two cards stacked at the top; `counting-cats.md`'s notices paragraph; spec § 2 *The location hint*.
- **Out of scope:** when the hint shows and what Grant and Dismiss do; the detail's no-location alert (E8), which
  keeps `NoticeCard`.
- **Ships safely because:** the same state and the same two actions behind a new surface.
- **Cleanup owed:** none; `NoticeCard` stays for E8.

### Slice E17 — The running import shows its photos
- **In scope:** the worker's progress carrying its batch's first photos; the running card's stack filling by progress;
  the ring on every stacked photo; `import.md`; spec § 2 *The import notice*.
- **Out of scope:** what an import writes; which photos the finished card shows; the imported-photos manager.
- **Ships safely because:** the same run and the same counts; a report without photos shows the gallery icon as before.
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
  "tap the current coat again" and a *No coat* tile, the owner chose the tile. E7b carries it.
- 2026-09-27: the owner asked for a no-location alert on a cat's page like the Counter's import notice, and a
  prototype of Encounters. Prototype version 15 drew both; the owner put the alert under the title ("under")
  rather than at the top, and approved the Encounters drawing ("looks ok"). The alert joins E8; Encounters is
  E12.
- 2026-09-27: E12 is split into E12a (the outing card, its header and the headline) and E12b (the cats inside the
  card). The Places list and the map's spot sheet share the rows and keep today's look, so the rows carry two drawing
  paths, which one slice would have carried past the size cap.
- 2026-09-27: before merging, the owner asked that each tally turn the Counter's cookie a step further and keep
  it there (it sprang back), which is E13; and that every coat have its own shape rather than one per column, then
  that the spiky shapes be calmer, which E4 carries.
- 2026-09-27: the owner found the walk button "a mess and very awkward". Three takes were prototyped (versions 17
  to 20): a pill row, Walk beside Photo and the walk on the ring's tag; then, reading the walk as a mode, a toggle at
  the top, an icon toggle and a session bar; then the warm cookie as a control of its own. The owner liked the ring,
  Walk beside Photo and the pill, and the warm cookie "maybe pulsating"; the ring was set aside for misses, and the
  owner took **With Photo** with the **warm, breathing cookie**. E14 carries it.
- 2026-09-27: the owner asked to revisit the successful import's block. Of the Photo card, a dock by Photo and the
  import landing on the count (versions 21 to 23), the owner chose the **Photo card**, asked that the finished notice
  be closable (OK or a swipe) with its tap booked for an imported-photos manager, and that it not shrink the cookie:
  "that island will be like a popup". E15 carries it.
- 2026-09-28: the owner asked for the walk in orange, yellow and blue; prototyped (version 24), then set aside:
  "lets do it without recoloring for now". The walk keeps the theme's tertiary colours.
- 2026-09-28: the owner passed E13's and E14's look checks and said "merge" after E15's renders. **E13, E14 and E15
  merged** as #222, #224 and #227, bottom first; each upper branch took the pinned main that its lower PR left, a
  tree identical to its gated head, before it merged. The plans of E1 to E4, E14 and E15 are archived.
- 2026-09-28: the owner asked whether the coat selection sheet could be redesigned to match. The sheet after a photo
  had grown a count since version 14 drew it, so E7 split: E7a, that sheet, on the Counter now; E7b, the detail's coat
  card and sheet, with the other detail slices. Of *Header*, *One or several* and *On the photo* (prototype version 25)
  the owner picked **One or several**. E7a carries it.
- 2026-09-28: the owner asked that the location hint be "like the import popup", reversing E15's line that what
  lasts sits in the column. E16 carries it.
- 2026-09-28: the owner asked that the import card show the photos "as in the prototype", where the running card's
  stack filled as the run went; E15 had kept the gallery icon while a run goes. E17 carries it.
- 2026-09-28: the owner said "merge them". **E7a, E11, the "+N" badge fix, E16 and E17 merged** as #229, #231, #234,
  #235 and #236, in that order. Each tree was built and gated before it landed; the badge fix's `TallyBlock` resolution
  kept E11's rung moment and the badge's new place, and was reviewed and re-audited. The plans of E7a, E11, E16 and
  E17 are archived.
