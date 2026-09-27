# Slice E15 — The import notice floats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The import's progress and summary are one floating card over the top of the count — the first photos, one muted line, a tonal Undo, × and a swipe to close — and the cookie keeps its size.

**Architecture:** `ImportStatus.kt`'s two notice cards become `ImportIsland`: a `Surface` on `surface` with a shadow and `large` corners in a `SwipeToDismissBox` (both directions → dismiss) while finished. `CounterScreen` wraps `FillOrScroll` in a `Box` and draws the island over it at the top centre, 12 dp in from the sides; `FillOrScroll`'s `above` keeps only the location hint. The summary carries up to three resolved thumbnails: a `:domain` use case `FindCatThumbnails` reads the added cats' covers, the Store asks it when a run finishes and the mapper resolves the paths.

**Tech Stack:** Compose Material 3 Expressive (`SwipeToDismissBox`, `LinearWavyProgressIndicator`, `FilledTonalButton`, `IconButton`), Coil `AsyncImage`, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2 *The import notice*. Map: row E15 and *Slice E15*. Criteria: `expressive-e15.md`.

## Global Constraints

- "It floats … over the top of the count rather than in the column … the cookie keeps its size."
- "Finished: *9 cats added* in `titleMedium`, and under it one muted line with only the parts that apply, *2 already here · 1 couldn't be read*; TalkBack reads the words as one item."
- "Undo is the filled tonal button, and a × icon button after it closes the notice; a swipe to either side closes it too. Closing does what OK did … OK itself goes. The running notice has no controls. A tap on the card's body does nothing."
- Strings EN and RU. Gradle only through `ctx_execute`; `--rerun --no-build-cache`; counts from JUnit XML; per-commit compiles.

## Deviation, decided here

- **The running card shows the gallery icon, not a stack that fills.** The Store learns which cats a run added only when it finishes (`Import.Finished.addedIds`); progress reports only counts. The photos appear on the finished card.

---

### Task 0: Freeze the criteria (`expressive-e15.md`)

### Task 1: The thumbnails (`:domain`, `:presentation`)

- `domain/…/usecase/FindCatThumbnails.kt`: `suspend operator fun invoke(ids: List<String>, limit: Int): List<String>` — the cover thumbnail of each id in order, skipping cats without one, at most `limit`. Test with `FakeEncounterRepository`: order, skipping, the limit, an unknown id.
- `ImportSummaryState.thumbPaths: ImmutableList<String> = persistentListOf()`; `CounterStateMapper.importSummary(…, thumbPaths)` resolves through `PhotoStorage`. The Store, on `Import.Finished`, sets the summary, then asks `FindCatThumbnails(addedIds, 3)` and puts the paths on the summary if the same run is still shown. Tests: mapper whole-state; Store `a finished import shows the first three added cats' photos`.
- `DomainModule` binds the use case; `KoinModulesTest` covers the Store's new parameter.

### Task 2: The island (`:ui`, `:app` tests)

- Failing tests `app/…/counter/ImportIslandTest.kt` (replacing the import half of `CounterNoticesTest`): the count the same size with and without the island (progress and summary); running text and wavy progress, no buttons; finished headline and the one line of what applies (three cases); Undo and × route to their callbacks and never to the tally's Undo; an undone import shows no Undo and no OK; a swipe left and a swipe right each dismiss; the body has no click action; three thumbnails for three or more paths, the check icon for none.
- `ImportIsland(progress, summary, onUndoClick, onDismiss)` in `ImportStatus.kt`; `CounterScreen` overlay. Strings: `counter_import_running_count`, `counter_import_parts`, `counter_import_close`; `counter_import_skipped` EN "already here"; `counter_import_ok` and `counter_import_running` go.

### Task 3: The record

`import.md` (the card, OK gone, × and the swipe, photos, the running icon), `counting-cats.md` (the notices paragraph), spec § 2 (the deviation), map row E15 `in-review`, the decision log (the walk keeps its peach).

### Task 4: Renders, review, gate

Renders before and after (light, dark, font 1.5; running, finished, undone); `/code-review`; the gate with mutations; the acceptance gate.
