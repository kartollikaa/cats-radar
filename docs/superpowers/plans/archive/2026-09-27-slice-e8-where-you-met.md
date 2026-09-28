# Slice E8 — Where you met Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Where card is restyled around its map:
- headed **Where you met**, on `surfaceContainerLow` with `large` corners;
- the spot map at 16:10;
- the place with its flag in `titleMediumEmphasized`;
- "Current location · ±12 m";
- quiet coordinates;
- a filled **Show on the map** pill as the card's visible cue.

The spot map also draws the fix's accuracy to scale around the dot. A cat with no location has no Where card: a notice under its facts row says so, with **Set on map**.

**Architecture:** This is `:ui`, strings and docs only; the state already carries everything needed:
- `location`, `coordinatesLabel`, `accuracyMeters`, `place` and `mapPosition` fill the card;
- `setsLocation` decides between the card and the notice;
- the existing coordinates and set-location intents act.

The pieces:
- **`NoticeCard` and `NoticeIcon`** move from `ui/counter` to `ui/components`. The icon takes its tone (container and content colours) as parameters, defaulting to today's secondary pair, and the card gains an optional action at its end. The Counter's two callers only change their import.
- **The accuracy circle** is a Compose overlay under the cat's dot. It is sized with mbgl's meters-per-dp at the spot map's fixed street zoom and the cat's latitude, transcribed as maplibre-compose's `Viewport.metersPerDpAtTarget` computes it. It is drawn only when wider than the dot. The JVM stand-in draws it too, so tests see it.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md`:
- § 3 *The no-location alert*, *The Where card*, *The accuracy circle*;
- *Behaviour changes* 5;
- map row E8.

Prototype versions 5 and 15. Criteria: `expressive-e8.md`. Stacked on E7b (#238).

## Decisions inside the spec

- **The card**, following prototype version 5:
  - one tap target when the map draws the cat, as today;
  - the header "Where you met" in `titleMediumEmphasized`, a heading;
  - the map at 16:10 with `medium` corners;
  - the place line: flag, then "City, Country", or the title alone when no city is known;
  - the source and accuracy joined by " · ", or the source alone with no accuracy;
  - the coordinates in `bodySmall`/`onSurfaceVariant`;
  - the pill is a filled `primary` shape with the map icon and "Show on the map", present only while the map draws the cat, and not a separate control.
- **The notice**, following prototype version 15:
  - the pin in a round `tertiaryContainer` icon;
  - **No location for this cat** over "It was logged without a fix, so it is not on the map or in Places.", read as one item;
  - a tonal **Set on map** at its end, its own control, sending the same set-location tap as before.
  - It shows exactly while `setsLocation` holds, so it goes as soon as the cat has a location, whichever way it came.
- **The circle:**
  - a `primary` disc at alpha 0.16 with a 1.5 dp `primary` outline;
  - radius = accuracy ÷ meters-per-dp at zoom 15 and the cat's latitude;
  - drawn only when that radius exceeds the dot's.
- **Strings:**
  - new: "Where you met", the source-and-accuracy pattern, the city-and-country pattern, and the notice's two lines;
  - "Where" and the lone accuracy string go;
  - "Set on map" and "Show on the map" are reused.

## Global Constraints

- Every tap reaches the intents it reaches today: the card's coordinates tap and Set on map. The Map tab is untouched.
- The Counter's two notices look exactly as before. Only the icon's import moves.
- Gradle only through `ctx_execute`: `--rerun --no-build-cache`, results read from the JUnit XML, every log headed by its sha, and every commit compiling.

### Task 0: Freeze the criteria (`expressive-e8.md`)
### Task 1: `NoticeCard` to `ui/components` with its tone and action; the Counter's imports follow
### Task 2: The Where card restyled, and the notice under the facts; Robolectric tests first; existing tests follow the new words
### Task 3: The accuracy circle, with a meters-per-dp unit test and stand-in pixel tests
### Task 4: The record — `encounter-detail.md` § Its map and § Where it was found, the opening; map row E8
### Task 5: Renders, device (the circle on a real map), review, gate, acceptance gate
