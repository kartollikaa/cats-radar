# Slice E12b — The cats inside the outing cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Inside each outing's card, a run's tiles take their coats' own shapes, a pair's times sit on a dark chip, each cat's card names its coat with its time and place, and the selection bar turns `primaryContainer`.

**Architecture:** `EncounterCell` gains the coat whose shape it takes (null for a shot or no coat), set by the mapper. `:ui` draws the Encounters tab's tiles through `coatShapeFor(cell.coat)`, a new `OutingCatCard` for the cards inside the card (the list's rows and a short run's), and the pair's chip and the bar in their new colours. The Places list and the spot sheet keep `EncounterCard` and today's tiles: the card mode of E12a is the only switch.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 6 *The grid*, *The list*, *Selecting*. Map: row E12b. Criteria: `expressive-e12b.md`. Stacked on E12a (#225).

## Decisions inside the spec

- A photo tile takes its cat's coat shape; a shot holds several coats and takes no coat's shape (`Ghostish`).
- The pair's chip is opaque `inverseSurface` with `inverseOnSurface` text: dark on the light theme, and still apart from any photo on the dark one.
- A cat's card inside the card: the lead in its coat's shape at 48 dp, the coat's name or "A cat" in `titleSmall` (a shot: "Photo of N cats"), "time · place" in `bodySmall`, with "No location yet" in `onTertiaryContainer`. The list's rows are separate cards 6 dp apart with `medium` corners.
- The selection's check stays; the ring follows the shape.

## Global Constraints

- Counter files untouched; Places and the spot sheet unchanged.
- Gradle only through `ctx_execute`; `--rerun --no-build-cache`; JUnit XML; sha first; per-commit compiles.

### Task 0: Freeze the criteria (`expressive-e12b.md`) — done before the branch
### Task 1: The cell's coat (`:presentation`, mapper test first)
### Task 2: Tiles, chip, cards, bar (`:ui` + `:app` Robolectric tests first)
### Task 3: The record — `browsing-cats.md`, map row E12b
### Task 4: Renders, device, review, gate, acceptance gate
