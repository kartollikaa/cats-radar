# Slice E17 — the running import shows its photos

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** while an import runs, its card shows the picked photos in the fanned stack, filling as the run reaches
them, and every stacked photo wears a thin ring in the card's colour, as prototype version 23 drew it.

**Architecture:** the worker adds its batch's first photos to each progress report, so a Counter that comes back
mid-run reads them from the work rather than from memory; `Import.PhotosPicked` gives the same photos before the
worker's first report. `CounterStateMapper.importProgress` keeps the photos the run has reached plus the one in hand,
up to the stack's size; `ImportProgressState` carries them. The running card draws `PhotoStack` in the gallery icon's
place, in a slot as wide as a full stack so the words hold still; `PhotoStack` rings each photo in `Island.kt`'s card
colour, shared with `IslandCard`.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2, *The import notice*; map row E17.

## Global constraints

- The stack's size is one constant, `Tuning.IMPORT_PREVIEWS`, for the worker's report, the running card and the
  finished card's thumbnails.
- The finished card keeps the added cats' own thumbnails; only the running card shows picked photos.
- The photos are decorative: no content description, nothing to press.
- No new strings.

## Tasks

### Task 1: The photos reach the state

- `Tuning.IMPORT_PREVIEWS`; `CounterStore`'s `IMPORT_THUMBNAILS` goes.
- `ImportPhotosWorker` puts `KEY_PREVIEWS` (the batch's first `IMPORT_PREVIEWS`) in each progress report;
  `ImportWorkInfo` reads it into `Import.Progressed(done, total, previews)`.
- `ImportProgressState.previewUris`; `CounterStateMapper.importProgress(done, total, previews)` keeps
  `previews.take(done + 1)` up to the stack's size; `CounterStore` builds progress through it on `PhotosPicked` and
  `Progressed`.
- Tests: `ImportPhotosWorkerTest` (the report carries the first photos), `ImportWorkInfoTest` (read back; none
  without the key), `CounterStateMapperTest` (0 → one, 1 → two, 2 and on → three, never more than given),
  `CounterStoreTest` (the first photo at once; the worker's photos on progress).

### Task 2: The card draws them

- `Island.kt`: the card colour as one composable value used by `IslandCard` and the ring.
- `ImportStatus.kt`: `Running` shows `PhotoStack(previewUris, slots = full)` or the gallery icon without photos;
  `PhotoStack` rings each photo, drawn outside its edge.
- `ImportIslandTest`: the running card shows the photos in its state and the gallery icon without; its words do not
  move as the stack fills; a later photo's ring covers the one beneath in the card's colour, running and finished.

### Task 3: The record

`import.md`'s card paragraph; spec § 2 *The import notice* and the decision log; map row E17 `in-review`.

### Task 4: Renders, review, gate

Renders (light, dark; running with one and three photos, finished); draft PR; `/code-review`; the gate with
mutations (the stack not filling by progress; no previews in the report; the ring gone; the stack sized to its
photos); the audit; the owner's look check.
