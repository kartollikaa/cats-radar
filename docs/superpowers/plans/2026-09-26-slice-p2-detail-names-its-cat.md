# Slice P2 — The detail screen's intents and effects name their cat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every per-cat intent and effect of the encounter detail screen carries the cat's id, and a camera or picker result reaches the cat it was opened for even after the process died — while the screen still shows one cat.

**Architecture:** `:presentation` intents and effects gain a `catId`; the Store acts on the id an intent names. In `:app`, the camera's saved queue records each shot's cat beside its target (null for the Counter, whose shot logs a new cat), and the single-photo picker saves the cat it was opened for; the effect handler opens both for the effect's cat, and the viewer and the map for the effect's cat too. The screen composables are untouched: the destination still supplies `key.id`, which P3 replaces with each page's id.

**Tech Stack:** Kotlin, Compose, Activity Result API, Robolectric + Compose UI test, turbine, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-25-outing-pager-design.md` § Intents and effects name their cat. Map: `docs/tbd/decompositions/2026-09-25-outing-pager.md` P2. Criteria: `outing-pager-p2.md` in the acceptance directory (Task 0).

## Global Constraints

- Behaviour a user can see does not change: one cat on screen, every id equal to `key.id`.
- `:presentation` imports no `androidx.compose.*` and no `dev.catsradar.data` (Konsist `ModuleBoundaryTest`).
- A composable callback carrying more than one value takes one payload type, never a two-parameter lambda (`docs/rules/compose-patterns.md` §5).
- Comments: default none; one line, English, a durable fact (`docs/rules/code-commenting-standards.md`).
- Tests first. `--rerun` on every focused Gradle test run; counts read from the JUnit XML.
- Gradle runs through the context-mode `ctx_execute` tool (language `shell`, a `timeout` of 900000), because a hook refuses Gradle in Bash; `cd` to the worktree first and print only the tail.
- Branch `tech/detail-names-its-cat` (from `origin/main` 6285b92d; carries the P1 housekeeping commit `f5dfcef8`). Nothing is pushed until Task 3.
- The feature doc changes in the same PR (`docs/features/encounter-detail.md`).

---

### Task 0: Freeze the acceptance criteria

Controller runs `acceptance:acceptance-criteria` (auto) into `outing-pager-p2.md` before any code: each behaviour test below by name, the launcher tests across recreation, the old-shape restore, the unchanged screen callbacks, the doc lines, `./gradlew check` green.

---

### Task 1: Intents, effects and the Store name their cat

**Files:**
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailIntent.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailEffect.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStore.kt`
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStoreTest.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt` (the `entry<EncounterDetail>` block)
- Modify: `app/src/test/kotlin/dev/catsradar/app/navigation/EncounterDetailEffectHandlerTest.kt`

**Interfaces:**
- Produces (Task 2 relies on these exact shapes):
  ```kotlin
  // EncounterDetailIntent
  data class CoatPicked(val catId: String, val coat: CoatOption?)
  data class TakePhotoClicked(val catId: String)
  data class PickPhotoClicked(val catId: String)
  data class PhotoClicked(val catId: String, val photoId: String)
  data class CoordinatesClicked(val catId: String)
  data class PhotoTaken(val catId: String, val uri: String?)
  data class PhotoPicked(val catId: String, val uri: String?)
  // EncounterDetailEffect
  data class OpenCamera(val catId: String)
  data class OpenPhotoPicker(val catId: String)
  data class OpenPhoto(val catId: String, val photoId: String)
  data class OpenMap(val catId: String)
  // app: handleEncounterDetailEffect(..., onOpenPhoto: (PhotoViewer) -> Unit, onOpenMap: (catId: String) -> Unit, ...)
  // app: EncounterDetailDestination(..., onOpenPhoto: (PhotoViewer) -> Unit, onOpenMap: (catId: String) -> Unit, ...)
  ```

- [ ] **Step 1: Update the existing Store tests to the new shapes, and add the id tests**

In `EncounterDetailStoreTest.kt`, every dispatch of `CoatPicked`, `TakePhotoClicked`, `PickPhotoClicked`, `PhotoClicked`, `CoordinatesClicked`, `PhotoTaken`, `PhotoPicked` gains `ID` as its first argument (`TakePhotoClicked` → `TakePhotoClicked(ID)`, `PhotoTaken(CAPTURE)` → `PhotoTaken(ID, CAPTURE)`, `CoatPicked(coat)` → `CoatPicked(ID, coat)`, `PhotoClicked(photoId)` → `PhotoClicked(ID, photoId)`), and every expected `OpenCamera`, `OpenPhotoPicker`, `OpenPhoto`, `OpenMap` likewise (`OpenCamera` → `OpenCamera(ID)`, `OpenPhoto(photoId)` → `OpenPhoto(ID, photoId)`, `OpenMap` → `OpenMap(ID)`). No other change to those tests.

Add these tests before the private helpers (`newStore`):

```kotlin
    @Test
    fun `the camera and the picker open for the cat they were asked for`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(ID), awaitItem())
            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, uri = null))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhotoPicker(ID), awaitItem())
        }
    }

    @Test
    fun `a photo lands on the cat its result names, not on the one the screen observes`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
        store.dispatch(EncounterDetailIntent.PhotoTaken(OTHER, CAPTURE))
        runCurrent()

        assertEquals(1, repository.observeById(OTHER).value()?.photos?.size)
        assertEquals(0, repository.observeById(ID).value()?.photos?.size)
    }

    @Test
    fun `a coat lands on the cat it was picked for`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED))
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.CoatPicked(OTHER, CoatOption.GINGER))
        runCurrent()

        assertEquals(CoatOption.GINGER, repository.observeById(OTHER).value()?.coat?.toOption())
        assertEquals(null, repository.observeById(ID).value()?.coat)
    }

    @Test
    fun `the viewer and the map open on the cat that was tapped`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(lat = 41.39, lon = 2.17).withPhoto())
        val store = newStore()
        runCurrent()
        val photoId = assertIs<EncounterDetailState.Loaded>(store.state.value).photos.first().id

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked(ID, photoId))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhoto(ID, photoId), awaitItem())
            store.dispatch(EncounterDetailIntent.CoordinatesClicked(ID))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenMap(ID), awaitItem())
        }
    }
```

Add `const val OTHER = "cat-2"` to the companion object, and import `dev.catsradar.presentation.coat.CoatOption` and `dev.catsradar.presentation.coat.toOption`. `withPhoto()` is the existing fixture helper already imported by the test; if its signature differs, follow the other photo tests in the file. The two tests with `OTHER` would pass against a Store that ignored the id only if it wrote to `OTHER` by chance — they pin that the Store acts on the id an intent names. They assert only where the write lands: the screen's own *attaching* state still follows the one cat it observes, and tracking attaching per cat is P3's.

- [ ] **Step 2: Run the Store tests and watch them fail to compile**

Run: `./gradlew :presentation:testAndroidHostTest --tests 'dev.catsradar.presentation.detail.EncounterDetailStoreTest' --rerun --console=plain 2>&1 | tail -30`
Expected: compilation FAIL — `TakePhotoClicked` has no constructor with an argument, and so on.

- [ ] **Step 3: Change the intents and effects**

`EncounterDetailIntent.kt`:

```kotlin
package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption

sealed interface EncounterDetailIntent {
    data object BackClicked : EncounterDetailIntent
    data object DeleteClicked : EncounterDetailIntent
    data object UndoClicked : EncounterDetailIntent

    /** [coat] of null clears it. */
    data class CoatPicked(val catId: String, val coat: CoatOption?) : EncounterDetailIntent

    data class TakePhotoClicked(val catId: String) : EncounterDetailIntent
    data class PickPhotoClicked(val catId: String) : EncounterDetailIntent
    data class PhotoClicked(val catId: String, val photoId: String) : EncounterDetailIntent
    data class CoordinatesClicked(val catId: String) : EncounterDetailIntent

    /** [uri] is null when the camera was cancelled. */
    data class PhotoTaken(val catId: String, val uri: String?) : EncounterDetailIntent

    /** [uri] is null when the picker was dismissed. */
    data class PhotoPicked(val catId: String, val uri: String?) : EncounterDetailIntent
}
```

`EncounterDetailEffect.kt`:

```kotlin
package dev.catsradar.presentation.detail

sealed interface EncounterDetailEffect {
    data object NavigateBack : EncounterDetailEffect
    data class OpenCamera(val catId: String) : EncounterDetailEffect
    data class OpenPhotoPicker(val catId: String) : EncounterDetailEffect
    data class OpenPhoto(val catId: String, val photoId: String) : EncounterDetailEffect
    data class OpenMap(val catId: String) : EncounterDetailEffect
    data object PhotoNotAttached : EncounterDetailEffect
    data object PhotoAlreadyThere : EncounterDetailEffect

    /** The camera's original at [uri] is no longer needed. */
    data class DiscardCapture(val uri: String) : EncounterDetailEffect
}
```

- [ ] **Step 4: Make the Store act on the named cat**

In `EncounterDetailStore.handle`, replace the arms of the seven intents:

```kotlin
            // A failed write leaves the shown coat as it was: the flow re-emits the stored value.
            is EncounterDetailIntent.CoatPicked -> runStorageWrite { setCoat(intent.catId, intent.coat?.toCatCoat()) }
            is EncounterDetailIntent.TakePhotoClicked -> requestPhoto(EncounterDetailEffect.OpenCamera(intent.catId))
            is EncounterDetailIntent.PickPhotoClicked -> requestPhoto(EncounterDetailEffect.OpenPhotoPicker(intent.catId))
            is EncounterDetailIntent.PhotoClicked ->
                if ((state.value as? EncounterDetailState.Loaded)?.photos.orEmpty().any { it.id == intent.photoId }) {
                    emit(EncounterDetailEffect.OpenPhoto(intent.catId, intent.photoId))
                }
            is EncounterDetailIntent.CoordinatesClicked ->
                if ((state.value as? EncounterDetailState.Loaded)?.onTheMap == true) {
                    emit(EncounterDetailEffect.OpenMap(intent.catId))
                }
            is EncounterDetailIntent.PhotoTaken -> onPhotoChosen(intent.catId, intent.uri, PhotoSource.CAMERA)
            is EncounterDetailIntent.PhotoPicked -> onPhotoChosen(intent.catId, intent.uri, PhotoSource.GALLERY)
```

and give `onPhotoChosen` the id:

```kotlin
    private suspend fun onPhotoChosen(catId: String, uri: String?, source: PhotoSource) {
        …unchanged…
        runStorageWrite { result = attachPhoto(catId, uri, source) }
        …unchanged…
    }
```

`encounterId` stays the id the Store observes, deletes and undoes; nothing else changes.

- [ ] **Step 5: Run the Store tests to green**

Run the Step 2 command. Expected: BUILD SUCCESSFUL; XML at `presentation/build/test-results/testAndroidHostTest/TEST-dev.catsradar.presentation.detail.EncounterDetailStoreTest.xml` shows the old count + 4, `failures="0"`.

- [ ] **Step 6: Wire `:app` to the new shapes**

`handleEncounterDetailEffect` in `EncounterDetailDestination.kt`:

```kotlin
@Suppress("LongParameterList") // one collaborator per effect the screen has to carry out
internal fun handleEncounterDetailEffect(
    effect: EncounterDetailEffect,
    onNavigateBack: () -> Unit,
    onOpenPhoto: (PhotoViewer) -> Unit,
    onOpenMap: (catId: String) -> Unit,
    cameraLauncher: CameraLauncher,
    photoPickerLauncher: PhotoPickerLauncher,
    photoFailureReporter: PhotoFailureReporter,
    alreadyThereReporter: PhotoFailureReporter,
    captureDiscarder: CaptureDiscarder,
) {
    when (effect) {
        EncounterDetailEffect.NavigateBack -> onNavigateBack()
        is EncounterDetailEffect.OpenCamera -> cameraLauncher.launch()
        is EncounterDetailEffect.OpenPhotoPicker -> photoPickerLauncher.launch()
        is EncounterDetailEffect.OpenPhoto -> onOpenPhoto(PhotoViewer(effect.catId, effect.photoId))
        is EncounterDetailEffect.OpenMap -> onOpenMap(effect.catId)
        EncounterDetailEffect.PhotoNotAttached -> photoFailureReporter.report()
        EncounterDetailEffect.PhotoAlreadyThere -> alreadyThereReporter.report()
        is EncounterDetailEffect.DiscardCapture -> captureDiscarder.discard(effect.uri)
    }
}
```

(The camera and picker still open without their cat here; Task 2 hands it over.)

`EncounterDetailDestination`: parameters become `onOpenPhoto: (PhotoViewer) -> Unit` and `onOpenMap: (catId: String) -> Unit`; the forwarded lambdas become `onOpenPhoto = { viewer -> currentOnOpenPhoto(viewer) }`, `onOpenMap = { catId -> currentOnOpenMap(catId) }`; the launchers dispatch `PhotoTaken(key.id, uri)` and `PhotoPicked(key.id, uri)`; the screen callbacks dispatch `CoatPicked(key.id, coat)`, `TakePhotoClicked(key.id)`, `PickPhotoClicked(key.id)`, `PhotoClicked(key.id, photoId)`, `CoordinatesClicked(key.id)`. `EncounterDetailScreen` and its callbacks do not change.

`CatsRadarNavHost.kt`, `entry<EncounterDetail>`:

```kotlin
            onOpenPhoto = { viewer -> backStack.push(viewer) },
            onOpenMap = { catId ->
                mapFocus.postCat(catId)
                backStack.selectTab(BottomNavTab.MAP)
            },
```

`EncounterDetailEffectHandlerTest`: `onOpenPhoto = { viewer -> calls += "photo ${viewer.encounterId} ${viewer.photoId}" }`, `onOpenMap = { catId -> calls += "map $catId" }`; the handled effects become `OpenCamera("cat-1")`, `OpenPhotoPicker("cat-1")`, `OpenPhoto("cat-1", "second")`, `OpenMap("cat-1")`; the expected list's entries become `"photo cat-1 second"` and `"map cat-1"`.

- [ ] **Step 7: Run the app's detail tests, then commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.navigation.EncounterDetailEffectHandlerTest' --tests 'dev.catsradar.app.navigation.*EncounterDetail*' --tests 'dev.catsradar.app.viewer.*' --rerun --console=plain 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL. (If the unit-test task has a different name, `./gradlew :app:tasks --all | grep -i unittest` finds it.)

```bash
git add presentation/src app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt app/src/test/kotlin/dev/catsradar/app/navigation/EncounterDetailEffectHandlerTest.kt
git commit -m "The detail screen's intents and effects name their cat"
```

---

### Task 2: The camera and the picker keep their cat across process death

**Files:**
- Modify: `app/src/main/kotlin/dev/catsradar/app/photo/PendingCaptures.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/photo/PendingCapturesTest.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/PhotoLaunchers.kt`
- Create: `app/src/test/kotlin/dev/catsradar/app/testing/RecordingActivityResultRegistry.kt` (moved out of `GalleryImportPickerTest.kt`)
- Modify: `app/src/test/kotlin/dev/catsradar/app/navigation/GalleryImportPickerTest.kt` (uses the moved registry)
- Create: `app/src/test/kotlin/dev/catsradar/app/navigation/PhotoLaunchersTest.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/CounterDestination.kt`, `CounterEffectHandler.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/navigation/CounterEffectHandlerTest.kt`, `EncounterDetailEffectHandlerTest.kt`
- Modify: `docs/features/encounter-detail.md`, `docs/features/widget.md` (its `PendingCaptures` line)

**Interfaces:**
- Consumes: Task 1's intents and effects.
- Produces:
  ```kotlin
  // app/photo
  data class PendingCapture(val target: String, val catId: String?)
  class PendingCaptures { fun launched(capture: PendingCapture); fun answered(): PendingCapture?; companion object { val Saver } }
  // app/navigation
  internal fun interface CameraLauncher { fun launch(catId: String?) }
  internal data class CameraShot(val catId: String?, val uri: String?)
  @Composable internal fun rememberCameraLauncher(onResult: (CameraShot) -> Unit): CameraLauncher
  internal fun interface CatPhotoPickerLauncher { fun launch(catId: String) }
  internal data class PickedPhoto(val catId: String, val uri: String?)
  @Composable internal fun rememberCatPhotoPicker(onResult: (PickedPhoto) -> Unit): CatPhotoPickerLauncher
  ```
  `rememberSinglePhotoPicker` is removed (its only caller moves to `rememberCatPhotoPicker`); `PhotoPickerLauncher` stays for the gallery import.

- [ ] **Step 1: Failing `PendingCaptures` tests**

Replace `PendingCapturesTest.kt`:

```kotlin
package dev.catsradar.app.photo

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PendingCapturesTest {

    @Test
    fun eachResultBelongsToTheOldestCameraStillWaiting() {
        val pending = PendingCaptures()
        pending.launched(PendingCapture("closed-under-the-newer-one", catId = "cat-a"))
        pending.launched(PendingCapture("newer", catId = "cat-b"))

        assertEquals(PendingCapture("closed-under-the-newer-one", "cat-a"), pending.answered())
        assertEquals(PendingCapture("newer", "cat-b"), pending.answered())
        assertNull(pending.answered())
    }

    @Test
    fun theWaitingTargetsAndTheirCatsSurviveTheProcessDying() {
        val pending = PendingCaptures().apply {
            launched(PendingCapture("for-a-cat", catId = "cat-a"))
            launched(PendingCapture("for-a-new-cat", catId = null))
        }

        val restored = restore(save(pending))

        assertEquals(PendingCapture("for-a-cat", "cat-a"), restored.answered())
        assertEquals(PendingCapture("for-a-new-cat", null), restored.answered())
    }

    @Test
    fun aQueueSavedBeforeShotsNamedTheirCatRestoresEmptyRatherThanGuessingOne() {
        val restored = restore(listOf("content://captures/1", "content://captures/2"))

        assertNull(restored.answered())
    }

    private fun save(pending: PendingCaptures): Any =
        assertNotNull(with(PendingCaptures.Saver) { SaverScope { true }.save(pending) })

    private fun restore(saved: Any): PendingCaptures = assertNotNull(PendingCaptures.Saver.restore(saved))
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.photo.PendingCapturesTest' --rerun --console=plain 2>&1 | tail -30` — expected compilation FAIL (`PendingCapture` unresolved).

- [ ] **Step 2: `PendingCaptures` records each shot's cat**

```kotlin
package dev.catsradar.app.photo

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** A camera told to write to [target] for [catId]; null when the shot logs a new cat. */
data class PendingCapture(val target: String, val catId: String?)

/**
 * The cameras still open, oldest first. A camera closed under a newer one still reports back, and
 * first, so each result belongs to the oldest capture still waiting.
 */
class PendingCaptures(captures: List<PendingCapture> = emptyList()) {

    private val captures = ArrayDeque(captures)

    fun launched(capture: PendingCapture) {
        captures.addLast(capture)
    }

    /** The capture the next camera result belongs to, or null when no camera was waiting. */
    fun answered(): PendingCapture? = captures.removeFirstOrNull()

    companion object {
        // A queue saved by a version whose shots named no cat restores empty: guessing a cat would be permanent.
        private const val SHAPE = "pending-captures/2"

        val Saver: Saver<PendingCaptures, Any> = listSaver(
            save = { pending -> listOf(SHAPE) + pending.captures.flatMap { listOf(it.target, it.catId.orEmpty()) } },
            restore = { saved ->
                if (saved.firstOrNull() != SHAPE) {
                    PendingCaptures()
                } else {
                    PendingCaptures(
                        saved.drop(1).chunked(2) { (target, catId) -> PendingCapture(target, catId.ifEmpty { null }) },
                    )
                }
            },
        )
    }
}
```

(Cat ids are generated and never empty, so `""` safely stands for "no cat" in the saved list. If the listSaver's element type needs to be explicit, declare it `listSaver<PendingCaptures, String>`.)

Run the Step 1 command. Expected: 3/3 pass (XML).

- [ ] **Step 3: Move the recording registry to a shared test helper**

Create `app/src/test/kotlin/dev/catsradar/app/testing/RecordingActivityResultRegistry.kt` holding the `RecordingRegistry` class from the bottom of `GalleryImportPickerTest.kt`, renamed `RecordingActivityResultRegistry`, `internal` instead of `private`, unchanged otherwise. Delete it from `GalleryImportPickerTest.kt` and use the new name there. `GalleryImportPickerTest` must still pass unchanged.

- [ ] **Step 4: Failing launcher tests, across recreation**

Create `app/src/test/kotlin/dev/catsradar/app/navigation/PhotoLaunchersTest.kt`:

```kotlin
package dev.catsradar.app.navigation

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.RecordingActivityResultRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class PhotoLaunchersTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val registry = RecordingActivityResultRegistry()

    @Test
    fun aShotReachesTheCatTheCameraWasOpenedForAfterTheScreenIsRecreated() {
        val shots = mutableListOf<CameraShot>()
        lateinit var camera: CameraLauncher
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                camera = rememberCameraLauncher { shots += it }
            }
        }

        compose.runOnIdle { camera.launch("cat-b") }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.answer(true) }

        val shot = shots.single()
        assertEquals("cat-b", shot.catId)
        assertNotNull(shot.uri)
    }

    @Test
    fun aCancelledCameraStillNamesItsCatAndNoPhoto() {
        val shots = mutableListOf<CameraShot>()
        lateinit var camera: CameraLauncher
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                camera = rememberCameraLauncher { shots += it }
            }
        }

        compose.runOnIdle {
            camera.launch("cat-b")
            registry.answer(false)
        }

        assertEquals(CameraShot(catId = "cat-b", uri = null), shots.single())
    }

    @Test
    fun theCountersShotNamesNoCat() {
        val shots = mutableListOf<CameraShot>()
        lateinit var camera: CameraLauncher
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                camera = rememberCameraLauncher { shots += it }
            }
        }

        compose.runOnIdle {
            camera.launch(catId = null)
            registry.answer(true)
        }

        assertNull(shots.single().catId)
    }

    @Test
    fun aPickReachesTheCatThePickerWasOpenedForAfterTheScreenIsRecreated() {
        val picks = mutableListOf<PickedPhoto>()
        lateinit var picker: CatPhotoPickerLauncher
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                picker = rememberCatPhotoPicker { picks += it }
            }
        }

        compose.runOnIdle { picker.launch("cat-b") }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { registry.answer(Uri.parse(PHOTO)) }

        assertEquals(PickedPhoto(catId = "cat-b", uri = PHOTO), picks.single())
    }

    @Test
    fun aDismissedPickerNamesItsCatAndNoPhoto() {
        val picks = mutableListOf<PickedPhoto>()
        lateinit var picker: CatPhotoPickerLauncher
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                picker = rememberCatPhotoPicker { picks += it }
            }
        }

        compose.runOnIdle {
            picker.launch("cat-b")
            registry.answer<Uri?>(null)
        }

        assertEquals(PickedPhoto(catId = "cat-b", uri = null), picks.single())
    }

    private companion object {
        const val PHOTO = "content://media/picker/0/com.android.providers.media.photopicker/media/20"
    }
}
```

`StateRestorationTester` lives in `androidx.compose.ui.test.junit4` (ui-test-junit4); if the project's `v2` rule cannot be passed to it, use the non-`v2` `createComposeRule()` for this class and say so in the report. If the camera's `CaptureTarget.newUri` fails under Robolectric, report NEEDS_CONTEXT with the error rather than faking it.

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.navigation.PhotoLaunchersTest' --rerun --console=plain 2>&1 | tail -30` — expected compilation FAIL.

- [ ] **Step 5: The launchers carry the cat**

In `PhotoLaunchers.kt` replace `CameraLauncher`, `rememberCameraLauncher` and `rememberSinglePhotoPicker`:

```kotlin
/** Opens the camera for [catId] — null when the shot logs a new cat; it owns the file the camera writes to. */
internal fun interface CameraLauncher {
    fun launch(catId: String?)
}

/** [uri] is null when the camera was cancelled. */
internal data class CameraShot(val catId: String?, val uri: String?)

internal fun interface CatPhotoPickerLauncher {
    fun launch(catId: String)
}

/** [uri] is null when the picker was dismissed. */
internal data class PickedPhoto(val catId: String, val uri: String?)

@Composable
internal fun rememberCameraLauncher(onResult: (CameraShot) -> Unit): CameraLauncher {
    val context = LocalContext.current
    // Saveable: the process can die while a camera is in front, and its result says nothing about
    // where it wrote or for which cat.
    val pending = rememberSaveable(saver = PendingCaptures.Saver) { PendingCaptures() }
    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val capture = pending.answered()
        onResult(CameraShot(catId = capture?.catId, uri = capture?.target?.takeIf { saved }))
        if (!saved && capture != null) CaptureTarget.discard(context, capture.target)
    }
    return remember(resultLauncher, context, pending) {
        CameraLauncher { catId ->
            val target = CaptureTarget.newUri(context)
            pending.launched(PendingCapture(target.toString(), catId))
            resultLauncher.launch(target)
        }
    }
}

@Composable
internal fun rememberCatPhotoPicker(onResult: (PickedPhoto) -> Unit): CatPhotoPickerLauncher {
    // Saveable for the same reason as the camera's queue: the picker's answer names no cat.
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val resultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val catId = pickingFor
        pickingFor = null
        if (catId != null) onResult(PickedPhoto(catId, uri?.toString()))
    }
    return remember(resultLauncher) {
        CatPhotoPickerLauncher { catId ->
            pickingFor = catId
            resultLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}
```

Imports: `dev.catsradar.app.photo.PendingCapture`, `androidx.compose.runtime.getValue`, `setValue`, `mutableStateOf`. Delete `rememberSinglePhotoPicker`.

- [ ] **Step 6: Callers**

- `CounterEffectHandler.kt`: `CounterEffect.OpenCamera -> cameraLauncher.launch(catId = null)`.
- `CounterDestination.kt`: `rememberCameraLauncher { shot -> store.dispatch(CounterIntent.PhotoCaptured(shot.uri)) }`.
- `CounterEffectHandlerTest.kt`: `CountingCameraLauncher.launch(catId: String?)` counts as before.
- `EncounterDetailDestination.kt`:
  - `handleEncounterDetailEffect` takes `photoPickerLauncher: CatPhotoPickerLauncher`; `is OpenCamera -> cameraLauncher.launch(effect.catId)`; `is OpenPhotoPicker -> photoPickerLauncher.launch(effect.catId)`.
  - `val cameraLauncher = rememberCameraLauncher { shot -> shot.catId?.let { store.dispatch(EncounterDetailIntent.PhotoTaken(it, shot.uri)) } }` — a shot with no cat came from a queue that lost it and has nothing to attach to.
  - `val photoPicker = rememberCatPhotoPicker { picked -> store.dispatch(EncounterDetailIntent.PhotoPicked(picked.catId, picked.uri)) }`.
- `EncounterDetailEffectHandlerTest.kt`: `cameraLauncher = { catId -> calls += "camera $catId" }`, `photoPickerLauncher = { catId -> calls += "picker $catId" }`; expected `"camera cat-1"`, `"picker cat-1"`.

- [ ] **Step 7: Green, then prove the recreation tests can fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.photo.PendingCapturesTest' --tests 'dev.catsradar.app.navigation.PhotoLaunchersTest' --tests 'dev.catsradar.app.navigation.GalleryImportPickerTest' --tests 'dev.catsradar.app.navigation.CounterEffectHandlerTest' --tests 'dev.catsradar.app.navigation.EncounterDetailEffectHandlerTest' --rerun --console=plain 2>&1 | tail -30`. Expected: all pass (XML per class).

Commit, then break on purpose one at a time, record which test fails, and restore with `git checkout -- <file>`:

| # | Mutation | Must fail |
|---|----------|-----------|
| M1 | `rememberCatPhotoPicker`: `remember { mutableStateOf<String?>(null) }` instead of `rememberSaveable` | `aPickReachesTheCatThePickerWasOpenedForAfterTheScreenIsRecreated` |
| M2 | `rememberCameraLauncher`: `remember { PendingCaptures() }` instead of `rememberSaveable(saver = …)` | `aShotReachesTheCatTheCameraWasOpenedForAfterTheScreenIsRecreated` |
| M3 | Saver `save`: drop the catIds (`listOf(SHAPE) + captures.flatMap { listOf(it.target, "") }`) | `theWaitingTargetsAndTheirCatsSurviveTheProcessDying` |
| M4 | Saver `restore`: accept any list (remove the `SHAPE` check, read pairs from index 0) | `aQueueSavedBeforeShotsNamedTheirCatRestoresEmptyRatherThanGuessingOne` |

- [ ] **Step 8: Docs**

- `docs/features/encounter-detail.md`, *Giving a cat a photo*: after the sentence about the second tap opening nothing, add: "The camera and the picker are opened for a named cat, and their answer names it back — even when the process died while they were in front, since the camera's queue and the picker remember the cat with the rest of the screen's saved state (`PhotoLaunchersTest`; `PendingCapturesTest`). A queue saved by an older version, whose shots named no cat, restores empty: the capture file waits for the start-up cleanup rather than landing on a guessed cat." In *Where the code lives*, name `PhotoLaunchers.kt` as holding the camera and the cat's photo picker, and add `app/…/photo/PendingCaptures.kt`.
- `docs/features/widget.md`: its `PendingCaptures` line reads "which camera a result belongs to, and for which cat".

- [ ] **Step 9: Commit**

```bash
git add app/src docs/features/encounter-detail.md docs/features/widget.md
git commit -m "The camera and the picker keep the cat they were opened for"
```

---

### Task 3: Check, review, gate, PR

- [ ] `./gradlew check --rerun-tasks` green (through `ctx_execute`).
- [ ] Push `tech/detail-names-its-cat` (HTTP/1.1 retry on a framing error), open a draft PR "The detail screen's intents and effects name their cat" with the criteria and evidence; map P2 → `in-review`.
- [ ] `/code-review` on the branch; fix findings in new commits.
- [ ] `acceptance:acceptance-gate` against `outing-pager-p2.md`.
- [ ] Mark ready. Merging is the owner's call; once merged, P2 → `merged` with a decision-log line and this plan moves to `archive/`.
