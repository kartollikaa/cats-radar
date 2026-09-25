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
| D1 | Places on the card rhythm | Every level of the drill-down gets a headline with its name and count, its rows in a titled card with share bars and chevrons, and its cats in the Encounters list layout. | safe | ~600 | — | planned |
| D2 | Import status as a card | The Counter's import progress and summary become low-surface cards with a leading icon. | safe | ~250 | — | planned |
| D3 | One layout for the coat sheets | The coat question after a photo and the map's coat filter share a header, the grid and an action row; Not specified becomes a cell of the filter's grid. | safe | ~350 | — | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice D1 — Places on the card rhythm
- **In scope:** `RegionView.self` in `ObserveRegion`; `RegionsState` header, section and share; the cats as
  `EncountersRow`s, removing `EncounterListItem` and `mapList`; `RegionsScreen` restyled; `EncounterRows`'
  leading item; the entry's On the map; the location-pin icon; EN/RU strings; `places.md`, `app-shell.md`.
- **Out of scope:** a top bar with a back arrow (after #146); the Encounters grid on this screen.
- **Ships safely because:** same screen, same navigation; the one new action is the On the map every other
  outing header already has.
- **Cleanup owed:** none.

### Slice D2 — Import status as a card
- **In scope:** `ImportProgress` and `ImportSummary` restyled; one EN/RU string; `import.md`,
  `counting-cats.md` where they describe the Counter's lines.
- **Out of scope:** what the lines say and when they appear.
- **Ships safely because:** layout only.
- **Cleanup owed:** none.

### Slice D3 — One layout for the coat sheets
- **In scope:** a shared sheet header in `:ui`; `CoatPromptSheet` and `MapCoatSheet` on it; the grid's
  Not-specified cell for the filter; EN/RU strings; `coat.md`, `map.md`.
- **Out of scope:** `MapCoatSheet` as a Navigation 3 destination (behaviour, its own follow-up); the coat
  grid on the Counter and the detail's picker.
- **Ships safely because:** the same taps reach the same intents.
- **Cleanup owed:** none.

## Decision log

- 2026-09-25: the owner asked for a design pass over the screens the earlier iterations did not touch, in
  autonomous mode, without merging. The audit is in the spec: 23a–23d reached the theme, coats, Counter,
  Encounters, detail, Statistics and Settings, and everything built later used their vocabulary except
  Places, the Counter's import lines and the two coat sheets. The photo viewer's and detail's top bar is
  #146's. `app-shell.md`'s *Not handled yet*, and the roadmap line copied from it, were stale.
