# Slice M6 — The viewer pages through a cat's photos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The viewer shows every photo of a cat in a horizontal pager, opens on the one it was opened for, and its gallery button acts on the photo on screen.

**Architecture:** `PhotoViewer(encounterId, photoId = null)`; the Store takes the photo id and passes it to the mapper, which builds `Showing(photos, firstPage, timeLabel, dayLabel)` with a `ViewerPhoto` per photo (path, link flag) and the index of the opened photo, the cover when there is none. `OpenInGalleryClicked(photoId)` resolves that photo's link. The screen draws a `HorizontalPager` of Telephoto images keyed by photo id, the gallery button for the current page, and a position at the bottom while there is more than one.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § The viewer. Map: M6. Criteria: `many-photos-m6.md` in the acceptance directory.

## Global Constraints

As M1–M5 (`docs/superpowers/plans/archive/`), plus:
- The page on screen is view state (the pager's); the Store never learns it.
- EN and RU strings for the position and its description.

---

### Task 1: Presentation
`PhotoViewerState`, `ViewerPhoto`, `PhotoViewerIntent.OpenInGalleryClicked(photoId)`, the mapper's `openedOn`, the Store's `openedOn` and per-photo resolve; Koin binding. Tests: `PhotoViewerStateMapperTest`, `PhotoViewerStoreTest`.

### Task 2: Screen and key
`PhotoViewer.photoId`; destination; `HorizontalPager`, per-page gallery button, `PagePosition`; strings. Tests: `PhotoViewerScreenTest` (swipe, button follows the page, position, opening page), entry and navigation tests.

### Task 3: Docs, check, review
`photo-viewer.md`; the map; M5's plan archived; `./gradlew check`; `/code-review`; acceptance gate.
