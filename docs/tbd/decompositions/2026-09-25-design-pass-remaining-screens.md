# Design pass on the remaining screens — PR Decomposition Map

- **Created:** 2026-09-25
- **Epic reference:** [docs/superpowers/specs/2026-09-25-design-pass-remaining-screens-design.md](../../superpowers/specs/2026-09-25-design-pass-remaining-screens-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, string resources.
- **Integration strategy:** every slice is **naturally safe** — each restyles one surface in place, keeps
  what it does, and depends on no other slice, so the three can land in any order.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| D1 | Places on the card rhythm (#149) | Every level of the drill-down gets a headline with its name and count, its rows in a titled card with share bars and chevrons, and its cats in the Encounters list layout. | safe | ~600 | — | in-review |
| D2 | Notices on the Counter as cards (#155) | The Counter's import progress and summary, and the location hint beside them, become one low-surface notice card with a leading icon. | safe | ~250 | — | in-review |
| D3 | One layout for the sheets (#157) | The coat question after a photo, the map's coat filter and the spot list share a header and an action row; Not specified becomes a cell of the filter's grid. | safe | ~350 | — | in-review |
| D4 | A back arrow on Places | Each level of the drill-down gets the pinned back arrow the cat's detail has, now that `CenterAppBar` is on `main`. | safe | ~250 | D1, #146 | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice D1 — Places on the card rhythm
- **In scope:** `RegionView.self` in `ObserveRegion`; `RegionsState` header, section and share; the cats as
  `EncountersRow`s, removing `EncounterListItem` and `mapList`; `RegionsScreen` restyled; `EncounterRows`'
  leading item; the entry's On the map; the location-pin icon; EN/RU strings; `places.md`, `app-shell.md`.
- **Out of scope:** a top bar with a back arrow (D4); the Encounters grid on this screen.
- **Ships safely because:** same screen, same navigation; the one new action is the On the map every other
  outing header already has.
- **Also landed (review):** `EmptyState` and `HeadlineCard` in `ui/components`, used by Statistics,
  Encounters and the Map too; an optional long press on cat cards, so the lists without selection offer
  none; `ObserveRegion` on a compute dispatcher.
- **Cleanup owed:** none.

### Slice D2 — Notices on the Counter as cards
- **In scope:** `ImportProgress` and `ImportSummary` restyled; one EN/RU string; `import.md`,
  `counting-cats.md` where they describe the Counter's lines.
- **Also landed (review):** the location hint, which shares that slot, on the same `NoticeCard`; each
  notice reads to TalkBack as one item.
- **Out of scope:** what the lines say and when they appear.
- **Ships safely because:** layout only.
- **Cleanup owed:** none.

### Slice D3 — One layout for the sheets
- **In scope:** a shared sheet header in `:ui`; `CoatPromptSheet` and `MapCoatSheet` on it; the grid's
  Not-specified cell for the filter; EN/RU strings; `coat.md`, `map.md`.
- **Also landed (review):** the spot list on the same header, `SheetActions`, and the sheet look in
  `app-shell.md`; the sheets' contents (`CoatPrompt`, `MapCoatFilter`) are public.
- **Out of scope:** `MapCoatSheet` as a Navigation 3 destination (behaviour, its own follow-up); the coat
  grid on the Counter and the detail's picker.
- **Ships safely because:** the same taps reach the same intents.
- **Cleanup owed:** none.

### Slice D4 — A back arrow on Places
- **In scope:** a `CenterAppBar` with the pinned back arrow over each level, the way #146 gave it to the
  cat's detail, with a back that pops only its own level; `places.md`.
- **Out of scope:** a title in the bar; the headline already names the level.
- **Ships safely because:** additive; system back keeps working.
- **Cleanup owed:** none.

## Decision log

- 2026-09-25: the owner asked for a design pass over the screens the earlier iterations did not touch, in
  autonomous mode, without merging. The audit is in the spec: 23a–23d reached the theme, coats, Counter,
  Encounters, detail, Statistics and Settings, and everything built later used their vocabulary except
  Places, the Counter's import lines and the two coat sheets. The photo viewer's and detail's top bar is
  #146's. `app-shell.md`'s *Not handled yet*, and the roadmap line copied from it, were stale.
- 2026-09-25: the slices widened in review, each within its own surface: D1 took the shared empty and
  headline cards, D2 the location hint, D3 the spot list's header. #146 landed while D1 was in review,
  so the back arrow it was waiting for is D4 rather than a change to a reviewed, gated PR.
