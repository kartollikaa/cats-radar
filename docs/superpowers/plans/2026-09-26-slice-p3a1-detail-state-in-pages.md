# Slice P3a-1 — The detail state holds pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `EncounterDetailState.Loaded` carries `pages` of `CatPage` and the cat on screen; the screen draws that page and every tap names its cat; the Store still reads one cat, wrapped in a one-cat `OutingWindow`. The screen looks and behaves exactly as today.

**Architecture:** In `:presentation`, the per-cat fields of `Loaded` move to a new `CatPage`, and `Loaded` becomes `pages`, `currentId` and `currentNumber`. The mapper splits in two: `page(encounter, …)` builds one `CatPage` (today's `map` body), and `map(window, currentId, today, attaching, places)` builds a page for each cat of an `OutingWindow`. The Store keeps observing its one cat and hands the mapper `OutingWindow(listOf(cat), newer = null, older = null)`. Its tap guards read the cat's page instead of `Loaded`, still only for the observed cat (P3a-2 widens them). In `:ui`, the screen draws the page for `currentId` and wraps each page callback with that page's id; a callback carrying two values takes an `*Interaction` payload. In `:app`, the destination dispatches those ids instead of `key.id`. Task 1 moves the State together with everything that reads it (the screen draws the page, callbacks unchanged); Task 2 adds the ids.

**Tech Stack:** Kotlin Multiplatform, Compose, Robolectric + Compose UI test, turbine, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-25-outing-pager-design.md` § The Store (the `Loaded` shape), § Intents and effects name their cat. Map: `docs/tbd/decompositions/2026-09-25-outing-pager.md` row P3a-1 and *Slice P3a-1*. Criteria: `outing-pager-p3a1.md` in the acceptance directory (Task 0).

## Global Constraints

- **Behaviour does not change.** The window always holds exactly the observed cat, so the screen looks and behaves as it does on main: one cat, no position, the same taps opening the same things. The Store's tap guards stay "the observed cat only".
- **Out of scope (P3a-2, P3b):** `OutingPages`, `ObserveEncounters` in the Store, a second cat in the window, widening the tap guards, Koin changes, `PageSettled`, the pager, restore by id, `outings.md`.
- **Base.** Branch `tech/detail-state-in-pages`, cut from `origin/main` e180faf3 (P2 merged as #183) plus the docs commit 754fcb79. The working tree is current main: every file below is quoted from it, including L2 (*Set on map*: `SetLocationClicked(catId)`, `OpenLocationPicker(catId)`, `setsLocation`, `onSetLocationClick`), the photos-know-their-shot changes (#174/#186), the detail place (#168) and `mapPosition`. At the start of every task, check HEAD (`git branch --show-current`, `git rev-parse --short HEAD`) and compare against `origin/main..HEAD`.
- **Given shapes, unchanged:** every per-cat intent and effect already carries `catId` (P2). `DeleteClicked` and `UndoClicked` stay objects (ruling: delete acts on the cat on screen; P4 reworks it). `onSetLocationClick` stays a one-value callback, now `(catId: String) -> Unit`.
- **MVI** (`docs/rules/mvi-architecture.md`): "**State is data.** `data class`, immutable collections (`kotlinx.collections.immutable`), no function types, no platform types." "**Mappers build state.**"
- **Compose** (`docs/rules/compose-patterns.md` §5): "When a callback carries more than one value, wrap the payload in an `*Interaction` data class instead of a multi-parameter lambda." "Pass callbacks down unchanged. … Wrap only at the level that owns data the child doesn't know (a pager adding its `page`)." §2: "Never put a lambda in a State class." §6: "`modifier: Modifier = Modifier` is the first optional parameter."
- **Boundaries** (Konsist `ModuleBoundaryTest`): `:presentation` imports no `androidx.compose.*` and no `dev.catsradar.data`; `:ui` imports no `dev.catsradar.data`.
- **detekt**, with no baseline, runs in `check`. `EncounterDetailStore` has ten functions today (`reduce`, `handle`, `emitIfShownAndOffered`, `requestPhoto`, `onPhotosChosen`, `refresh`, `onDeleteClicked`, `startUndoWindow`, `navigateBack`, `onUndoClicked`), and `onDeleteClicked` keeps its inline `restore` lambda. This slice adds no function to the class; its one new helper, `page(catId)`, is top-level in the file. `handle` gains no branch.
- **Comments** (`docs/rules/code-commenting-standards.md`): "**Default: no comment.**" "**One line. Two at most.**" Re-read every added comment against the five tests before committing.
- **Tests first.** Every Gradle run goes through the context-mode `ctx_execute` tool (language `shell`, `timeout` 900000; a hook refuses Gradle in Bash), with `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" &&` first and `| tail -30` last. `--rerun` applies to one task only, so it follows **each** test task in a command. Counts come from the JUnit XML, never from the console. Greps use absolute paths.
- **Every task's commit compiles every module.** PRs land with merge commits, so each commit stays in main's history. Task 1 therefore moves the read side (`:ui` and the screen tests' fixtures) together with the State; Task 2 adds only the ids. Each task's green run includes `:app:testDebugUnitTest`.
- **Keep every test main has.** Tests are migrated, never deleted; the only renames are listed in the steps.
- **Docs in the same PR:** `docs/features/encounter-detail.md` (Task 3).

---

### Task 0: Freeze the acceptance criteria

Controller runs `acceptance:acceptance-criteria` (auto) into `outing-pager-p3a1.md` before any code. The criteria name each item with its evidence:
- the new mapper and Store tests of Task 1 and the new screen tests of Task 2, by name;
- the migrated test classes (`EncounterDetailStateMapperTest`, `EncounterDetailStoreTest`, `EncounterDetailStorePhotoTest`, `EncounterDetailPickSeveralTest`, `EncounterDetailScreenTest`, `EncounterDetailCoatPickerTest`, `DetailPhotoPagerTest`, and the L2 screen tests), each keeping its test count;
- mutations M1–M6 each failing their named test;
- no `key.id` left in `EncounterDetailDestination` outside the Store's parameters — the destination's only guard in this slice, since no test can fail on it until P3b's entry tests;
- no function added to `EncounterDetailStore`;
- the `encounter-detail.md` statements of Task 3, and the map row P3a-1 at ~630;
- every module compiling at each task's final commit;
- `./gradlew check` green.

---

### Task 1: State in pages, built from a one-cat window; the screen reads its page

**Files:**
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStateMapperTest.kt`
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStoreTest.kt` (both classes in it: `EncounterDetailStoreTest`, `EncounterDetailStorePhotoTest`)
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailPickSeveralTest.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailState.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStateMapper.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStore.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/detail/EncounterDetailScreen.kt` (the read side: the page drawn, `CatPageContent`, the samples)
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/detail/WhereCard.kt`
- Create: `app/src/test/kotlin/dev/catsradar/app/detail/DetailStates.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailScreenTest.kt`, `EncounterDetailCoatPickerTest.kt`, `DetailPhotoPagerTest.kt` (their fixtures)

Task 1 changes what the screen reads and nothing it sends: every screen callback keeps its type, so `:ui` and `:app` compile at the task's commit, and the screen draws exactly what it draws today.

**Interfaces:**
- Consumes: `OutingWindow(cats: List<Encounter> /* newest first */, newer: List<Encounter>?, older: List<Encounter>?)` from `dev.catsradar.domain.session` (P1).
- Produces (Task 2, P3a-2 and P3b rely on these exact shapes):
  ```kotlin
  data class Loaded(val pages: ImmutableList<CatPage>, val currentId: String, val currentNumber: Int) : EncounterDetailState
  data class CatPage(
      val id: String, val dayLabel: String, val timeLabel: String, val location: LocationLabel,
      val coordinatesLabel: String?, val accuracyMeters: Int?,
      val photos: ImmutableList<DetailPhoto> = persistentListOf(), val coat: CoatOption? = null,
      val addPhoto: AddPhoto = AddPhoto.READY, val attachProgress: AttachProgress? = null,
      val mapPosition: MapPosition? = null, val setsLocation: Boolean = false, val place: DetailPlace? = null,
  )
  // EncounterDetailStateMapper
  fun map(window: OutingWindow, currentId: String, today: LocalDate,
      attaching: Map<String, AttachProgress> = emptyMap(), places: Map<String, EncounterPlace?> = emptyMap(),
  ): EncounterDetailState.Loaded
  internal fun page(encounter: Encounter, today: LocalDate, attaching: AttachProgress? = null,
      place: EncounterPlace? = null): CatPage
  // ui/detail (callback types as on main)
  @Composable private fun CatPageContent(page: CatPage, modifier: Modifier = Modifier,
      contentPadding: PaddingValues = PaddingValues(), onDeleteClick: () -> Unit = {},
      onCoatClick: (CoatOption?) -> Unit = {}, onTakePhotoClick: () -> Unit = {}, onPickPhotoClick: () -> Unit = {},
      onPhotoClick: (photoId: String) -> Unit = {}, onCoordinatesClick: () -> Unit = {},
      onSetLocationClick: () -> Unit = {})
  @Composable internal fun WhereCard(page: CatPage, modifier: Modifier = Modifier,
      onCoordinatesClick: () -> Unit = {}, onSetLocationClick: () -> Unit = {})
  // app/src/test …/detail/DetailStates.kt
  internal fun loadedWith(page: CatPage): EncounterDetailState.Loaded
  ```
  The Store's constructor does not change.

- [ ] **Step 1: Move the mapper tests to pages, and add the window tests**

In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStateMapperTest.kt`:
- every `mapper.map(` and `offsetMapper.map(` becomes `mapper.page(` / `offsetMapper.page(`, keeping all its arguments, named ones included (`attaching = AttachProgress(0, 1)`, `place = place`). After Step 1, `grep -n 'mapper\.map(\|offsetMapper\.map(' "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStateMapperTest.kt"` prints only the two `mapper.map(` calls of the new window tests;
- in *an encounter with a fix maps every field, coordinates with five decimals*, the expected `EncounterDetailState.Loaded(` becomes `CatPage(`, with `id = "e1",` as its first argument; nothing else in that test changes;
- imports: add `dev.catsradar.domain.session.OutingWindow` and `kotlinx.collections.immutable.persistentListOf`.

Add these tests before *coordinates keep a fixed five decimals with a decimal point, negatives included*:

```kotlin
    @Test
    fun `a window maps to one page per cat, newest first, and names the cat on screen and its position`() {
        val older = encounterFixture("older", OCCURRED)
        val newer = encounterFixture("newer", OCCURRED + 5.minutes).withPhoto(photoPath = "newer.jpg")
        val window = OutingWindow(cats = listOf(newer, older), newer = null, older = null)

        val state = mapper.map(window, currentId = "older", today = today)

        assertEquals(
            EncounterDetailState.Loaded(
                pages = persistentListOf(mapper.page(newer, today), mapper.page(older, today)),
                currentId = "older",
                currentNumber = 2,
            ),
            state,
        )
    }

    @Test
    fun `each page takes its own cat's place and attempt`() {
        val older = encounterFixture("older", OCCURRED)
        val newer = encounterFixture("newer", OCCURRED + 5.minutes)
        val window = OutingWindow(cats = listOf(newer, older), newer = null, older = null)
        val barcelona = EncounterPlace(countryCode = "ES", country = "Spain", city = "Barcelona")
        val lisbon = EncounterPlace(countryCode = "PT", country = "Portugal", city = "Lisbon")

        val state = mapper.map(
            window,
            currentId = "older",
            today = today,
            attaching = mapOf("newer" to AttachProgress(done = 1, total = 3)),
            places = mapOf("newer" to lisbon, "older" to barcelona),
        )

        assertEquals(listOf("Lisbon", "Barcelona"), state.pages.map { it.place?.title })
        assertEquals(listOf(AttachProgress(done = 1, total = 3), null), state.pages.map { it.attachProgress })
        assertEquals(listOf(AddPhoto.ATTACHING, AddPhoto.READY), state.pages.map { it.addPhoto })
    }
```

The review's point M4, "places are per page", is covered here at the mapper, where two cats on different cells each get their own place. The Store-level version (two cats observed on two cells) waits for P3a-2: until then the Store observes one cat, so its window never holds two.

- [ ] **Step 2: Move the Store tests to the page on screen, and pin the one-cat window**

In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStoreTest.kt` (both classes):

1. At the bottom of the file, before `private object LocatesNoGalleryItem`, add:

   ```kotlin
   private fun EncounterDetailStore.shownPage(): CatPage =
       assertIs<EncounterDetailState.Loaded>(state.value).let { loaded ->
           loaded.pages.single { it.id == loaded.currentId }
       }
   ```
2. Every read of a cat's field off the `Loaded` state reads it off `shownPage()` instead:
   - `assertIs<EncounterDetailState.Loaded>(store.state.value).<field>` becomes `store.shownPage().<field>`;
   - `val state = assertIs<EncounterDetailState.Loaded>(store.state.value)` whose `state.<field>` is read afterwards becomes `val state = store.shownPage()`;
   - `(store.state.value as EncounterDetailState.Loaded).place` (twice, in *the cat's place reaches the screen once its cell is named*) becomes `store.shownPage().place`.

   The three bare `assertIs<EncounterDetailState.Loaded>(store.state.value)` lines, which read nothing, stay. Afterwards, `grep -nE 'Loaded>\(store\.state\.value\)\.|val state = assertIs<EncounterDetailState\.Loaded>|as EncounterDetailState\.Loaded\)' "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStoreTest.kt"` prints nothing.
3. In the class `EncounterDetailStoreTest`, after *an existing encounter renders as loaded with its fields*, add:

   ```kotlin
       @Test
       fun `the observed cat is the one page, and it is on screen`() =
           runTest(mainDispatcher) {
               repository.insert(encounterFixture(ID, OCCURRED))
               val store = newStore()
               runCurrent()

               val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
               assertEquals(listOf(ID), state.pages.map { it.id })
               assertEquals(ID, state.currentId)
               assertEquals(1, state.currentNumber)
           }
   ```

   It pins the one-cat window the slice's safety rests on (M6 proves it can fail); P3a-2 rewrites it for the whole outing.

In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailPickSeveralTest.kt`:
- `private fun loaded(store: EncounterDetailStore) = assertIs<EncounterDetailState.Loaded>(store.state.value)` becomes
  ```kotlin
      private fun shownPage(store: EncounterDetailStore): CatPage =
          assertIs<EncounterDetailState.Loaded>(store.state.value).let { state ->
              state.pages.single { it.id == state.currentId }
          }
  ```
  and every `loaded(store)` in the file becomes `shownPage(store)`, with the field read after it unchanged;
- in *every picked photo lands after the cat's own, in the order picked*, `val state = assertIs<EncounterDetailState.Loaded>(store.state.value)` becomes `val state = shownPage(store)`.

`CatPage` lives in the tests' own package; nothing to import.

- [ ] **Step 3: The screen tests build their state from one page**

Create `app/src/test/kotlin/dev/catsradar/app/detail/DetailStates.kt`:

```kotlin
package dev.catsradar.app.detail

import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.EncounterDetailState
import kotlinx.collections.immutable.persistentListOf

internal fun loadedWith(page: CatPage): EncounterDetailState.Loaded =
    EncounterDetailState.Loaded(pages = persistentListOf(page), currentId = page.id, currentNumber = 1)
```

Then the fixtures of the three screen test classes, and nothing else in them; each keeps every test and its count (`EncounterDetailScreenTest` 16, `EncounterDetailCoatPickerTest` 3, `DetailPhotoPagerTest` 5). The screen's callbacks keep their types in this task, so no callback argument changes:

- `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailScreenTest.kt`:
  1. In the companion, `val loaded = EncounterDetailState.Loaded(` becomes `val cat = CatPage(` with `id = "cat-1",` as its first argument, and `val onTheMap = loaded.copy(` becomes `val onTheMap = cat.copy(`. Both comment lines above them stay.
  2. Everywhere else, `loaded.copy(` becomes `cat.copy(`, and every expression that passed `loaded`, `cat.copy(…)` or `onTheMap` as a screen state is wrapped in `loadedWith(…)`. That covers `show(loaded)` → `show(loadedWith(cat))`, `show(loaded, onBackClick = …)` → `show(loadedWith(cat), onBackClick = …)`, `show(loaded.copy(…))` → `show(loadedWith(cat.copy(…)))`, `show(onTheMap…)` → `show(loadedWith(onTheMap)…)`, and L2's `EncounterDetailScreen(state = loaded.copy(setsLocation = true), onSetLocationClick = { taps++ })` inside `setContent` → `EncounterDetailScreen(state = loadedWith(cat.copy(setsLocation = true)), onSetLocationClick = { taps++ })`.
  3. `private fun EncounterDetailState.hasMap() = (this as? EncounterDetailState.Loaded)?.mapPosition != null` becomes `private fun EncounterDetailState.hasMap() = (this as? EncounterDetailState.Loaded)?.pages?.any { it.mapPosition != null } == true`.
  4. Import `dev.catsradar.presentation.detail.CatPage`.

  Afterwards, `grep -n 'loaded' "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailScreenTest.kt"` finds only `loadedWith(`.
- `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailCoatPickerTest.kt`, in `showDetail`: `state = EncounterDetailState.Loaded(` becomes `state = loadedWith(CatPage(` with `id = "cat-1",` first and the same arguments, closed by one more `)`. Import `dev.catsradar.presentation.detail.CatPage` and drop the `EncounterDetailState` import.
- `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/test/kotlin/dev/catsradar/app/detail/DetailPhotoPagerTest.kt`: `catWith` becomes `private fun catWith(vararg photoIds: String) = loadedWith(CatPage(id = "cat-1", …the same six arguments…))`, one argument per line. Import `dev.catsradar.presentation.detail.CatPage`.

- [ ] **Step 4: Run the presentation and screen tests and watch them fail to compile**

Run: `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && ./gradlew :presentation:testAndroidHostTest --tests 'dev.catsradar.presentation.detail.*' --rerun --console=plain 2>&1 | tail -30`
Expected: compilation FAIL — `CatPage`, `mapper.page` and `Loaded.pages` are unresolved. (`:app`'s tests cannot compile before `:presentation` does; they run in Step 9.)

- [ ] **Step 5: State in pages**

`EncounterDetailState.kt` becomes:

```kotlin
package dev.catsradar.presentation.detail

import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

sealed interface EncounterDetailState {

    data object Loading : EncounterDetailState

    data class Loaded(
        /** Newest first. */
        val pages: ImmutableList<CatPage>,
        val currentId: String,
        /** 1-based position of [currentId] on [pages]. */
        val currentNumber: Int,
    ) : EncounterDetailState

    /** The user deleted this encounter from this screen; [undoVisible] is false once the window closed. */
    data class Deleted(val undoVisible: Boolean) : EncounterDetailState

    /** No live encounter has this id: it was never there, was purged, or was deleted elsewhere. */
    data object Missing : EncounterDetailState
}

data class CatPage(
    val id: String,
    val dayLabel: String,
    val timeLabel: String,
    val location: LocationLabel,
    val coordinatesLabel: String?,
    val accuracyMeters: Int?,
    /** Oldest first. */
    val photos: ImmutableList<DetailPhoto> = persistentListOf(),
    val coat: CoatOption? = null,
    val addPhoto: AddPhoto = AddPhoto.READY,
    /** Null unless several photos are being attached. */
    val attachProgress: AttachProgress? = null,
    /** Null when the map does not draw this cat. */
    val mapPosition: MapPosition? = null,
    /** Whether the cat can be given a location on a map: it has none. */
    val setsLocation: Boolean = false,
    /** Null while the cat has no named place: no location, or its cell not named yet. */
    val place: DetailPlace? = null,
)

/** [path] is the absolute path of the app's full copy. */
data class DetailPhoto(val id: String, val path: String)

enum class AddPhoto { READY, ATTACHING }

/** [done] of the [total] photos being attached are through, whether or not each one landed. */
data class AttachProgress(val done: Int, val total: Int) {
    val fraction: Float get() = done.toFloat() / total
}

/** [title] is the city, or the country when no city is known; [country] is set only under a city. */
data class DetailPlace(val title: String, val country: String?, val flag: String?)
```

(The per-cat fields and their KDoc move from `Loaded` to `CatPage` word for word; `Deleted`, `Missing` and the types below are unchanged.)

- [ ] **Step 6: The mapper builds pages from a window**

In `EncounterDetailStateMapper.kt`, replace the class body (the `COORDINATE_DECIMALS` constant and `formatCoordinate` below the class stay):

```kotlin
class EncounterDetailStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    /** [currentId] is one of [window]'s cats; [attaching] and [places] are keyed by cat id. */
    fun map(
        window: OutingWindow,
        currentId: String,
        today: LocalDate,
        attaching: Map<String, AttachProgress> = emptyMap(),
        places: Map<String, EncounterPlace?> = emptyMap(),
    ): EncounterDetailState.Loaded {
        val pages = window.cats.map { cat -> page(cat, today, attaching[cat.id], places[cat.id]) }
        return EncounterDetailState.Loaded(
            pages = pages.toImmutableList(),
            currentId = currentId,
            currentNumber = pages.indexOfFirst { it.id == currentId } + 1,
        )
    }

    internal fun page(
        encounter: Encounter,
        today: LocalDate,
        attaching: AttachProgress? = null,
        place: EncounterPlace? = null,
    ): CatPage {
        val lat = encounter.lat
        val lon = encounter.lon
        return CatPage(
            id = encounter.id,
            dayLabel = dateTimeFormatter.dayHeader(encounter, today),
            timeLabel = dateTimeFormatter.time(encounter),
            location = encounter.locationSource.toLocationLabel(),
            coordinatesLabel = if (lat != null && lon != null) {
                "${formatCoordinate(lat)}, ${formatCoordinate(lon)}"
            } else {
                null
            },
            accuracyMeters = encounter.accuracyMeters?.takeIf { lat != null && lon != null }?.roundToInt(),
            photos = encounter.photos.map { DetailPhoto(it.id, photoStorage.resolve(it.photoPath)) }.toImmutableList(),
            coat = encounter.coat?.toOption(),
            addPhoto = if (attaching != null) AddPhoto.ATTACHING else AddPhoto.READY,
            attachProgress = attaching?.takeIf { it.total > 1 },
            mapPosition = if (lat != null && lon != null && encounter.isOnTheMap()) MapPosition(lat, lon) else null,
            setsLocation = encounter.locationSource == LocationSource.NONE,
            place = place?.let { found ->
                // A city-state's locality repeats its country's name.
                val city = found.city?.takeIf { !it.equals(found.country, ignoreCase = true) }
                DetailPlace(
                    title = city ?: found.country,
                    country = if (city != null) found.country else null,
                    flag = countryFlag(found.countryCode),
                )
            },
        )
    }
}
```

Add `import dev.catsradar.domain.session.OutingWindow`. `page` is today's `map` body with `CatPage(id = encounter.id, …)` in place of `EncounterDetailState.Loaded(…)`.

- [ ] **Step 7: The Store wraps its one cat in a window**

In `EncounterDetailStore.kt`:

1. Add `import dev.catsradar.domain.session.OutingWindow`.
2. `reduce` becomes

   ```kotlin
       // A null emission after our own delete is the delete taking effect, not the encounter vanishing.
       private fun EncounterDetailState.reduce(encounter: Encounter?): EncounterDetailState = when {
           encounter != null -> stateMapper.map(
               OutingWindow(cats = listOf(encounter), newer = null, older = null),
               currentId = encounter.id,
               today = clock.today(timeZone),
               attaching = attachProgress.takeIf { attachingPhotos || arrivingPhotoIds.isNotEmpty() }
                   ?.let { mapOf(encounter.id to it) }
                   .orEmpty(),
               places = mapOf(encounter.id to lastPlace),
           )
           deletedHere -> this
           else -> EncounterDetailState.Missing
       }
   ```
3. `emitIfShownAndOffered` reads the cat's page:

   ```kotlin
       private suspend fun emitIfShownAndOffered(
           catId: String,
           effect: EncounterDetailEffect,
           offered: CatPage.() -> Boolean,
       ) {
           if (catId == encounterId && state.value.page(catId)?.offered() == true) emit(effect)
       }
   ```
   The three lambdas passed to it in `handle` (`photos.any { … }`, `mapPosition != null`, `setsLocation`) compile unchanged against `CatPage`.
4. In `requestPhoto`, `val offered = (state.value as? EncounterDetailState.Loaded)?.addPhoto == AddPhoto.READY` becomes `val offered = state.value.page(encounterId)?.addPhoto == AddPhoto.READY`. It checks the observed cat's offer, as it does today; P3a-2 makes it per cat.
5. Directly after `private fun Encounter.photoIds()`, before the `message()` comment and function that follow it, add:

   ```kotlin
   private fun EncounterDetailState.page(catId: String): CatPage? =
       (this as? EncounterDetailState.Loaded)?.pages?.firstOrNull { it.id == catId }
   ```

The class keeps its ten functions; `handle` keeps its branches.

- [ ] **Step 8: The screen reads its page**

In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/ui/src/main/kotlin/dev/catsradar/ui/detail/EncounterDetailScreen.kt`:

1. `EncounterDetailScreen`'s parameters do not change. Its `is EncounterDetailState.Loaded -> LoadedDetail(` arm becomes
   ```kotlin
               is EncounterDetailState.Loaded -> CatPageContent(
                   state.pages.first { it.id == state.currentId },
                   contentPadding = belowBar,
                   onDeleteClick = onDeleteClick,
                   onCoatClick = onCoatClick,
                   onTakePhotoClick = onTakePhotoClick,
                   onPickPhotoClick = onPickPhotoClick,
                   onPhotoClick = onPhotoClick,
                   onCoordinatesClick = onCoordinatesClick,
                   onSetLocationClick = onSetLocationClick,
               )
   ```
2. `private fun LoadedDetail(state: EncounterDetailState.Loaded, …)` becomes `private fun CatPageContent(page: CatPage, …)`, and every `state.` inside its body becomes `page.` (`page.photos`, `page.addPhoto`, `page.attachProgress`, `page.dayLabel`, `page.timeLabel`, `page.coat`), with `WhereCard(page, onCoordinatesClick = onCoordinatesClick, onSetLocationClick = onSetLocationClick)`. Its callback parameters keep their types.
3. The samples at the bottom become:
   ```kotlin
   private val sampleLoaded = EncounterDetailState.Loaded(
       pages = persistentListOf(
           CatPage(
               id = "5f1c2d9e",
               dayLabel = "Today",
               timeLabel = "14:32",
               location = LocationLabel.CURRENT,
               coordinatesLabel = "41.39864, 2.17842",
               accuracyMeters = 12,
               photos = persistentListOf(
                   DetailPhoto(id = "5f1c2d9e", path = "photos/5f1c2d9e-4b7a.jpg"),
                   DetailPhoto(id = "8a03b6c1", path = "photos/8a03b6c1-77d2.jpg"),
               ),
               mapPosition = MapPosition(latitude = 41.39864, longitude = 2.17842),
               place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
           ),
       ),
       currentId = "5f1c2d9e",
       currentNumber = 1,
   )

   private val sampleNoLocation = EncounterDetailState.Loaded(
       pages = persistentListOf(
           CatPage(
               id = "2b6d0f73",
               dayLabel = "Yesterday",
               timeLabel = "09:05",
               location = LocationLabel.NONE,
               coordinatesLabel = null,
               accuracyMeters = null,
               addPhoto = AddPhoto.READY,
           ),
       ),
       currentId = "2b6d0f73",
       currentNumber = 1,
   )
   ```
4. Import `dev.catsradar.presentation.detail.CatPage`.

In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/ui/src/main/kotlin/dev/catsradar/ui/detail/WhereCard.kt`: the parameter `state: EncounterDetailState.Loaded` of `WhereCard` and of `WhereLines` becomes `page: CatPage`, and every `state.` inside both becomes `page.` (`WhereCard` passes `page` on to `WhereLines`). The two previews call `WhereCard(page = sampleOnTheMap, …)` / `WhereCard(page = sampleNotOnTheMap, …)`. `sampleOnTheMap` and `sampleNotOnTheMap` become `CatPage(` with `id = "7c2e91a4",` and `id = "d05b3f18",` first and the same arguments. Replace the `EncounterDetailState` import with `dev.catsradar.presentation.detail.CatPage`.

The screen now draws the page State names as on screen; what it draws, and every callback it has, is as on main.

- [ ] **Step 9: Run the presentation, screen and entry tests to green**

Run: `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && ./gradlew :presentation:testAndroidHostTest --tests 'dev.catsradar.presentation.detail.*' --rerun :app:testDebugUnitTest --tests 'dev.catsradar.app.detail.*' --tests 'dev.catsradar.app.navigation.*' --rerun --console=plain 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL, so every module compiles.
- In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/presentation/build/test-results/testAndroidHostTest/`, the XML shows `tests="20"` for `EncounterDetailStateMapperTest` (main's 18 + 2) and `tests="17"` for `EncounterDetailStoreTest` (16 + 1). `EncounterDetailStorePhotoTest` shows `tests="29"` and `EncounterDetailPickSeveralTest` `tests="12"` (both unchanged).
- In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/build/test-results/testDebugUnitTest/`, `EncounterDetailScreenTest` shows 16, `EncounterDetailCoatPickerTest` 3 and `DetailPhotoPagerTest` 5.
- Every one of those files has `failures="0" errors="0"`.

The counts were taken on e180faf3. If main has moved, recount with `grep -c '@Test'` on `git show origin/main:<path>`.

- [ ] **Step 10: Commit**

```bash
cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && git add presentation/src ui/src app/src && git commit -m "The detail state holds pages, built from a one-cat window"
```

- [ ] **Step 11: Prove the new tests can fail**

Break one thing at a time and run the `:presentation` half of the Step 9 command. Record from the XML which test fails, then restore the file with `git -C "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" checkout -- <path>`, where `<path>` is `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStateMapper.kt` for M1–M2 and `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStore.kt` for M3 and M6:

| # | Mutation | Must fail |
|---|----------|-----------|
| M1 | mapper `map`: `currentNumber = 1` | *a window maps to one page per cat, newest first, and names the cat on screen and its position* |
| M2 | mapper `map`: `page(cat, today, attaching[cat.id], places.values.firstOrNull())` | *each page takes its own cat's place and attempt* |
| M3 | Store `reduce`: `places = emptyMap()` | *the cat's place reaches the screen once its cell is named* |
| M6 | Store `reduce`: `OutingWindow(cats = listOf(encounter, encounter), newer = null, older = null)` | *the observed cat is the one page, and it is on screen* |

---

### Task 2: Every tap names the cat on screen; the destination sends that id

**Files:**
- Create: `ui/src/main/kotlin/dev/catsradar/ui/detail/DetailInteractions.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/detail/EncounterDetailScreen.kt` (callback types and wrappers)
- Modify: `app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt`
- Create: `app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailPagesTest.kt`
- Modify: `app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailScreenTest.kt`, `DetailPhotoPagerTest.kt` (one callback argument each)

**Interfaces:**
- Consumes: Task 1's `Loaded`, `CatPage`, `CatPageContent`, `loadedWith`.
- Produces (P3b wraps the same callbacks per page of its pager):
  ```kotlin
  data class CoatInteraction(val catId: String, val coat: CoatOption?)
  data class PhotoInteraction(val catId: String, val photoId: String)
  @Composable fun EncounterDetailScreen(
      state: EncounterDetailState, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(),
      onBackClick: () -> Unit = {}, onDeleteClick: () -> Unit = {}, onUndoClick: () -> Unit = {},
      onCoatClick: (CoatInteraction) -> Unit = {}, onTakePhotoClick: (catId: String) -> Unit = {},
      onPickPhotoClick: (catId: String) -> Unit = {}, onPhotoClick: (PhotoInteraction) -> Unit = {},
      onCoordinatesClick: (catId: String) -> Unit = {}, onSetLocationClick: (catId: String) -> Unit = {},
  )
  ```

- [ ] **Step 1: A failing screen test, and the two callback arguments that change**

Create `app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailPagesTest.kt`:

```kotlin
package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.map.SpotMapTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class EncounterDetailPagesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `the screen draws the cat on screen, and its taps name that cat`() {
        val taps = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = secondOnScreen(unlocated),
                    onPhotoClick = { taps += "photo ${it.catId} ${it.photoId}" },
                    onTakePhotoClick = { taps += "take $it" },
                    onPickPhotoClick = { taps += "pick $it" },
                    onSetLocationClick = { taps += "set on map $it" },
                    onCoatClick = { taps += "coat ${it.catId} ${it.coat}" },
                )
            }
        }
        compose.onNodeWithText(SECOND_TIME).assertExists()
        compose.onNodeWithText(FIRST_TIME).assertDoesNotExist()

        compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_take_photo)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_pick_photo)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_set_location)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.coat_ginger)).performScrollTo().performClick()

        assertEquals(
            listOf("photo cat-2 photo-2", "take cat-2", "pick cat-2", "set on map cat-2", "coat cat-2 GINGER"),
            taps,
        )
    }

    @Test
    fun `a tap on the map of the cat on screen names that cat`() {
        val taps = mutableListOf<String>()
        compose.setContent {
            // As in EncounterDetailScreenTest's show(): the map cannot start on the JVM.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CatsRadarTheme {
                    EncounterDetailScreen(state = secondOnScreen(located), onCoordinatesClick = { taps += it })
                }
            }
        }

        compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true).performTouchInput { click() }

        assertEquals(listOf("cat-2"), taps)
    }

    private fun secondOnScreen(second: CatPage) = EncounterDetailState.Loaded(
        pages = persistentListOf(first, second),
        currentId = second.id,
        currentNumber = 2,
    )

    private companion object {
        const val FIRST_TIME = "14:32"
        const val SECOND_TIME = "14:25"

        val first = CatPage(
            id = "cat-1",
            dayLabel = "Today",
            timeLabel = FIRST_TIME,
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        )

        val unlocated = CatPage(
            id = "cat-2",
            dayLabel = "Today",
            timeLabel = SECOND_TIME,
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            photos = persistentListOf(DetailPhoto(id = "photo-2", path = "/data/photos/photo-2.jpg")),
            setsLocation = true,
        )

        val located = unlocated.copy(
            location = LocationLabel.CURRENT,
            coordinatesLabel = "41.39864, 2.17842",
            photos = persistentListOf(),
            mapPosition = MapPosition(latitude = 41.39864, longitude = 2.17842),
            setsLocation = false,
        )
    }
}
```

The two pages share a day but not a time. `FIRST_TIME … assertDoesNotExist()` is what tells them apart, and `SECOND_TIME … assertExists()` shows the second page is the one composed. `assertExists()` rather than `assertIsDisplayed()`: at `w360dp-h640dp` the time label sits at the fold, and the claim is which page is drawn, not where it scrolls. The class keeps `EncounterDetailScreenTest`'s qualifiers rather than moving to `w360dp-h800dp` like `EncounterDetailCoatPickerTest`, so every tap here — the lower ones behind `performScrollTo()` — runs on the same screen as the existing detail tests.

In the existing tests, only the two arguments whose callback type changes:
- `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/test/kotlin/dev/catsradar/app/detail/EncounterDetailScreenTest.kt`: in the private `show(onBackClick, onCoordinatesClick, state)`, `onCoordinatesClick = onCoordinatesClick,` becomes `onCoordinatesClick = { onCoordinatesClick() },`. Both `show` functions keep their `() -> Unit` parameters. L2's `onSetLocationClick = { taps++ }` compiles as is, taking the cat id as its unused `it`.
- `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/test/kotlin/dev/catsradar/app/detail/DetailPhotoPagerTest.kt`: in `show`, `onPhotoClick = onPhotoClick` becomes `onPhotoClick = { onPhotoClick(it.photoId) }`.

- [ ] **Step 2: Run the screen tests and watch them fail to compile**

Run: `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && ./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.detail.*' --rerun --console=plain 2>&1 | tail -30`
Expected: compilation FAIL — the screen's callbacks take no id yet, and `CoatInteraction` and `PhotoInteraction` are unresolved.

- [ ] **Step 3: The interaction payloads**

Create `ui/src/main/kotlin/dev/catsradar/ui/detail/DetailInteractions.kt`:

```kotlin
package dev.catsradar.ui.detail

import dev.catsradar.presentation.coat.CoatOption

data class CoatInteraction(val catId: String, val coat: CoatOption?)

data class PhotoInteraction(val catId: String, val photoId: String)
```

- [ ] **Step 4: The screen names the page's cat in every tap**

In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/ui/src/main/kotlin/dev/catsradar/ui/detail/EncounterDetailScreen.kt`:

1. `EncounterDetailScreen`'s callback parameters from `onCoatClick` to `onSetLocationClick` become:
   ```kotlin
       onCoatClick: (CoatInteraction) -> Unit = {},
       onTakePhotoClick: (catId: String) -> Unit = {},
       onPickPhotoClick: (catId: String) -> Unit = {},
       onPhotoClick: (PhotoInteraction) -> Unit = {},
       onCoordinatesClick: (catId: String) -> Unit = {},
       onSetLocationClick: (catId: String) -> Unit = {},
   ```
2. Task 1's `is EncounterDetailState.Loaded -> CatPageContent(…)` arm becomes:
   ```kotlin
               is EncounterDetailState.Loaded -> {
                   val page = state.pages.first { it.id == state.currentId }
                   CatPageContent(
                       page,
                       contentPadding = belowBar,
                       onDeleteClick = onDeleteClick,
                       onCoatClick = { coat -> onCoatClick(CoatInteraction(page.id, coat)) },
                       onTakePhotoClick = { onTakePhotoClick(page.id) },
                       onPickPhotoClick = { onPickPhotoClick(page.id) },
                       onPhotoClick = { photoId -> onPhotoClick(PhotoInteraction(page.id, photoId)) },
                       onCoordinatesClick = { onCoordinatesClick(page.id) },
                       onSetLocationClick = { onSetLocationClick(page.id) },
                   )
               }
   ```
   `CatPageContent` and `WhereCard` keep their parameters: the screen is the level that knows the page, so it wraps (`compose-patterns.md` §5).

- [ ] **Step 5: The destination dispatches the page's id**

In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt`, the six screen callbacks that name `key.id` become:

```kotlin
        onCoatClick = { pick -> store.dispatch(EncounterDetailIntent.CoatPicked(pick.catId, pick.coat)) },
        onTakePhotoClick = { catId -> store.dispatch(EncounterDetailIntent.TakePhotoClicked(catId)) },
        onPickPhotoClick = { catId -> store.dispatch(EncounterDetailIntent.PickPhotoClicked(catId)) },
        onPhotoClick = { tap -> store.dispatch(EncounterDetailIntent.PhotoClicked(tap.catId, tap.photoId)) },
        onCoordinatesClick = { catId -> store.dispatch(EncounterDetailIntent.CoordinatesClicked(catId)) },
        onSetLocationClick = { catId -> store.dispatch(EncounterDetailIntent.SetLocationClicked(catId)) },
```

`key.id` stays only in `koinViewModel<EncounterDetailStore> { parametersOf(key.id) }`. The camera and picker launchers already dispatch the cat their result names (P2), so they stay as they are.

No test can fail on this step in P3a-1: the page's id equals `key.id` while the window holds the observed cat alone. The guard is the `key.id` grep of Step 6, also named in Task 0. P3b's entry tests (the viewer and the map open on the cat swiped to) are the first to prove it.

- [ ] **Step 6: Run the app's tests to green**

Run: `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && ./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.detail.*' --tests 'dev.catsradar.app.navigation.*' --tests 'dev.catsradar.app.architecture.*' --rerun --console=plain 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL, so every module compiles.
- In `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/build/test-results/testDebugUnitTest/` the XML shows `EncounterDetailPagesTest` `tests="2"`, and `EncounterDetailScreenTest` 16, `EncounterDetailCoatPickerTest` 3 and `DetailPhotoPagerTest` 5 as before.
- `grep -L 'failures="0"' "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/build/test-results/testDebugUnitTest/"*.xml` prints nothing.
- `EncounterDetailEntryTest` (including L2's *set on map opens the picker for this cat above the detail, once however often it is tapped*), `EncounterDetailMapLinkTest` and `PhotoViewerEntryTest` pass unchanged.
- `grep -n 'key.id' "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8/app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt"` prints only the `parametersOf` line.

- [ ] **Step 7: Commit**

```bash
cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && git add ui/src app/src && git commit -m "Every tap on the detail screen names the cat on screen"
```

- [ ] **Step 8: Prove the screen tests can fail**

Break one thing at a time and run `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && ./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.detail.EncounterDetailPagesTest' --rerun --console=plain 2>&1 | tail -15`. Record the failing test from the XML, then restore with `git -C "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" checkout -- ui/src/main/kotlin/dev/catsradar/ui/detail/EncounterDetailScreen.kt`:

| # | Mutation | Must fail |
|---|----------|-----------|
| M4 | `val page = state.pages.first()` | *the screen draws the cat on screen, and its taps name that cat* |
| M5 | in the six wrappers, `page.id` → `state.pages.first().id` (the page drawn stays right) | both tests of `EncounterDetailPagesTest` |

---

### Task 3: `encounter-detail.md`

**Files:** `docs/features/encounter-detail.md`, `docs/tbd/decompositions/2026-09-25-outing-pager.md`. Every anchor below is quoted verbatim from the current file.

- [ ] **Step 1: The state in pages**

After the paragraph that ends
> (`EncounterDetailStateMapperTest`, *the day comes from the encounter's own offset*).

add the paragraph:

```markdown
The state holds each cat as a page — a `CatPage` in `Loaded.pages`, beside the id of the cat on screen and
its position — and the mapper builds one page for each cat of the outing window it is handed, with each
page's own place and attempt (`EncounterDetailStateMapperTest`, *a window maps to one page per cat, newest
first, and names the cat on screen and its position*; *each page takes its own cat's place and attempt*).
The Store hands it a window of the observed cat alone, so the screen has one page
(`EncounterDetailStoreTest`, *the observed cat is the one page, and it is on screen*).
```

- [ ] **Step 2: Taps name their page's cat**

In the paragraph that begins
> A tap on the photo, on the coordinates or on *Set on map* only ever acts on the cat the screen is

after its last words
> the screen does not show opens nothing*).

add: "The screen names that cat in every tap: each button, the photo and the map send the id of the page they sit on (`EncounterDetailPagesTest`, *the screen draws the cat on screen, and its taps name that cat*; *a tap on the map of the cat on screen names that cat*)."

- [ ] **Step 3: Where the code lives**

- The line
  > - `presentation/…/detail/` — `EncounterDetailState`, `Intent`, `Effect`, `StateMapper`, `Store`

  becomes `` - `presentation/…/detail/` — `EncounterDetailState` (a `CatPage` per cat), `Intent`, `Effect`, `StateMapper`, `Store` ``.
- The line
  > - `ui/…/detail/EncounterDetailScreen.kt`, `WhereCard.kt`, `DetailPhotoPager.kt`, `AddPhotoCard.kt`;

  becomes `` - `ui/…/detail/EncounterDetailScreen.kt`, `WhereCard.kt`, `DetailPhotoPager.kt`, `AddPhotoCard.kt`, `DetailInteractions.kt` (the two-value taps' payloads); ``.

- [ ] **Step 4: The map row's size**

In `docs/tbd/decompositions/2026-09-25-outing-pager.md`, in the row that begins
> | P3a-1 | The detail state holds pages |

the size `~450` becomes `~630`, the planned estimate (see *Size*).

- [ ] **Step 5: Commit**

```bash
cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && git add docs/features/encounter-detail.md docs/tbd/decompositions/2026-09-25-outing-pager.md && git commit -m "Docs: the detail state holds its cat as a page"
```

---

### Task 4: Check, review, gate, PR

- [ ] Run `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" && ./gradlew check --rerun-tasks --console=plain 2>&1 | tail -40` through `ctx_execute` until it is green. If detekt or lint fails, read its report and fix the code; never add a suppression.
- [ ] Measure the size: `git -C "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/firebase-analytics-crashlytics-5db5f8" diff --numstat origin/main...HEAD -- . ':(exclude)*.md' | awk '{a+=$1; d+=$2} END {print a+d}'`. Record the number in the PR.
- [ ] Push `tech/detail-state-in-pages`. On an HTTP/2 framing error, retry with `git -c http.version=HTTP/1.1 push`, then verify with `git ls-remote`.
- [ ] Open a draft PR, "The detail state holds pages", with the repo's `/create-pr` skill if there is one, else `gh pr create --draft`. The description carries the criteria and the evidence: XML counts per class, and the mutation table with the test each mutation failed.
- [ ] Set the map row P3a-1 → `in-review`, and commit that.
- [ ] Run `/code-review` on the PR. Fix findings in new commits; never force-push after a review.
- [ ] Run `acceptance:acceptance-gate` against `outing-pager-p3a1.md`.
- [ ] Mark the PR ready. Merging is the owner's call. Once merged: P3a-1 → `merged` with a decision-log line, and this plan moves to `docs/superpowers/plans/archive/`.

## Size

Reviewable lines as `git diff --numstat` counts them — added plus removed, so a changed line counts twice; `*.md` excluded — estimated from this plan against e180faf3:

| Task | What | ~Lines |
|------|------|-------:|
| 1 | State (~40), mapper (~30), Store (~25) | 95 |
| 1 | Screen read side: the arm, `CatPageContent`, samples (~75); `WhereCard` (~40) | 115 |
| 1 | Mapper tests: 22 call lines + 2 new tests (~95); Store tests: helper, ~19 field reads, 1 new test (~60); pick-several (~15) | 170 |
| 1 | `DetailStates.kt` (7) and the three screen tests' fixtures (~50) | 57 |
| 2 | Interactions (7), callback types and wrappers (~30), destination (~12), two test arguments (4) | 53 |
| 2 | `EncounterDetailPagesTest` | 120 |
| **Total** | | **~630** |

It sits over the 600 target, for one reason. About 150 of those lines migrate main's existing tests and fixtures to the new `Loaded` (mechanical rewrites of lines that exist today), and they cannot leave the slice: every commit must compile, and no test or screen can read the old `Loaded` once it is gone. The slice's own code and new tests come to about 480. The map row's estimate, now ~450, becomes ~630 in Task 3 Step 4. Well under the 1,000 cap, nothing moves out. If the measured diff passes 1,000, stop and report rather than split.

## New questions

1. **Where the "which cat" guards read from.** `emitIfShownAndOffered` keeps `catId == encounterId`, and `requestPhoto` reads `page(encounterId)` — the observed cat — rather than the tapped cat's page. That keeps P2's test *the camera and the picker open for the cat they were asked for* (a tap naming `OTHER` while `ID` is observed) passing unchanged. *Recommend:* keep it so. P3a-2 drops `catId == encounterId` and reads `page(catId)` everywhere, as the ruled "tap-guard widening".
2. **A shared test helper in `:app`.** `DetailStates.kt` (`loadedWith(page)`) is new, shared by three screen test classes (`EncounterDetailScreenTest`, `EncounterDetailCoatPickerTest`, `DetailPhotoPagerTest`). *Recommend:* keep it in `app/src/test/…/detail/` (not `testing/`): it is only about the detail screen, and P3b adds its multi-page builder beside it.
