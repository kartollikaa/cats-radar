# Slice E16 — the location hint floats

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** the Counter's location hint leaves the column and floats like the import's card, with Grant, a × and a swipe.

**Architecture:** `IslandCard` moves out of `ImportStatus.kt` into `Island.kt`, shared. `LocationPermissionHint.kt`
becomes `LocationIsland(visible, onAction)`: the drop-in, a `SwipeToDismissBox` whose dismissal is `DISMISS`, the pin,
the words, a tonal Grant and a ×. `CounterScreen` stacks the import's island and the location island in one column
over the count, read first by TalkBack; the column's `above` slot empties.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2, *The location hint*; map row E16.

## Global constraints

- `locationPermissionHintVisible`, `GRANT` and `DISMISS` keep their meaning; no presentation change.
- `NoticeCard` stays (E8 uses it).
- EN and RU strings; the existing hint, grant and dismiss strings carry the words.

## Tasks

### Task 1: The island

- `Island.kt` (`IslandCard`), `LocationIsland`, `CounterScreen`'s stacked column.
- `LocationIslandTest`: the count keeps its size with the hint; Grant reports GRANT; × reports DISMISS; a swipe to
  either side reports DISMISS; the body takes no tap and the words are one item; with an import both show, the import
  above, not overlapping; the stack is read before the count. `CounterNoticesTest`'s one test moves here.

### Task 2: The record

`counting-cats.md` § the notices paragraph; map row E16 `in-review`.

### Task 3: Renders, review, gate

Renders (light, dark, font 1.5; the hint alone, the hint under an import); draft PR; `/code-review`; the gate with
mutations (the hint back in the column; × wired to Grant; no swipe; the import under the hint); the audit; the owner's
look check.
