# Slice V1 — Fullscreen photo viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a cat's photo on its detail screen opens it fullscreen, above the bottom bar, with pinch, double-tap and one-finger zoom, pan and fling; back closes it.

**Architecture:** A new MVI screen (`PhotoViewerStore` + mapper in `:presentation`, `PhotoViewerScreen` in `:ui`) behind a Navigation 3 key `PhotoViewer(encounterId)` whose entry is drawn by Navigation 3's own `DialogSceneStrategy` in an edge-to-edge dialog window. Gestures come from Telephoto's `ZoomableAsyncImage` over the app's Coil 3. The detail Store turns a photo tap into an `OpenPhoto` effect; the `:app` entry pushes the key.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00, Navigation 3 1.2.0-rc01, Coil 3.3.0, Telephoto 0.19.0 (`me.saket.telephoto:zoomable-image-coil3`), Koin, Turbine, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-24-photo-viewer-and-gallery-link-design.md` (§ The viewer). Map: `docs/tbd/decompositions/2026-09-24-photo-viewer.md` (V1).

## Global Constraints

- Every version lives in `gradle/libs.versions.toml`; modules configure nothing but dependencies.
- `:presentation` imports no `androidx.compose.*`; `:ui` imports no `dev.catsradar.data`; State classes hold no lambdas; collections in State are `Immutable*`.
- No hard-coded colours in screens: a colour not in the scheme goes into `ui/theme` first.
- No user-facing text in code: EN `ui/src/main/res/values/strings.xml` and RU `values-ru/strings.xml`.
- Every NavKey is `@Serializable`. Only `BottomNavBackStack.kt` touches a raw `NavBackStack`.
- Comments: default none; one line, two at most; only facts a stranger needs (see `docs/rules/code-commenting-standards.md`).
- `./gradlew check` green before the PR; fresh test evidence needs `--rerun` on the tasks that ran.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: The viewer's Store and mapper

**Files:**
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerState.kt`
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerIntent.kt`
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerEffect.kt`
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerStateMapper.kt`
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer/PhotoViewerStore.kt`
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/viewer/PhotoViewerStateMapperTest.kt`
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/viewer/PhotoViewerStoreTest.kt`

**Interfaces:**
- Consumes: `ObserveEncounter(repository)(id): Flow<Encounter?>`; `PhotoStorage.resolve(relativePath): String`;
  test doubles `FakeEncounterRepository` (`presentation.counter`), `FakePhotoStorage(root = "/data/photos")`
  and `encounterFixture(id, occurredAt, …)` (`presentation.encounters`).
- Produces: `PhotoViewerState` (`Loading`, `Showing(photoPath: String)`), `PhotoViewerIntent.BackClicked`,
  `PhotoViewerEffect.Close`, `PhotoViewerStateMapper(photoStorage).map(encounter): PhotoViewerState.Showing?`,
  `PhotoViewerStore(encounterId, observeEncounter, stateMapper)`.

- [ ] **Step 1: Write the failing mapper test**

```kotlin
package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PhotoViewerStateMapperTest {

    private val mapper = PhotoViewerStateMapper(FakePhotoStorage(root = "/data/photos"))

    @Test
    fun `a cat with a photo shows the app's copy by its absolute path`() {
        val cat = encounterFixture("cat-1", OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "cat-1.jpg")

        assertEquals(PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg"), mapper.map(cat))
    }

    @Test
    fun `a cat without a photo has nothing to show`() {
        assertEquals(null, mapper.map(encounterFixture("cat-1", OCCURRED)))
    }

    private companion object {
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
```

- [ ] **Step 2: Write the failing Store test**

```kotlin
package dev.catsradar.presentation.viewer

import app.cash.turbine.test
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.presentation.counter.FakeEncounterRepository
import dev.catsradar.presentation.encounters.FakePhotoStorage
import dev.catsradar.presentation.encounters.encounterFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoViewerStoreTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val repository = FakeEncounterRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a cat with a photo shows its copy`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        assertEquals(PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg"), store.state.value)
    }

    @Test
    fun `a cat without a photo closes the viewer instead of showing black`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()

        store.effects.test {
            runCurrent()
            assertEquals(PhotoViewerEffect.Close, awaitItem())
            assertEquals(PhotoViewerState.Loading, store.state.value)
        }
    }

    @Test
    fun `an id nobody has seen closes the viewer`() = runTest(mainDispatcher) {
        val store = newStore()

        store.effects.test {
            runCurrent()
            assertEquals(PhotoViewerEffect.Close, awaitItem())
        }
    }

    @Test
    fun `a cat deleted while on screen closes the viewer, and back after it closes nothing more`() =
        runTest(mainDispatcher) {
            repository.insert(photographedCat())
            val store = newStore()
            runCurrent()

            store.effects.test {
                repository.softDelete(ID, OCCURRED)
                runCurrent()
                assertEquals(PhotoViewerEffect.Close, awaitItem())

                store.dispatch(PhotoViewerIntent.BackClicked)
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `back closes the viewer once`() = runTest(mainDispatcher) {
        repository.insert(photographedCat())
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(PhotoViewerIntent.BackClicked)
            store.dispatch(PhotoViewerIntent.BackClicked)
            runCurrent()
            assertEquals(PhotoViewerEffect.Close, awaitItem())
            expectNoEvents()
        }
    }

    private fun photographedCat() =
        encounterFixture(ID, OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "cat-1.jpg")

    private fun newStore() = PhotoViewerStore(
        encounterId = ID,
        observeEncounter = ObserveEncounter(repository),
        stateMapper = PhotoViewerStateMapper(FakePhotoStorage(root = "/data/photos")),
    )

    private companion object {
        const val ID = "cat-1"
        val OCCURRED = Instant.parse("2026-09-22T10:00:00Z")
    }
}
```

- [ ] **Step 3: Run both to verify they fail**

Run: `./gradlew :presentation:testAndroidHostTest --tests 'dev.catsradar.presentation.viewer.*'`
(use the host-test task name `./gradlew :presentation:tasks --all | grep -i test` reports)
Expected: compilation failure — `PhotoViewerStateMapper`, `PhotoViewerStore` unresolved.

- [ ] **Step 4: Implement**

`PhotoViewerState.kt`
```kotlin
package dev.catsradar.presentation.viewer

sealed interface PhotoViewerState {
    data object Loading : PhotoViewerState

    /** [photoPath] is the absolute path of the app's full copy. */
    data class Showing(val photoPath: String) : PhotoViewerState
}
```

`PhotoViewerIntent.kt`
```kotlin
package dev.catsradar.presentation.viewer

sealed interface PhotoViewerIntent {
    data object BackClicked : PhotoViewerIntent
}
```

`PhotoViewerEffect.kt`
```kotlin
package dev.catsradar.presentation.viewer

sealed interface PhotoViewerEffect {
    data object Close : PhotoViewerEffect
}
```

`PhotoViewerStateMapper.kt`
```kotlin
package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage

class PhotoViewerStateMapper(private val photoStorage: PhotoStorage) {

    /** Null when the cat has no photo to show. */
    fun map(encounter: Encounter): PhotoViewerState.Showing? =
        encounter.photoPath?.let { PhotoViewerState.Showing(photoPath = photoStorage.resolve(it)) }
}
```

`PhotoViewerStore.kt`
```kotlin
package dev.catsradar.presentation.viewer

import androidx.lifecycle.viewModelScope
import dev.catsradar.domain.usecase.ObserveEncounter
import dev.catsradar.presentation.Store
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PhotoViewerStore(
    encounterId: String,
    observeEncounter: ObserveEncounter,
    private val stateMapper: PhotoViewerStateMapper,
) : Store<PhotoViewerState, PhotoViewerIntent, PhotoViewerEffect>(PhotoViewerState.Loading) {

    private var closing = false

    init {
        observeEncounter(encounterId)
            .onEach { encounter ->
                val showing = encounter?.let(stateMapper::map)
                if (showing != null) setState { showing } else close()
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handle(intent: PhotoViewerIntent) {
        when (intent) {
            PhotoViewerIntent.BackClicked -> close()
        }
    }

    private suspend fun close() {
        if (closing) return
        closing = true
        emit(PhotoViewerEffect.Close)
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: the same command with `--rerun`. Expected: 7 tests, 0 failures (read the JUnit XML under
`presentation/build/test-results/`).

- [ ] **Step 6: Commit**

```bash
git add presentation/src/commonMain/kotlin/dev/catsradar/presentation/viewer presentation/src/commonTest/kotlin/dev/catsradar/presentation/viewer
git commit -m "Photo viewer: Store and mapper that close when there is no photo to show"
```

---

### Task 2: A photo tap on the detail screen asks for the viewer

**Files:**
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailIntent.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailEffect.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStore.kt` (`handle`)
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStoreTest.kt`

**Interfaces:**
- Produces: `EncounterDetailIntent.PhotoClicked`, `EncounterDetailEffect.OpenPhoto`.

- [ ] **Step 1: Write the failing tests** (append to `EncounterDetailStoreTest`; imports `EncounterKind`)

```kotlin
    @Test
    fun `a tap on the photo opens the viewer`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED).copy(kind = EncounterKind.PHOTO, photoPath = "cat-1.jpg"))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked)
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhoto, awaitItem())
        }
    }

    @Test
    fun `a cat without a photo has no viewer to open`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.PhotoClicked)
            runCurrent()
            expectNoEvents()
        }
    }
```

- [ ] **Step 2: Run to verify they fail** — `PhotoClicked` / `OpenPhoto` unresolved.

- [ ] **Step 3: Implement**

`EncounterDetailIntent`: add `data object PhotoClicked : EncounterDetailIntent` after `PickPhotoClicked`.
`EncounterDetailEffect`: add `data object OpenPhoto : EncounterDetailEffect` after `OpenPhotoPicker`.
`EncounterDetailStore.handle`: add the branch and the function

```kotlin
            EncounterDetailIntent.PhotoClicked -> onPhotoClicked()
```
```kotlin
    private suspend fun onPhotoClicked() {
        if ((state.value as? EncounterDetailState.Loaded)?.photoPath != null) emit(EncounterDetailEffect.OpenPhoto)
    }
```

- [ ] **Step 4: Run the detail Store tests** (`--tests 'dev.catsradar.presentation.detail.*' --rerun`) — all pass.
  `:app` no longer compiles (`handleEncounterDetailEffect`'s `when` is not exhaustive) until Task 4 — run
  only `:presentation` here.

- [ ] **Step 5: Commit** — "Detail: a tap on the photo asks for the viewer"

---

### Task 3: The viewer screen, and a tappable photo on the detail

**Files:**
- Modify: `gradle/libs.versions.toml` — `telephoto = "0.19.0"` under `[versions]`;
  `telephoto-zoomable-image-coil3 = { module = "me.saket.telephoto:zoomable-image-coil3", version.ref = "telephoto" }`
- Modify: `ui/build.gradle.kts` — `implementation(libs.telephoto.zoomable.image.coil3)`, `implementation(libs.androidx.core.ktx)`
- Create: `ui/src/main/kotlin/dev/catsradar/ui/theme/ViewerColors.kt`
- Create: `ui/src/main/kotlin/dev/catsradar/ui/viewer/PhotoViewerScreen.kt`
- Create: `ui/src/main/res/drawable/ic_arrow_back.xml` (Material Symbols `arrow_back`, `android:autoMirrored="true"`)
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/detail/EncounterDetailScreen.kt` — `onPhotoClick` parameter, clickable photo
- Modify: `ui/src/main/res/values/strings.xml`, `ui/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/kotlin/dev/catsradar/app/viewer/PhotoViewerScreenTest.kt`

**Interfaces:**
- Consumes: `PhotoViewerState` (Task 1).
- Produces: `PhotoViewerScreen(state, modifier, onBackClick)`; `EncounterDetailScreen(…, onPhotoClick: () -> Unit = {})`;
  strings `viewer_back`, `detail_open_photo`.

- [ ] **Step 1: Write the failing UI test**

```kotlin
package dev.catsradar.app.viewer

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.viewer.PhotoViewerScreen
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PhotoViewerScreenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `a tap on the photo hides the top bar and a second tap brings it back`() {
        show()
        back().assertIsDisplayed()

        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        back().assertDoesNotExist()

        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        back().assertIsDisplayed()
    }

    @Test
    fun `the back arrow reports the tap`() {
        var backs = 0
        show(onBackClick = { backs++ })

        back().performClick()

        assertEquals(1, backs)
    }

    private fun show(onBackClick: () -> Unit = {}) = compose.setContent {
        CatsRadarTheme {
            PhotoViewerScreen(state = PhotoViewerState.Showing(photoPath = "/nonexistent/cat.jpg"), onBackClick = onBackClick)
        }
    }

    private fun back() = compose.onNodeWithContentDescription(context.getString(R.string.viewer_back))
}
```

If Telephoto swallows the tap while its image has not loaded, write a real image first
(`File(context.cacheDir, "cat.png")` from a 4×4 `Bitmap.compress(PNG)`) and pass its path instead.

- [ ] **Step 2: Run to verify it fails** — `PhotoViewerScreen` unresolved.

- [ ] **Step 3: Add the dependency and the resources**

`ViewerColors.kt`
```kotlin
package dev.catsradar.ui.theme

import androidx.compose.ui.graphics.Color

// A photo is judged against black whatever the app's theme, as galleries show it.
internal object ViewerColors {
    val Stage = Color.Black
    val OnStage = Color.White
    val ChromeScrim = Color.Black.copy(alpha = 0.5f)
}
```

`ic_arrow_back.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="@android:color/white"
            android:pathData="m313-440 224 224-57 56-320-320 320-320 57 56-224 224h487v80H313Z" />
    </group>
</vector>
```
(verify against the existing icons' `translateY` convention: Material Symbols paths are drawn in
`0..-960`; `ic_close.xml` shows the pattern.)

Strings — EN: `<string name="viewer_back">Back</string>`, `<string name="detail_open_photo">Open full screen</string>`;
RU: `<string name="viewer_back">Назад</string>`, `<string name="detail_open_photo">Открыть на весь экран</string>`.

- [ ] **Step 4: Implement `PhotoViewerScreen`**

```kotlin
package dev.catsradar.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.theme.ThemePreviews
import dev.catsradar.ui.theme.ViewerColors
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@Composable
fun PhotoViewerScreen(
    state: PhotoViewerState,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    SystemBarsVisibility(visible = chromeVisible)
    Box(modifier = modifier.fillMaxSize().background(ViewerColors.Stage)) {
        if (state is PhotoViewerState.Showing) {
            ZoomableAsyncImage(
                model = state.photoPath,
                contentDescription = stringResource(R.string.detail_photo_description),
                modifier = Modifier.fillMaxSize(),
                onClick = { chromeVisible = !chromeVisible },
            )
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopStart),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ViewerTopBar(onBackClick = onBackClick)
        }
    }
}

@Composable
private fun ViewerTopBar(modifier: Modifier = Modifier, onBackClick: () -> Unit = {}) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ViewerColors.ChromeScrim, Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.viewer_back),
                tint = ViewerColors.OnStage,
            )
        }
    }
}

@Composable
private fun SystemBarsVisibility(visible: Boolean) {
    val view = LocalView.current
    // Outside a dialog (a preview, a test) there is no window of the viewer's own to change.
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window, visible) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
}

@ThemePreviews
@Composable
private fun PhotoViewerScreenPreview() {
    CatsRadarTheme { PhotoViewerScreen(state = PhotoViewerState.Showing(photoPath = "/data/photos/cat-1.jpg")) }
}
```

- [ ] **Step 5: Make the detail photo tappable** — in `EncounterDetailScreen` add `onPhotoClick: () -> Unit = {}`
  after `onPickPhotoClick` (both on the public screen and on `LoadedDetail`), pass it down, and on the
  `AsyncImage` modifier chain add
  `.clickable(onClickLabel = stringResource(R.string.detail_open_photo), role = Role.Image, onClick = onPhotoClick)`
  after `.clip(…)` (imports `androidx.compose.foundation.clickable`, `androidx.compose.ui.semantics.Role`).

- [ ] **Step 6: Run** `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.viewer.*'` — note `:app` compiles
  only after Task 4's exhaustive `when`; if it does not compile yet, do Task 4 Step 3's handler branch first.
  Expected: 2 tests pass.

- [ ] **Step 7: Commit** — "Photo viewer screen with Telephoto zoom; the detail photo opens it"

---

### Task 4: Navigation — the viewer above the bottom bar

**Files:**
- Create: `app/src/main/kotlin/dev/catsradar/app/navigation/PhotoViewer.kt`
- Create: `app/src/main/kotlin/dev/catsradar/app/navigation/PhotoViewerDestination.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/CatsRadarNavHost.kt` (scene strategies, entries)
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt` (`onOpenPhoto`)
- Modify: `app/src/main/kotlin/dev/catsradar/app/di/PresentationModule.kt`
- Test: `app/src/test/kotlin/dev/catsradar/app/navigation/PhotoViewerNavigationTest.kt`
- Test: `app/src/test/kotlin/dev/catsradar/app/navigation/EncounterDetailEffectHandlerTest.kt`
- Test: `app/src/test/kotlin/dev/catsradar/app/di/KoinRuntimeResolutionTest.kt`

**Interfaces:**
- Consumes: Task 1's Store, Task 2's `OpenPhoto`, Task 3's screen.
- Produces: `PhotoViewer(encounterId: String) : NavKey`, `photoViewerMetadata()`,
  `PhotoViewerDestination(key, onClose, modifier)`; `handleEncounterDetailEffect(…, onOpenPhoto: () -> Unit, …)`.

- [ ] **Step 1: Write the failing navigation test** (mirrors `BottomSheetNavigationTest`)

```kotlin
package dev.catsradar.app.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.navigation.BottomNavTab
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PhotoViewerNavigationTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val backStack = BottomNavBackStack(NavBackStack<NavKey>(Counter))

    @Before
    fun openACat() {
        compose.setContent {
            CatsRadarNavDisplay(
                backStack = backStack,
                entryProvider = entryProvider {
                    entry<Counter>(metadata = tabRootMetadata()) { Text(COUNTER) }
                    entry<Encounters>(metadata = tabRootMetadata()) { Text(LIST) }
                    entry<EncounterDetail> { Text(CAT) }
                    entry<PhotoViewer>(metadata = photoViewerMetadata()) { Text(VIEWER) }
                },
            )
        }
        settle { backStack.selectTab(BottomNavTab.ENCOUNTERS) }
        settle { backStack.push(CAT_KEY) }
    }

    @Test
    fun `the viewer opens in a window of its own over the cat`() {
        settle { backStack.push(VIEWER_KEY) }

        assertTrue(inDialog(VIEWER), "the viewer is drawn in a dialog window")
        assertTrue(isShown(CAT), "the cat stays drawn under it")
    }

    @Test
    fun `back from the viewer uncovers the cat as it was`() {
        settle { backStack.push(VIEWER_KEY) }

        settle { backStack.popOrNull() }

        assertEquals(listOf(Counter, Encounters, CAT_KEY), backStack.toList())
        assertFalse(isShown(VIEWER), "the viewer is gone")
        assertTrue(isShown(CAT), "the cat is shown")
    }

    private fun settle(change: () -> Unit) {
        compose.runOnUiThread(change)
        compose.waitForIdle()
    }

    private fun isShown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun inDialog(text: String) =
        compose.onAllNodes(hasText(text) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val COUNTER = "counter screen"
        const val LIST = "list screen"
        const val CAT = "cat screen"
        const val VIEWER = "viewer screen"
        val CAT_KEY = EncounterDetail("a")
        val VIEWER_KEY = PhotoViewer("a")
    }
}
```

Check `backStack.toList()` for the Encounters tab root key's actual name (`Encounters`) and for
what `selectTab(ENCOUNTERS)` leaves on the stack (`BottomNavigationTest` shows it). If the dialog
root has no `isDialog()` ancestor semantics, assert with `compose.onAllNodes(isDialog())` non-empty
instead.

- [ ] **Step 2: Extend the effect handler test** — in `EncounterDetailEffectHandlerTest.handle` add
  `onOpenPhoto = { calls += "photo" },`, dispatch `EncounterDetailEffect.OpenPhoto` after `OpenPhotoPicker`
  and expect `"photo"` after `"picker"` in the list.

- [ ] **Step 3: Implement**

`PhotoViewer.kt`
```kotlin
package dev.catsradar.app.navigation

import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import kotlinx.serialization.Serializable

@Serializable
data class PhotoViewer(val encounterId: String) : NavKey

internal fun photoViewerMetadata(): Map<String, Any> = DialogSceneStrategy.dialog(
    DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
)
```

`PhotoViewerDestination.kt`
```kotlin
package dev.catsradar.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catsradar.presentation.viewer.PhotoViewerEffect
import dev.catsradar.presentation.viewer.PhotoViewerIntent
import dev.catsradar.presentation.viewer.PhotoViewerStore
import dev.catsradar.ui.viewer.PhotoViewerScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PhotoViewerDestination(key: PhotoViewer, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val store = koinViewModel<PhotoViewerStore> { parametersOf(key.encounterId) }
    val state by store.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                PhotoViewerEffect.Close -> close()
            }
        }
    }
    PhotoViewerScreen(
        state = state,
        modifier = modifier,
        onBackClick = { store.dispatch(PhotoViewerIntent.BackClicked) },
    )
}
```

`CatsRadarNavDisplay`: `val dialogs = remember { DialogSceneStrategy<NavKey>() }` next to `sheets`, and
`sceneStrategies = listOf(sheets, dialogs)`.

`catsRadarEntries`: the detail entry gains `onOpenPhoto = { backStack.push(PhotoViewer(key.id)) }`, and a
new entry after it:

```kotlin
    entry<PhotoViewer>(metadata = photoViewerMetadata()) { key ->
        PhotoViewerDestination(key = key, onClose = { backStack.popIfOnTop(key) })
    }
```

`EncounterDetailDestination`: add `onOpenPhoto: () -> Unit = {}` (after `onNavigateBack`), a
`rememberUpdatedState` for it, pass `onOpenPhoto = { currentOnOpenPhoto() }` to the handler, and
`onPhotoClick = { store.dispatch(EncounterDetailIntent.PhotoClicked) }` to the screen.
`handleEncounterDetailEffect` gains `onOpenPhoto: () -> Unit` after `onNavigateBack` and the branch
`EncounterDetailEffect.OpenPhoto -> onOpenPhoto()`.

`PresentationModule`:
```kotlin
    factoryOf(::PhotoViewerStateMapper)
    viewModel { (encounterId: String) ->
        PhotoViewerStore(encounterId = encounterId, observeEncounter = get(), stateMapper = get())
    }
```

`KoinRuntimeResolutionTest`: `assertNotNull(koin.get<PhotoViewerStore> { parametersOf("any-id") })`.

- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.navigation.*' --tests 'dev.catsradar.app.di.*' --tests 'dev.catsradar.app.viewer.*' --tests 'dev.catsradar.app.architecture.*' --rerun`
  Expected: all pass, including `NavKeySerializableTest`, `NamingConventionTest`, `ModuleBoundaryTest`.

- [ ] **Step 5: Commit** — "Photo viewer: a dialog destination above the bottom bar, opened from the detail"

---

### Task 5: Docs, full check, device run

**Files:**
- Create: `docs/features/photo-viewer.md`
- Modify: `docs/features/README.md` (list entry), `docs/features/encounter-detail.md` (the photo opens the viewer;
  where the code lives), `docs/features/photos.md` (*Seeing one*: the fullscreen viewer),
  `docs/tbd/decompositions/2026-09-24-photo-viewer.md` (V1 status)

- [ ] **Step 1: Write `photo-viewer.md`** — what opens it, what it shows and why the copy rather than the
  original, the gestures, the chrome toggle, closing (back, the arrow, the cat disappearing), process death,
  where the code lives, not built yet (swipe-down, between cats, share, from lists or the Map; the gallery
  action arrives with V2). Name the tests behind each edge.
- [ ] **Step 2: Update the other docs** listed above.
- [ ] **Step 3: Run** `./gradlew check` — green. Then `--rerun` the test tasks the slice touched and read the XML.
- [ ] **Step 4: Device run** on an isolated install (`applicationIdSuffix` init script, see memory
  `shared-emulator`): open a cat with a photo, tap it, pinch, double-tap, drag with one finger after a
  double-tap-hold, tap to hide the chrome, back. Screenshot before/after.
- [ ] **Step 5: Commit** — "Docs: the photo viewer"
