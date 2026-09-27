# Slice E12a — Encounters in outing cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Encounters tab opens on a headline with its totals, and each outing is one card headed by its day, its start · count · span, an *On a walk* chip when a walk overlapped it, and *On the map* as a tonal pill.

**Architecture:** `EncountersStateMapper` gives `OutingHeader` the day, the start, the count, the span and whether a stored walk overlapped the outing, marks the row that closes each outing, and totals the tab; `EncountersStore` combines the stored walks (`WalkRepository.observeAll()`) with the encounters. `EncounterRows` gains an outing-card mode that only the Encounters tab turns on: each row draws its piece of the outing's card on `surfaceContainerLow` with `extraLarge` corners at the header's top and the closing row's bottom, and no gap between the pieces. The Places list and the map's spot sheet keep today's rows.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 6. Map: rows E12a/E12b (E12 split, see the decision log). Criteria: `expressive-e12a.md`.

## Decisions inside the spec

- **E12 is split.** E12a: the headline, the outing card, its header and its pills. E12b: the cats inside the card (coat-shaped tiles, pair tiles' chip, the list's cards, the selection's ring and bar). Keeping the Places list and the spot sheet unchanged makes the rows' code two paths, which E12 as one slice would carry well past the size cap.
- **The day** is `dayHeader` as today (Today, Yesterday, a medium date); the start stays beside the count so two outings on one day still read apart.
- **The span** is first cat to last cat through `DateTimeFormatter.duration`; an outing whose cats share one moment (a lone cat) has no span, so the line never says "0 min".
- **A walk overlaps** an outing when the walk's window — its start to its end, with no end while it is on — meets the outing's first-to-last span, ends included.
- **The totals** count every cat on screen (a shot's cats each) and every outing; the headline is the list's first item and scrolls with it.
- **Card pieces.** The header piece has the top corners, the closing row the bottom corners, rows between none; the gap between rows lives inside the card, the gap between outings outside it.
- **Cards inside the card** (a short run's cards, the list's rows) move to `surface`, or they would vanish on the card's `surfaceContainerLow`; what they show is E12b's.

## Global Constraints

- Counter files are not touched. Places and the spot sheet render exactly as before.
- Gradle only through `ctx_execute`; `--rerun --no-build-cache`; results from JUnit XML; sha first in every log; per-commit compiles.

### Task 0: Freeze the criteria (`expressive-e12a.md`)
### Task 1: The header's labels, the walk and the totals (`:presentation` mapper + Store tests first)
### Task 2: The card and the header (`:ui` + `:app` Robolectric tests first)
### Task 3: The record — `browsing-cats.md`, map rows E12a/E12b and the decision log
### Task 4: Renders, device, review, gate, acceptance gate
