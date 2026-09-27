# Slice E4 — Coat faces in Material shapes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every coat grid — the Counter's, the coat question after a photo, the map's filter — draws each face in a 52 dp Material shape by column, and rings a chosen coat on its shape; the grid grows no taller.

**Architecture:** `CoatGrid` in `ui/…/coat/CoatSwatch.kt` gets its own tile, `CoatTile`: a 52 dp box clipped to `coatShapeFor(column)` (`Circle`, `Square`, `Clover4Leaf`, `Arch`), on `surfaceContainerHighest`, or `primaryContainer` with a 2 dp `primary` border when chosen, and the name under it. The detail's `CoatPicker` keeps today's cell until E7 retires it. Photo is already the filled split button the spec describes (`SplitButtonLayout` with `SplitButtonDefaults`' filled halves), so it does not change.

**Tech Stack:** Compose Material 3 Expressive (`MaterialShapes`, `RoundedPolygon.toShape()`), Robolectric `@GraphicsMode(NATIVE)` pixel samples, a JVM test in `:ui`.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2 *The coat grid*, *Photo*. Map: row E4 and *Slice E4*. Criteria: `expressive-e4.md`.

## Global Constraints

- "Each face sits in a 52 dp Material shape on `surfaceContainerHighest`. The shapes go by column: `Circle`, `Square`, `Clover4Leaf`, `Arch`. The ringed coat's shape fills with `primaryContainer` and takes a 2 dp `primary` outline; its name stays under it."
- "When the ring moves, clears and follows Undo does not change." — no presentation change.
- "The rule that a row's cells share the tallest one's height so their rings match goes."
- "The filter's *Not specified* cell takes the next shape in turn. The grid must not grow taller than today's."
- The detail's picker is out of scope (E7).
- Gradle only through `ctx_execute`; `--rerun --no-build-cache`; counts from JUnit XML; per-commit compiles.

---

### Task 0: Freeze the criteria (`expressive-e4.md`)

### Task 1: The shapes by column (`:ui` JVM test)

- [ ] **Step 1: Failing test** `ui/src/test/kotlin/dev/catsradar/ui/coat/CoatShapesTest.kt`: `the shapes go by column` — `coatShapeFor(0..3)` are `MaterialShapes.Circle`, `Square`, `Clover4Leaf`, `Arch`; `coatShapeFor(4)` is `Circle` again and `coatShapeFor(11)` (the filter's twelfth cell) is `Arch`.
- [ ] **Step 2:** red — `coatShapeFor` does not exist.
- [ ] **Step 3: Implement** `internal fun coatShapeFor(column: Int): RoundedPolygon` in `CoatSwatch.kt`.

### Task 2: The tile (`:app` Robolectric)

- [ ] **Step 1: Failing tests** in `app/src/test/kotlin/dev/catsradar/app/coat/CoatGridLookTest.kt` (`@GraphicsMode(NATIVE)`, `w411dp-h891dp`), on the Counter:
  - `every coat sits in a 52 dp shape` — eleven nodes tagged `CoatShapeTestTag`, each 52 × 52 dp.
  - `the ringed coat's shape fills with the primary container and is outlined in primary, the rest sit on the highest container` — with Ginger ringed, a pixel 4 dp in from the Ginger (circle) shape's start edge at mid-height is `primaryContainer` and one 1 dp in is `primary`; the same pixel of White is `surfaceContainerHighest`.
  - `the grid is no taller than it was` — the grid's height is at most its height at the branch base (measured there first and written into the test as the bound).
- [ ] **Step 2:** red on the first two; the third passes at the base by construction and is proven by a mutation.
- [ ] **Step 3: Implement** `CoatTile` and use it in `CoatGrid` with `forEachIndexed`, the column `index % CoatsPerRow`, the *Not specified* cell at `entries.size`; drop `fillMaxRowHeight`. Face 38 dp; name `labelSmall` 2 dp under the shape; no vertical padding, so a row stays as tall as today's.
- [ ] **Step 4:** green; `CoatSheetsTest`, `EncounterDetailEntryTest`, `EncounterDetailCoatPickerTest`, `CounterControlsTest`, `CounterMilestoneTest` pass.
- [ ] **Step 5: Mutations** (after commit): the shape at 48 dp; the chosen fill `surfaceContainerHighest`; 8 dp vertical padding back on the tile; `coatShapeFor` returning `Circle` for every column.

### Task 3: The record

`coat.md` (the shapes, the ring on the shape, the row-height rule gone; the detail's picker still rings the whole cell until E7), `map.md` (*Not specified* takes the next shape), map row E4 `in-review`.

### Task 4: Renders, review and the gate

Counter harness before and after (light, dark, font 1.5), the coat question and the map filter's sheet via their public composables; `/code-review`; the gate in a scratch worktree; the PR stacked on #210; the acceptance gate.
