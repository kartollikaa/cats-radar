# Outing pager — PR Decomposition Map

- **Created:** 2026-09-25
- **Epic reference:** [docs/superpowers/specs/2026-09-25-outing-pager-design.md](../../superpowers/specs/2026-09-25-outing-pager-design.md)
- **Trunk:** `main`
- **Size budgets:** target ≤600 reviewable lines, cap 1000 (see tbd:sizing-pull-requests). Excluded from the
  count: `*.md`, screenshot baselines.
- **Integration strategy:** every slice is **naturally safe**. Until P3 the screen still shows one cat; until
  P4 a delete behaves as today; until P5b the only way to another outing is TalkBack's action.

## Slices

| # | PR title | Purpose (one sentence) | Strategy | Size budget | Depends on | Status |
|---|----------|------------------------|----------|-------------|------------|--------|
| P1 | The outing window in the domain | `outingWindow(encounters, shown)` returns the pages and both neighbouring outings; nothing calls it yet. | safe | ~250 | — | planned |
| P2 | The detail screen's intents and effects name their cat | Every per-cat intent and effect carries the cat's id, and camera and picker results keep theirs across process death. | safe | ~350 | — | planned |
| P3 | The detail screen pages through its outing | One Store serves the outing; a pager keyed by cat id, the position in the bar, restore by id. | safe | ~800 | P1, P2 | planned |
| P4 | A delete leaves the pager with an undo bar | The deleted cat leaves the pages, the neighbour shows, and an undo bar replaces the *removed* state except for the last cat. | safe | ~500 | P3 | planned |
| P5a | Moving to the neighbouring outing | Neighbours in state, `OutingEdgeReleased`, the slide keyed by the jump counter, TalkBack's *Newer/Older outing*. | safe | ~400 | P3 | planned |
| P5b | Stretching past the edge opens the next outing | The pull on the pager's nested scroll: the give, the label, the threshold haptic, cancel, RTL. | safe | ~550 | P5a | planned |

Status values: `planned · in-progress · in-review · merged · dropped`

## Slice details

### Slice P1 — The outing window in the domain
- **In scope:** `OutingWindow` and `outingWindow` beside `SessionSplitter`; `OutingWindowTest`; `outings.md`
  (the window, its consumer list, and the stale *Not handled yet* that still says `StatsCalculator` is missing).
- **Out of scope:** any caller.
- **Ships safely because:** nothing calls it.
- **Cleanup owed:** none.

### Slice P2 — The detail screen's intents and effects name their cat
- **In scope:** the cat id on `CoatPicked`, `TakePhotoClicked`, `PickPhotoClicked`, `PhotoTaken`, `PhotoPicked`,
  `PhotoClicked`, `CoordinatesClicked` and on `OpenCamera`, `OpenPhotoPicker`, `OpenPhoto`, `OpenMap`;
  `PendingCaptures` saving each target with its cat id, and restoring the old shape without inventing one; the
  picker saving the id it opened for; the nav host opening the viewer and the map by the effect's id;
  `encounter-detail.md`.
- **Out of scope:** more than one cat on screen.
- **Ships safely because:** every id is still `key.id`.
- **Cleanup owed:** none.

### Slice P3 — The detail screen pages through its outing
- **In scope:** the Store collecting `ObserveEncounters` through `outingWindow`, with the anchor and the shown
  set, starting from `restoredId`, `openedId` or *Missing*; `CatPage`; `PageSettled`; per-cat attaching and one
  screen-wide *waiting* flag; a cat deleted elsewhere leaving the pages; the pager keyed by id and following
  `currentId`; "2 / 5" and "Cat 2 of 5" in EN/RU; the destination saving the anchor; the Store and entry tests
  rewritten around ids; `encounter-detail.md`, `browsing-cats.md`, `map.md`, `places.md`, `photo-viewer.md`,
  `coat.md`.
- **Out of scope:** the undo bar (P4); neighbours and jumps (P5a).
- **Ships safely because:** a delete still shows the *removed* state and closes the screen, as today.
- **Size:** over target because `EncounterDetailStoreTest` is rebuilt on a whole-table fake; the Store and its
  tests cannot change shape separately.
- **Cleanup owed:** none.

### Slice P4 — A delete leaves the pager with an undo bar
- **In scope:** `UndoBar` moving to `ui/components` with its message as a parameter; the delete rules of the
  spec (neighbour, a second delete replacing the undo, failed delete and undo, the last cat's *removed* state);
  EN/RU strings; `encounter-detail.md` *Delete and undo*, `browsing-cats.md` (the bar's path).
- **Out of scope:** jumps.
- **Ships safely because:** it replaces one delete behaviour with another, whole.
- **Cleanup owed:** none.

### Slice P5a — Moving to the neighbouring outing
- **In scope:** `NeighbourOuting` with the Encounters header extracted into one shared label function;
  `OutingEdgeReleased`; the landing cat; `jump` and `jumpDirection`; the `AnimatedContent` slide with the
  outgoing pager disabled; TalkBack's *Newer outing* and *Older outing* on each page; EN/RU strings;
  `encounter-detail.md`.
- **Out of scope:** the pull gesture.
- **Ships safely because:** only an accessibility action reaches it.
- **Cleanup owed:** none.

### Slice P5b — Stretching past the edge opens the next outing
- **In scope:** the pull as a `NestedScrollConnection` around the pager with its overscroll off; the give, the
  label in the gap, the threshold haptic once per crossing, spring-back, a cancelled gesture never jumping, RTL;
  the screen tests of the spec; `encounter-detail.md`.
- **Out of scope:** anything the Store does (P5a).
- **Ships safely because:** it adds a gesture for an intent that already exists.
- **Cleanup owed:** none.

## Decision log

- 2026-09-25: owner asked for a pager across an outing's cats with a stretch past its ends that moves to the
  neighbouring outing. Reviews before planning moved the stretch off `OverscrollEffect` (never called for one
  page, bypassed by the coat row), put the window in `:domain`, and found that camera and picker results carry
  no cat, which P2 fixes before any second cat is on screen. Owner chose **no split on delete in the pager
  only**, and a **cover with a count** for many photos on the detail screen (M7 in
  [2026-09-25-many-photos-per-cat.md](./2026-09-25-many-photos-per-cat.md)).
- Ordering against many photos per cat: P2 and M6 both change `OpenPhoto`; whichever lands second adds its id
  to the other's. M7 builds on P3's `CatPage` if P3 lands first.
