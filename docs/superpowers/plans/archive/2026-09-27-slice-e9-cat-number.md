# Slice E9 — The cat's number Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The facts row on a cat's page opens with the cat's number: its place among the live cats, oldest
first. It reads "#62" in English and "№ 62" in Russian, and TalkBack says "Cat number 62".

**Architecture:** One count, carried from the DAO up to the label:
- **`:data`:** `EncounterDao.observeNumber(id)` counts the live cats logged no later than this cat, with ties broken by id. It returns no row, so null, while the cat is not live. `EncounterRepositoryImpl` passes it through.
- **`:domain`:** `EncounterRepository.observeNumber(id)` and the use case `ObserveEncounterNumber(id)`, which drops repeats: a write that does not move the cat emits nothing new.
- **`:presentation`:** `EncounterDetailStore` observes the number of every cat in the outing window beside its place, through one helper that serves both. `EncounterDetailStateMapper` puts it on the page as `CatPage.numberInLog`.
- **`:ui`:** `DetailHeading` opens the facts row with a filled label. It shares its frame with `OutlinedLabel`: 32 dp, `extraSmall` corners and 12 dp padding, in `ui/components/Labels.kt`.
- **`:app`:** the Koin binding.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md`:
- § 4 *The cat's number*;
- § 3 *The facts row*;
- *Testing* (Domain);
- map row E9.

Prototype version 5 draws the number as the first chip, on `primaryContainer` with no border. Criteria: `expressive-e9.md`. Stacked on E6 (#232).

## Decisions inside the spec

- **The label.** It is filled, not outlined, as the prototype draws it:
  - `primaryContainer` behind `onPrimaryContainer` text, in `labelLarge`;
  - the outlined labels' height, corners and padding, which a shared frame keeps equal.
- **The words.** Each is a resource with the count as its argument: `detail_number` is "#%1$d", or "№ %1$d" with a no-break space in Russian; `detail_number_spoken` is "Cat number %1$d", or «Котик номер %1$d». The visible text carries the spoken form as its content description. Compose then reads it in place of "#62" as it walks the merged row, so the row still reads as one item.
- **No number, no label.** A cat that is not live, or not counted yet, has none. The row then starts with the day, as E6 left it.
- **One query per cat.** Each cat in the window gets its own query, as the spec names `ObserveEncounterNumber(id)`. The count rides the `(deletedAt, occurredAt)` index. The page waits for the numbers as it waits for the places, so it never shows first without one.
- **Renumbering reaches the page on its own.** Room re-runs every count on any write to `encounters`: a delete elsewhere, an undo, an older import. So the page renumbers while the outing window stays the same.
- **The fake repository mirrors the DAO's count**, so the Store tests renumber the way the app does.

## Global Constraints

- Nothing else on the detail changes. Numbers appear nowhere but the detail.
- Gradle only through `ctx_execute`: `--rerun --no-build-cache`, results read from the JUnit XML, every log headed by its sha, and every commit compiling.

### Task 0: Freeze the criteria (`expressive-e9.md`)
### Task 1: The count — the DAO query and its Room test; the repository, the fakes, `ObserveEncounterNumber` and its test
### Task 2: The page — the Store and the mapper, with tests first; the Koin binding
### Task 3: The label — `Labels.kt`, the number opening the facts row, the strings; the Robolectric tests first
### Task 4: The record — `encounter-detail.md`, spec § 4's interim sentence, map row E9
### Task 5: Renders, device, review, gate, acceptance gate
