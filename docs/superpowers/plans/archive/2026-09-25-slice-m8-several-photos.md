# Slice M8 — Several photos from the gallery at once — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan
> task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** *Choose from gallery* on the detail screen picks several images and attaches them one after
another, with a counted progress bar and one message for the lot.

**Architecture:** A new `PickSeveralPhotos` contract wraps `PickMultipleVisualMedia` and cuts its result to
`Tuning.ATTACH_BATCH_MAX`. `EncounterDetailIntent.PhotosPicked(uris)` replaces the single pick; the Store
runs `AttachPhoto` for each URI in turn, tracks `AttachProgress(done, total)` and the ids still to arrive,
and folds the outcomes into at most one effect. The mapper shows the count only for more than one photo.

**Tech Stack:** Kotlin Multiplatform, MVI Store, Compose Material 3, AndroidX Activity Result, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-25-many-photos-per-cat-design.md` § The detail screen.

## Global Constraints

- The cap is `Tuning.ATTACH_BATCH_MAX`; a picker that ignores it is cut to the first ones chosen.
- EN and RU strings for every new text; counts through `<plurals>`.
- No new comments beyond durable facts; `./gradlew check` green.

---

### Task 1: The cap and the picker contract

**Files:** `domain/.../Tuning.kt`, `TuningTest.kt`; create `app/.../photo/PickSeveralPhotos.kt` and
`app/src/test/.../photo/PickSeveralPhotosTest.kt`.

- [ ] Test: the intent is `ACTION_PICK_IMAGES` for `image/*` with `EXTRA_PICK_IMAGES_MAX` = the cap; a result
  larger than the cap keeps the first ones in order; a dismissed picker yields an empty list.
- [ ] Implement `PickSeveralPhotos(maxItems)` over `PickMultipleVisualMedia(maxItems)` with `.take(maxItems)`.

### Task 2: The Store attaches a pick one after another

**Files:** `EncounterDetailIntent.kt`, `EncounterDetailEffect.kt`, `EncounterDetailState.kt`,
`EncounterDetailStateMapper.kt`, `EncounterDetailStore.kt`; tests `EncounterDetailPickSeveralTest.kt`,
`EncounterDetailStateMapperTest.kt`, `EncounterDetailStoreTest.kt`; fakes in `CounterStoreTestDoubles.kt`
(`FakeImageResizer.unreadable`, `FakeDigest(digestOf)`).

- [ ] Tests: order kept after the cat's own photos; progress advancing `0/3 → 1/3 → 2/3 → ready`; the
  progress held until every attached photo is observed; one failure → `PhotoNotAttached`; several →
  `PhotosNotAttached(count)`; all already there → `PhotosAlreadyThere`; a duplicate among added ones
  silent; leaving mid-pick keeps what landed; a cat removed mid-pick gets nothing more and no message.
- [ ] `PhotosPicked(uris: List<String>)`; `PhotoTaken(uri)` goes through the same path with one URI.
- [ ] `AttachProgress(done, total)` with `fraction`; `Loaded.attachProgress` only when `total > 1`.

### Task 3: The counted bar and the messages on screen

**Files:** `AddPhotoCard.kt`, `EncounterDetailScreen.kt`, strings EN/RU, `PhotoLaunchers.kt`
(`rememberSeveralPhotosPicker`, `PhotoCountReporter`), `EncounterDetailDestination.kt`; tests
`EncounterDetailScreenTest.kt`, `EncounterDetailEffectHandlerTest.kt`.

- [ ] Tests: the bar reads "Attached 2 of 5 photos" at 0.4; a single photo keeps the uncounted bar; each new
  effect reaches its own reporter.
- [ ] Strings `detail_photos_attaching`, `detail_photos_already_there`, plurals `detail_photos_not_attached`.

### Task 4: Docs

- [ ] `docs/features/encounter-detail.md` § Its photos: *Several from the gallery*; `photos.md`: several
  images, leaving mid-pick, some photos failing; the map (M7 merged, M8 in review); archive the M7 plan.
