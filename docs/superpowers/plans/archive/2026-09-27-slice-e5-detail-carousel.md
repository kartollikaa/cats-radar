# Slice E5 — The detail's photos as a carousel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A cat's photos become a carousel ending in two add items, with a position label and a wavy progress indicator under it, and a cat without a photo opens on its face in a `primaryContainer` block with a connected pair of buttons.

**Architecture:** `:ui` only. A new `DetailPhotoCarousel` replaces `DetailPhotoPager` and `AddPhotoCard`: Material's multi-aspect carousel (`MultiAspectCarouselScope` with `maskClip` over a snapping `LazyRow`), so the photos and the narrower add items keep their own widths. `NoPhotoBlock` takes the place of both on a cat without a photo. The page's content keeps its 16 dp inset; the carousel spans the width and insets its items instead, so they scroll under the screen's edges. State is unchanged: `CatPage.photos`, `coat`, `addPhoto` and `attachProgress` already say everything the two blocks draw.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 3 *Photos*, *No photo*; map row E5. Prototype version 5 (the Expressive detail). Criteria: `expressive-e5.md`.

## Decisions inside the spec

- **The multi-aspect carousel, not `HorizontalMultiBrowseCarousel`.** The multi-browse carousel sizes every item by the same keylines, so it cannot draw the two narrower add items the spec and the prototype ask for. The multi-aspect carousel gives each item its own width and the same mask and parallax as it leaves the edge. The spec's wording is amended to match.
- **16 dp, not 20 dp, of side inset.** `encounter-detail.md` keeps the back arrow in line with the content under it (`EncounterDetailScreenTest`, *the back button lines up with the content under it*). The first photo starts in line with the arrow, and the spec is amended to match.
- **The geometry follows the prototype:**
  - photos 300 dp wide at 4:5 with `large` corners, 8 dp apart;
  - add items 140 dp wide, as tall as a photo, on `surfaceContainerLow`, a 28 dp icon over a label;
  - items snap to the start.
- **The position label** is an outlined label, 32 dp tall, with `extraSmall` corners, under the carousel. The photo in front is the one whose start sits nearest the carousel's start, and scrolled onto the add items it is the last photo.
- **The no-photo block** follows the prototype:
  - the face at 170 dp, or the paw at 120 dp in `onPrimaryContainer`;
  - "No photo yet" in `titleMedium`;
  - the connected pair on `surfaceContainerHighest`, labelled **Take a photo** and **Gallery**.
- **Strings:** "From gallery" and "Gallery" replace "Choose from gallery", and "No photo yet" is new. "Add a photo" goes with its card.

## Global Constraints

- The outing pager's behaviour is untouched. A drag on the carousel moves it to its own end, then the page.
- The viewer, the camera, the picker and the attach flow are unchanged; the same intents are sent for the page's cat.
- Gradle only through `ctx_execute`; `--rerun --no-build-cache`; JUnit XML; sha first; per-commit compiles.

### Task 0: Freeze the criteria (`expressive-e5.md`)
### Task 1: `DetailPhotoCarousel` and `NoPhotoBlock` (`:app` Robolectric tests first); retire `DetailPhotoPager` and `AddPhotoCard`
### Task 2: Existing tests follow the new labels and the carousel's end
### Task 3: The record — `encounter-detail.md`, `photos.md`, spec § 3 amendments, map row E5
### Task 4: Renders, device (flings, the nested drag, the mask), review, gate, acceptance gate
