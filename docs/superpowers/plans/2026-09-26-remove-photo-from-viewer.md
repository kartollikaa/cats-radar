# Remove Photo From Viewer — Implementation Plan

> **Execution:** Native in this isolated worktree. Follow `superpowers:executing-plans` and `superpowers:test-driven-development` task by task.

**Goal:** Let a person remove only the photo currently displayed in the fullscreen viewer after an explicit confirmation, without changing the cat, its other photos, gallery originals, or other cats in the same shot.

**Approved design:** Owner approved the bounded in-chat design on 2026-09-26: detach only the displayed photo and require confirmation. With photos remaining, keep the viewer on the nearest page; after the last photo, close to detail. Failure keeps the photo and viewer and reports an error.

**Acceptance:** `/Users/dmitrijmaksimov/.claude/projects/-Users-dmitrijmaksimov--codex-worktrees-3d9b-Cats Radar/acceptance/remove-photo-from-viewer.md`

**Architecture:** Add an exact-photo removal operation to the `EncounterRepository`, implemented as one guarded Room transaction that removes the row only from its live encounter and stamps the encounter's `updatedAt`. A domain `RemovePhoto` use case validates the current attachment, removes only that row's app-owned files while the row still points at them, then removes the row; each shot member owns separate files, so no reference counting is needed. `PhotoViewerStore` owns confirmation/in-flight state and reacts to the repository flow; Compose renders the action and confirmation dialog, while the navigation entry handles the failure effect as a localized toast.

**Tech stack:** Kotlin Multiplatform, coroutines/Flow, Room, Koin, Compose Material 3, Navigation 3, Robolectric, kotlin-test, Turbine.

**Governing docs:** `docs/features/photo-viewer.md`, `docs/features/photos.md`, `docs/features/encounter-detail.md`, `docs/features/data-model.md`, `docs/superpowers/specs/2026-09-24-photo-viewer-and-gallery-link-design.md`, and every file in `docs/rules/` named by `AGENTS.md`.

**Global constraints:** Tests precede implementation. Preserve the six existing workflow commits under this branch. Do not delete gallery originals. Do not touch another encounter's row or files, even when `shotId` matches. User-facing strings exist in EN and RU. Add no explanatory comments unless they record a non-derivable domain fact.

---

## Task 1: Exact attachment removal in domain and data

**Interfaces:** Produces `EncounterRepository.removePhoto(encounterId, photoId, updatedAt): Boolean` and `RemovePhoto(encounterId, photoId)`. Consumed by Task 2.

**Files:**

- Create `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/RemovePhoto.kt`
- Create `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/RemovePhotoTest.kt`
- Modify `domain/src/commonMain/kotlin/dev/catsradar/domain/repository/EncounterRepository.kt`
- Modify `domain/src/commonTest/kotlin/dev/catsradar/domain/testing/FakeEncounterRepository.kt`
- Modify `data/src/commonMain/kotlin/dev/catsradar/data/db/EncounterDao.kt`
- Modify `data/src/commonMain/kotlin/dev/catsradar/data/repository/EncounterRepositoryImpl.kt`
- Modify `data/src/commonTest/kotlin/dev/catsradar/data/repository/FakeEncounterDao.kt`
- Create `data/src/androidHostTest/kotlin/dev/catsradar/data/db/EncounterDaoRemovePhotoTest.kt`
- Modify `data/src/commonTest/kotlin/dev/catsradar/data/repository/EncounterRepositoryImplTest.kt`

1. Add failing domain tests proving the exact row is requested, full and thumbnail files are deleted before the guarded row removal, missing/deleted/wrong-encounter photos change nothing, and same-shot neighbours are untouched.
2. Run `./gradlew :domain:testAndroidHostTest --tests '*RemovePhotoTest' --console=plain`.
   Expected: RED because the use case and repository operation do not exist.
3. Add failing data tests for a transaction that deletes `photoId` only when it belongs to the live `encounterId`, stamps `updatedAt`, and returns false otherwise.
4. Run `./gradlew :data:testAndroidHostTest --tests '*EncounterDaoRemovePhotoTest' --tests '*EncounterRepositoryImplTest' --console=plain`.
   Expected: RED on the missing DAO/repository operation.
5. Implement the smallest repository contract, fake, Room transaction, repository adapter, and use case. Keep the gallery URI/source URI untouched because only app-owned paths are passed to `PhotoStorage.delete`.
6. Re-run both focused commands.
   Expected: GREEN; exact-row and same-shot isolation assertions pass.
7. Run `./gradlew :domain:testAndroidHostTest :data:testAndroidHostTest --console=plain`.
   Expected: GREEN.
8. Commit the independently understandable checkpoint.

## Task 2: Viewer confirmation and reactive page behavior

**Interfaces:** Consumes Task 1's `RemovePhoto`. Produces viewer state/intents/effects used by Task 3.

**Files:**

- Modify `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerState.kt`
- Modify `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerIntent.kt`
- Modify `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerEffect.kt`
- Modify `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerStore.kt`
- Modify `presentation/src/commonTest/kotlin/dev/catsradar/presentation/viewer/PhotoViewerStoreTest.kt`
- Modify `app/src/main/kotlin/dev/catsradar/app/di/PresentationModule.kt`

1. Add failing Store tests: requesting removal remembers the displayed photo id and opens confirmation; cancel clears it; confirm calls `RemovePhoto` once despite repeated taps; failure clears in-flight state and emits one `RemovePhotoFailed`; a remaining photo keeps the viewer open; removing the last photo emits `Close` once.
2. Run `./gradlew :presentation:testAndroidHostTest --tests '*PhotoViewerStoreTest' --console=plain`.
   Expected: RED on the missing removal intents/state/effect.
3. Add the minimum state (`removingPhotoId`, `removalInFlight`), fact-shaped intents, failure effect, Store orchestration, and Koin parameter.
4. Preserve page identity by photo id: after the observed list changes, the screen can choose the same index clamped to the remaining list; the Store closes only when the observed encounter has no photos.
5. Re-run the focused Store test, then `./gradlew :presentation:testAndroidHostTest --console=plain`.
   Expected: GREEN.
6. Commit the independently understandable checkpoint.

## Task 3: Accessible destructive UI and destination feedback

**Interfaces:** Consumes Task 2 viewer state/intents/effects. Produces the user-visible action, confirmation dialog, and failure toast.

**Files:**

- Modify `ui/src/main/kotlin/dev/catsradar/ui/viewer/PhotoViewerScreen.kt`
- Add or reuse a Material 3 delete vector under `ui/src/main/res/drawable/`
- Modify `ui/src/main/res/values/strings.xml`
- Modify `ui/src/main/res/values-ru/strings.xml`
- Modify `app/src/main/kotlin/dev/catsradar/app/navigation/PhotoViewerDestination.kt`
- Modify `app/src/test/kotlin/dev/catsradar/app/viewer/PhotoViewerScreenTest.kt`
- Modify `app/src/test/kotlin/dev/catsradar/app/navigation/PhotoViewerEntryTest.kt`

1. Add failing Compose tests proving the visible chrome exposes `viewer_remove_photo`, the action targets the current pager item, the Material 3 confirmation dialog has Cancel and Remove, Cancel changes nothing, Confirm reports the remembered id once, and remaining-page selection clamps to the nearest page.
2. Add a failing entry test proving `RemovePhotoFailed` produces the localized failure toast while the back stack remains unchanged.
3. Run `./gradlew :app:testDebugUnitTest --tests '*PhotoViewerScreenTest' --tests '*PhotoViewerEntryTest' --console=plain`.
   Expected: RED on missing action/dialog/effect handling.
4. Implement the trash action, dialog, callbacks, pager reconciliation by photo id/index, EN/RU resources, and destination toast. Disable confirmation while removal is in flight.
5. Re-run the focused tests, then `./gradlew :app:testDebugUnitTest --console=plain`.
   Expected: GREEN.
6. Commit the independently understandable checkpoint.

## Task 4: Durable behavior docs and complete evidence

**Interfaces:** Documents Tasks 1–3 and closes AC-1 through AC-8.

**Files:**

- Modify `docs/features/photo-viewer.md`
- Modify `docs/features/photos.md`
- Modify `docs/features/encounter-detail.md`
- Update this plan's task checkboxes or ledger only; do not create delivery-report files in the repository

1. Update the three feature docs with confirmation, exact attachment scope, remaining/last-photo behavior, same-shot isolation, gallery-original preservation, and failure behavior. Remove the old statement that photos cannot be removed.
2. Run `python3 tools/test_check_agent_docs.py -v` and `python3 tools/check-agent-docs.py`.
   Expected: both pass.
3. Positive control: temporarily invert one focused Store assertion or disable its removal dispatch, run its focused command, and record the expected failure in the execution ledger. Restore immediately and rerun the same focused command to green.
4. Run `CI=true ./gradlew check :app:assembleRelease --console=plain`.
   Expected: `BUILD SUCCESSFUL` with tests, detekt, lint, and release assembly green.
5. Invoke `acceptance:acceptance-gate`; obtain one verdict per frozen AC from a fresh independent auditor. Fix every Critical/Important finding and rerun affected checks.
6. Run a final whole-diff review and verify `git status --short`, `git diff --check`, branch name, HEAD, and divergence from `origin/main`.
7. Commit the final docs/evidence-ready checkpoint. Do not push or open a PR without separate authority.
