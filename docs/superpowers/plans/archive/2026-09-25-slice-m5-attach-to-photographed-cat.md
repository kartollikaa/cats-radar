# Slice M5 — Attaching a photo to a cat that has one Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AttachPhoto` adds a photo to any live cat, skips a photo the cat already has, and keeps both links on every cat.

**Architecture:** The DAO's `addPhoto` guard becomes "the cat is live". `AttachPhoto` takes the digest before copying and returns `AttachResult.AlreadyThere` on a match among the cat's photos; the photo gets its own id (the resizer's base name) and names this install, so its links stay valid wherever the row travels. The detail Store treats `AlreadyThere` as it treats a cat that cannot take the photo until M7 gives it a message.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § Attaching photos. Map: M5. Criteria: `many-photos-m5.md` in the acceptance directory.

## Global Constraints

As M1–M4 (`docs/superpowers/plans/archive/`).

---

### Task 1: The use case and the guard
`AttachPhoto` (no photo check, digest first, `AlreadyThere`, fresh photo id, this install, both links); `EncounterDao.countLive`; repository KDoc; fakes. Tests: `AttachPhotoTest` (second photo after the first, duplicate costs no disk, fresh id, foreign cat keeps links that open here only), `EncounterDaoAttachPhotoTest`.

### Task 2: Docs, check, review
`photos.md`, `photo-viewer.md`, `encounter-detail.md`, `data-model.md`; the map; M4's plan archived; `./gradlew check`; `/code-review`; acceptance gate.
