# Slice E6 — The detail names its cat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A cat's page opens under its photos with its title from the coat and a row of fact labels, More in the bar holds *Show on the map* and *Remove this cat*, and a tonal *Remove this cat* ends the page in place of Delete.

**Architecture:** This is `:ui`, strings and docs only; the state already carries everything needed:
- `CatPage.coat` names the cat;
- `dayLabel`, `timeLabel` and `place` fill the facts;
- `mapPosition` says whether the cat is on the map;
- the existing Delete and coordinates intents do the menu's work.

The pieces:
- `CoatOption?.titleRes()` sits beside `labelRes()`, with one resource per coat and one for no coat.
- A new `OutlinedLabel` in `ui/components` serves E5's position label and the facts row.
- `BackBar` takes an optional end slot, where the detail puts More and its menu.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 3 *The bar*, *The title*, *The facts row*, *Remove*; *Behaviour changes* 2; map row E6. Prototype version 5 (the Expressive detail). Criteria: `expressive-e6.md`. Stacked on E5 (#230).

## Decisions inside the spec

- **The titles follow the prototype:** the coat's name up to any comma, plus "cat" ("Ginger & white cat"; both calicoes are "Calico cat"), or "A cat" with no coat noted. The Russian names the cat with «котик»: «Рыжий котик», «Рыже-белый котик», «Трёхцветный котик», and «Котик» with no coat noted. Each coat keeps its own resource, so a language may tell the calicoes apart.
- **The facts row** is the day with a calendar icon, the time with a clock icon, and the place's flag and name, as the prototype draws it. The row is outlined labels, 32 dp tall with `extraSmall` corners, in a `FlowRow`. It is merged for TalkBack, and the flag stays silent as it does elsewhere. There is no place label while the cat has no named place. The number arrives in E9 and the no-location alert in E8.
- **More** is a tonal icon button at the bar's end, in the back arrow's circle. Its menu:
  - *Show on the map*, disabled while the map does not draw the cat (no `mapPosition`), sends the coordinates tap for the cat on screen;
  - *Remove this cat*, in the error colour, sends Delete.
  - A removed or missing cat has no More.
- ***Remove this cat*** is a `FilledTonalButton` on `errorContainer` / `onErrorContainer`, centred at the end of the page.
- **Order:** the day and time headline gives way to the title and the facts, under the photos and *On this photo*. The Where card and the coat keep their order until E7 and E8 restyle them.
- **New icons:** `ic_more_horiz`, `ic_calendar`, `ic_schedule` (Material Symbols). "Delete" goes; "Remove this cat" and "More" are new.

## Global Constraints

- Delete's behaviour, the removed state and Undo are unchanged. The bar's back arrow and the outing position stay as they are.
- Gradle only through `ctx_execute`; `--rerun --no-build-cache`; JUnit XML; sha first; per-commit compiles.

### Task 0: Freeze the criteria (`expressive-e6.md`)
### Task 1: Titles, `OutlinedLabel`, the facts row (`:app` Robolectric tests first)
### Task 2: More in the bar and its menu; *Remove this cat* at the end; existing tests follow the new label
### Task 3: The record — `encounter-detail.md`, map row E6
### Task 4: Renders, device, review, gate, acceptance gate
