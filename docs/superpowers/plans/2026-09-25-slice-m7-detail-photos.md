# Slice M7 — A cat's photos on its detail screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The detail screen shows every photo of a cat in a pager that opens the viewer on the tapped one, and offers *Add photo* on every live cat.

**Architecture:** `EncounterDetailState.Loaded.photos: ImmutableList<DetailPhoto>` and a non-null `addPhoto`. `AttachResult.Attached(photoId)` lets the Store keep the attempt showing until an emission carries that photo (`arrivingPhotoId`). `PhotoClicked(photoId)` → `OpenPhoto(photoId)` → `PhotoViewer(id, photoId)`. `AlreadyThere` → `PhotoAlreadyThere`, reported by a toast. `DetailPhotoPager` draws the pager, the position and scrolls to a photo that arrives.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § The detail screen (the multi-select pick is M8). Map: M7. Criteria: `many-photos-m7.md` in the acceptance directory.

## Global Constraints

As M1–M6 (`docs/superpowers/plans/archive/`).

---

### Task 1: Presentation and domain
`AttachResult.Attached(photoId)`; detail state, mapper, intents, effects, Store. Tests: `EncounterDetailStateMapperTest`, `EncounterDetailStoreTest`, `AttachPhotoTest`.

### Task 2: Screen and wiring
`DetailPhotoPager`, `EncounterDetailScreen`, the add card's title; destination, effect handler, nav host; strings. Tests: `DetailPhotoPagerTest`, `EncounterDetailScreenTest`, `EncounterDetailEffectHandlerTest`, `PhotoViewerEntryTest`.

### Task 3: Docs, check, review
`encounter-detail.md`, `photos.md`; the map (M7 split, M8 added); M6's plan archived; `./gradlew check`; `/code-review`; acceptance gate.
