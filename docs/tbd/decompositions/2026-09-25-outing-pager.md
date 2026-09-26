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
| P1 | The outing window in the domain | `outingWindow(encounters, shown)` returns the pages and both neighbouring outings; nothing calls it yet. | safe | ~250 | — | merged |
| P2 | The detail screen's intents and effects name their cat | Every per-cat intent and effect carries the cat's id, and camera and picker results keep theirs across process death. | safe | ~350 | — | merged |
| P3a-1 | The detail state holds pages | `Loaded` carries `pages` of `CatPage` and the cat on screen; the screen draws that page and every tap names it; the Store still reads one cat. | safe | ~630 | P2 | in-review |
| P3a-2 | One Store serves the outing | The Store reads the outing through `outingWindow` with `OutingPages` (anchor and shown set); per-cat attaching; a cat deleted elsewhere hands over to its neighbour. | safe | ~700 | P3a-1 | planned |
| P3b | The detail screen pages through its outing | The pager keyed by cat id, following `currentId`; "2 / 5" in the bar; `PageSettled`; restore by id. | safe | ~300 | P3a-2 | planned |
| P4 | A delete leaves the pager with an undo bar | The deleted cat leaves the pages, the neighbour shows, and an undo bar replaces the *removed* state except for the last cat. | safe | ~500 | P3b | planned |
| P5a | Moving to the neighbouring outing | Neighbours in state, `OutingEdgeReleased`, the slide keyed by the jump counter, TalkBack's *Newer/Older outing*. | safe | ~400 | P3b | planned |
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
- **In scope:** the cat id on `CoatPicked`, `TakePhotoClicked`, `PickPhotoClicked`, `PhotoTaken`, `PhotosPicked`,
  `PhotoClicked` (beside its `photoId`), `CoordinatesClicked` and on `OpenCamera`, `OpenPhotoPicker`, `OpenPhoto`
  (beside its `photoId`), `OpenMap`;
  `PendingCaptures` saving each target with its cat id, and restoring the old shape without inventing one; the
  picker saving the id it opened for; the nav host opening the viewer and the map by the effect's id;
  `encounter-detail.md`.
- **Out of scope:** more than one cat on screen.
- **Ships safely because:** every id is still `key.id`.
- **Cleanup owed:** none.

### Slice P3a-1 — The detail state holds pages
- **In scope:** `CatPage` (per cat: id, the detail labels, photos, the photo and location offers, the place);
  `Loaded(pages, currentId, currentNumber, …)`; the mapper building pages from an `OutingWindow`; the Store wrapping
  its one observed cat in a one-cat window; the screen drawing the page for `currentId` and every tap naming its cat
  (`*Interaction` payloads where a callback carries two values); the destination dispatching the page's id instead of
  `key.id`; the screen and mapper tests migrated; `encounter-detail.md`.
- **Out of scope:** more than one cat in the window (P3a-2); the pager (P3b).
- **Ships safely because:** the window always holds exactly the observed cat, so the screen looks and behaves as today.
- **Cleanup owed:** none.

### Slice P3a-2 — One Store serves the outing
- **In scope:** `EncounterDetailStore(openedId, restoredId, …)` collecting `ObserveEncounters` through
  `outingWindow`; `OutingPages` (anchor, shown set, hand-over to the next older else newer page, `settle`,
  `release`) with its own tests; start from `restoredId`, `openedId` or *Missing*; per-cat attaching including the
  several-photos batch across a swipe; one screen-wide *waiting* flag; the tap guards widened from "the observed
  cat" to "a cat on the pages"; each page's place; Koin binding and `KoinRuntimeResolutionTest`; the Store tests on
  multi-cat outings; `encounter-detail.md`, `outings.md`.
- **Out of scope:** the pager and `PageSettled` from the screen (P3b); the undo bar (P4).
- **Ships safely because:** the screen still draws one page; the visible change is that a cat deleted elsewhere
  hands the screen to its neighbour instead of *Missing*.
- **Cleanup owed:** none.

### Slice P3b — The detail screen pages through its outing
- **In scope:** the `HorizontalPager` keyed by cat id with its own overscroll off, following `currentId` and
  reporting a settled page only when its cat differs; each page nesting its photo pager; "2 / 5" and "Cat 2 of 5" in
  EN/RU; the destination saving the anchor and passing it as `restoredId`; entry tests for the viewer, the map and the
  location picker opening on the cat swiped to; `encounter-detail.md`, `browsing-cats.md`, `map.md`, `places.md`,
  `photo-viewer.md`, `coat.md`.
- **Out of scope:** the undo bar (P4); neighbours and jumps (P5a).
- **Ships safely because:** a delete still shows the *removed* state and closes the screen, as today.
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
  a pull that starts on a photo; the screen tests of the spec; `encounter-detail.md`.
- **Out of scope:** anything the Store does (P5a).
- **Ships safely because:** it adds a gesture for an intent that already exists.
- **Cleanup owed:** none.

## Decision log

- 2026-09-25: owner asked for a pager across an outing's cats with a stretch past its ends that moves to the
  neighbouring outing. Reviews before planning moved the stretch off `OverscrollEffect` (never called for one
  page, bypassed by the coat row), put the window in `:domain`, and found that camera and picker results carry
  no cat, which P2 fixes before any second cat is on screen. Owner chose **no split on delete in the pager
  only**.
- 2026-09-26: many photos per cat shipped its detail-screen photo pager (M7, #169) before this map's
  amendment (a cover with a count) could land, and the owner **kept the photo pager**. The amendment left
  #166; each cat page nests the photo pager, and a drag past a cat's last photo moves on to the next cat.
  P2 adds the cat's id beside M7's `photoId`. M8 (several photos at once) and P2 both change what the
  picker hands back; whichever lands second carries the other's change.
- 2026-09-26: **P1 merged** as #166 (~190 reviewable lines of Kotlin). Its gate caught that cats sharing an
  `occurredAt` made the window depend on input order; `groupByOuting` now orders them by when they were
  recorded, then by id (#172), and the window relies on that.
- 2026-09-26: **P2 merged** as #183 (~880 reviewable lines, three quarters tests). It merged main twice and carried
  the cat into what landed meanwhile — M8's several-photos picker and L2's *Set on map*. Taps act only on the cat the
  screen shows until P3a-2 widens that to the pages. The plan review for P3 measured it at ~1,230 lines, so it splits
  into P3a-1, P3a-2 and P3b.
