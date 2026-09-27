# Slice P3a-2 — One Store serves the outing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `EncounterDetailStore` reads every live cat, builds its pages from the opened cat's outing through
`outingWindow`, and keeps them and the cat on screen in `OutingPages`; attaching is tracked per cat, the tap guards
accept any cat on the pages, and a cat deleted elsewhere hands the screen to its neighbour. The screen still draws
one page.

**Architecture:** A new `OutingPages` (`:presentation/detail`) holds the anchor and the cats on the pages, starts
from the restored or the opened cat, hands the anchor over when its cat leaves, and hands out `ShownPages` — the
window and the cat on screen as one value, so the two can never disagree. A new `PhotoAttempts` holds each cat's
photo attempt. The Store collects `ObserveEncounters`, feeds `OutingPages`, observes each page's place, and renders
the last `ShownPages` it received. `EncounterDetailStore(openedId, restoredId, …)`; the destination passes
`restoredId = null` until P3b saves the anchor.

**Tech Stack:** Kotlin Multiplatform, kotlinx-coroutines Flow, Koin, turbine, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-25-outing-pager-design.md` § The Store, § Intents and effects name their
cat, § Deleting (*a cat deleted elsewhere*). Map: `docs/tbd/decompositions/2026-09-25-outing-pager.md` row P3a-2 and
*Slice P3a-2*. Criteria: `outing-pager-p3a2.md` in the acceptance directory (Task 0).

## Global Constraints

- **The screen still draws one page**, the cat on screen. The visible change is that a cat deleted elsewhere hands
  the screen to its neighbour instead of *Missing*; the pager, "2 / 5" and `PageSettled` are P3b's.
- **A delete from this screen behaves as today** (map: "until P4 a delete behaves as today"): the removed state
  with Undo, then the screen closes. It deletes the cat on screen, and Undo returns to that cat.
- **Out of scope:** `PageSettled`, `OutingEdgeReleased`, neighbours and jumps in the state, the undo bar, the
  destination saving the anchor (P3b, P4, P5a).
- **Base.** Branch `tech/detail-store-serves-outing` on `origin/main` f5011884 plus the docs commit 43afde7c. At the
  start of every task check `git branch --show-current`, `git rev-parse --short HEAD`, `git log origin/main..HEAD`.
- **MVI** (`docs/rules/mvi-architecture.md`): "**No business logic in the Store.**" — the outing boundary stays in
  `outingWindow`; `OutingPages` and `PhotoAttempts` hold per-screen state, like `ReportedRun`
  (`docs/rules/dependency-injection.md`: "a helper that holds one Store's per-screen state" is not a collaborator,
  so the Store builds both itself).
- **detekt** runs in `check` with no baseline and detekt 1.23.8 defaults: `TooManyFunctions` flags a class with 11
  functions, and `EncounterDetailStore` has 10 today — it keeps 10 (`reduce` becomes `pagesState`; the places
  helper is top-level). `ReturnCount` allows 2 per function.
- **Comments** (`docs/rules/code-commenting-standards.md`): "**Default: no comment.**" "**One line. Two at most.**"
- **Tests first.** Gradle runs through the context-mode `ctx_execute` tool (`language: "shell"`, `timeout` 900000),
  `cd` into the worktree first, output through `tail`. `--rerun` follows **each** test task. Counts come from the
  JUnit XML.
- **Every commit compiles every module**, main and test sources (merge commits keep each commit in main).
- **Keep every test main has.** Migrated, never deleted; the renames are listed in Task 2.
- **Docs in the same PR:** `docs/features/encounter-detail.md`, `docs/features/outings.md`, the map (Task 3).

## Decisions

1. **`settle` and `release` land in `OutingPages` now**, unit-tested, with no Store caller: the map puts
   `OutingPages` whole in this slice. `PageSettled` (P3b) and `OutingEdgeReleased` (P5a) only wire intents to them.
   `OutingDirection` goes beside the state, where the spec puts it for `jumpDirection`.
2. **The cat this screen deleted holds emissions back.** While it is not live, emissions do not reach
   `OutingPages`, so the anchor stays on it and Undo shows it again; the removed state stays as today. Undo renders
   the pages at once from the last `ShownPages`, because an emission equal to the one before the delete is dropped
   by `distinctUntilChanged`.
3. **`ShownPages` carries the cat on screen with its window.** `flatMapLatest` can deliver an older window after
   `OutingPages` already moved on; rendering the pair it was built with keeps `currentId` on the pages. The mapper
   now `require`s that (the P3a-1 gate's note: the screen would throw where the mapper returned position 0).
4. **The presentation fake's `observeAll` mirrors the DAO** (`WHERE deletedAt IS NULL`) and honours `observeDelay`,
   which the attach-arrival tests need now that the Store reads `observeAll`.
5. **Each page's place** comes from one `ObserveEncounterPlace` flow per cat, combined. An outing is a handful of
   cats; the Store already re-subscribed its one place flow on every emission.

---

### Task 0: Freeze the acceptance criteria

Controller runs `acceptance:acceptance-criteria` (auto) into `outing-pager-p3a2.md` before any code, naming the
tests below by class and name, the mutations M1–M8 of Task 4, the kept test counts of the migrated classes, the
Store's function count, the docs statements of Task 3, every commit compiling, and `check` green.

---

### Task 1: `OutingPages`

**Files:**
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/OutingPages.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailState.kt` (add
  `OutingDirection`)
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/OutingPagesTest.kt`

**Interfaces:**
- Consumes: `OutingWindow`, `outingWindow(encounters, shown)` from `dev.catsradar.domain.session` (P1).
- Produces:
  ```kotlin
  internal data class ShownPages(val window: OutingWindow, val currentId: String)
  internal class OutingPages(openedId: String, restoredId: String?) {
      val shown: ShownPages?
      fun update(live: List<Encounter>): ShownPages?
      fun settle(catId: String)
      fun release(direction: OutingDirection): ShownPages?
  }
  internal fun List<Encounter>.holdsLive(id: String): Boolean
  enum class OutingDirection { NEWER, OLDER }
  ```

- [ ] **Step 1: Write the failing tests** — `OutingPagesTest.kt`:

```kotlin
package dev.catsradar.presentation.detail

import dev.catsradar.presentation.encounters.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OutingPagesTest {

    private val e1 = cat("e1", 0.minutes)
    private val e2 = cat("e2", 10.minutes)
    private val m1 = cat("m1", 3.hours)
    private val m2 = cat("m2", 3.hours + 10.minutes)
    private val m3 = cat("m3", 3.hours + 20.minutes)
    private val m4 = cat("m4", 3.hours + 30.minutes)
    private val l1 = cat("l1", 6.hours)
    private val l2 = cat("l2", 6.hours + 10.minutes)
    private val all = listOf(e1, e2, m1, m2, m3, m4, l1, l2)

    @Test
    fun `it starts on the restored cat while that cat is live`() {
        val pages = OutingPages(openedId = "m1", restoredId = "m4")

        assertEquals("m4", pages.update(all)?.currentId)
    }

    @Test
    fun `it starts on the opened cat when the restored one is deleted`() {
        val pages = OutingPages(openedId = "m1", restoredId = "m4")

        val shown = pages.update(all.map { if (it.id == "m4") it.copy(deletedAt = START) else it })

        assertEquals("m1", shown?.currentId)
    }

    @Test
    fun `with neither cat live there are no pages until the opened one is`() {
        val pages = OutingPages(openedId = "m1", restoredId = null)

        assertNull(pages.update(all - m1))
        assertEquals("m1", pages.update(all)?.currentId)
    }

    @Test
    fun `the pages are the outing of the cat on screen, newest first`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)

        assertEquals(listOf(m4, m3, m2, m1), pages.update(all)?.window?.cats)
    }

    @Test
    fun `a cat logged into the outing joins the pages, and the cat on screen stays`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)
        val logged = cat("m5", 3.hours + 40.minutes)

        val shown = pages.update(all + logged)

        assertEquals(listOf(logged, m4, m3, m2, m1), shown?.window?.cats)
        assertEquals("m2", shown?.currentId)
    }

    @Test
    fun `an outing a delete splits in two stays whole on the pages`() {
        val glued = listOf(cat("a", 0.minutes), cat("b", 25.minutes), cat("c", 50.minutes))
        val pages = OutingPages(openedId = "a", restoredId = null)
        pages.update(glued)

        val shown = pages.update(glued.filterNot { it.id == "b" })

        assertEquals(listOf("c", "a"), shown?.window?.cats?.map { it.id })
    }

    @Test
    fun `the cat on screen deleted hands the screen to the next older page`() {
        val pages = OutingPages(openedId = "m3", restoredId = null)
        pages.update(all)

        assertEquals("m2", pages.update(all - m3)?.currentId)
    }

    @Test
    fun `the oldest page deleted hands the screen to the newer one`() {
        val pages = OutingPages(openedId = "m1", restoredId = null)
        pages.update(all)

        assertEquals("m2", pages.update(all - m1)?.currentId)
    }

    @Test
    fun `the cat on screen deleted with its older neighbour hands the screen to the next older page left`() {
        val pages = OutingPages(openedId = "m3", restoredId = null)
        pages.update(all)

        assertEquals("m1", pages.update(all - m3 - m2)?.currentId)
    }

    @Test
    fun `with every page deleted there are none, and a cat brought back rejoins them`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        assertNull(pages.update(listOf(e1, e2, l1, l2)))
        val shown = pages.update(listOf(e1, e2, m3, l1, l2))

        assertEquals(listOf(m3), shown?.window?.cats)
        assertEquals("m3", shown?.currentId)
    }

    @Test
    fun `settling moves the screen to a cat on the pages and ignores any other`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        pages.settle("m4")
        pages.settle("l1")

        assertEquals("m4", pages.shown?.currentId)
    }

    @Test
    fun `releasing towards the older outing moves the pages there, onto its newest cat`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        val shown = pages.release(OutingDirection.OLDER)

        assertEquals(listOf(e2, e1), shown?.window?.cats)
        assertEquals("e2", shown?.currentId)
    }

    @Test
    fun `releasing towards the newer outing moves the pages there, onto its oldest cat`() {
        val pages = OutingPages(openedId = "m2", restoredId = null)
        pages.update(all)

        val shown = pages.release(OutingDirection.NEWER)

        assertEquals(listOf(l2, l1), shown?.window?.cats)
        assertEquals("l1", shown?.currentId)
    }

    @Test
    fun `releasing past the newest outing changes nothing`() {
        val pages = OutingPages(openedId = "l1", restoredId = null)
        val before = pages.update(all)

        assertNull(pages.release(OutingDirection.NEWER))
        assertEquals(before, pages.shown)
    }

    private fun cat(id: String, at: Duration) = encounterFixture(id, START + at)

    private companion object {
        val START = Instant.parse("2026-09-22T06:00:00Z")
    }
}
```

- [ ] **Step 2: Run to see it fail** — `./gradlew :presentation:testAndroidHostTest --tests '*OutingPagesTest' --rerun`;
  expected: compilation fails, `OutingPages` unresolved.

- [ ] **Step 3: Implement** — add to `EncounterDetailState.kt`, after `Missing`'s sealed interface:

```kotlin
enum class OutingDirection { NEWER, OLDER }
```

`OutingPages.kt`:

```kotlin
package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.session.OutingWindow
import dev.catsradar.domain.session.outingWindow

/** [currentId] is one of [window]'s cats. */
internal data class ShownPages(val window: OutingWindow, val currentId: String)

/** The cats a detail screen pages through and the one on screen; a cat leaves the pages only by being deleted. */
internal class OutingPages(private val openedId: String, private val restoredId: String?) {
    private var encounters: List<Encounter> = emptyList()

    // Newest first.
    private var pageIds: List<String> = emptyList()
    private var anchor: String? = null

    /** Null before a live cat to start from is seen, and while no cat on the pages is live. */
    var shown: ShownPages? = null
        private set

    fun update(live: List<Encounter>): ShownPages? {
        encounters = live
        if (anchor == null) {
            anchor = listOfNotNull(restoredId, openedId).firstOrNull { live.holdsLive(it) }
            pageIds = listOfNotNull(anchor)
        }
        shown = outingWindow(live, pageIds.toSet())?.let { window ->
            val nextIds = window.cats.map { it.id }
            val kept = handOver(checkNotNull(anchor), nextIds)
            anchor = kept
            pageIds = nextIds
            ShownPages(window, kept)
        }
        return shown
    }

    fun settle(catId: String) {
        val current = shown
        if (current != null && catId in pageIds) {
            anchor = catId
            shown = current.copy(currentId = catId)
        }
    }

    /** The pages moved to the outing next to them, on its cat nearest to them; null when there is none. */
    fun release(direction: OutingDirection): ShownPages? {
        val window = shown?.window
        val (outing, landing) = when (direction) {
            OutingDirection.NEWER -> window?.newer to window?.newerLanding
            OutingDirection.OLDER -> window?.older to window?.olderLanding
        }
        if (outing == null || landing == null) return null
        anchor = landing.id
        pageIds = outing.map { it.id }
        return update(encounters)
    }

    /** [current], or when its cat has left the nearest page still there: the next older, else the next newer. */
    private fun handOver(current: String, nextIds: List<String>): String {
        if (current in nextIds) return current
        val at = pageIds.indexOf(current)
        return (pageIds.drop(at + 1) + pageIds.take(at).asReversed()).first { it in nextIds }
    }
}

internal fun List<Encounter>.holdsLive(id: String): Boolean = any { it.id == id && it.deletedAt == null }
```

`handOver` always finds a page: the window holds at least one live cat of `pageIds`, and `current` is not among
the next ones.

- [ ] **Step 4: Run to see it pass** — the same command; 14 tests in the XML, 0 failures.

- [ ] **Step 5: Commit** — `presentation` main and test sources compile (the Store does not use `OutingPages`
  yet): `feat: OutingPages keeps the cats a detail screen pages through`.

---

### Task 2: The Store serves the outing

**Files:**
- Create: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/PhotoAttempts.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStore.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/detail/EncounterDetailStateMapper.kt`
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/counter/CounterStoreTestDoubles.kt`
- Modify: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/detail/EncounterDetailStoreTest.kt` (both
  classes), `EncounterDetailPickSeveralTest.kt`, `EncounterDetailStateMapperTest.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/di/PresentationModule.kt`,
  `app/src/main/kotlin/dev/catsradar/app/navigation/EncounterDetailDestination.kt`,
  `app/src/test/kotlin/dev/catsradar/app/di/KoinRuntimeResolutionTest.kt`

The constructor changes, so Koin, the destination and every `newStore()` move in this one commit.

**Interfaces:**
- Consumes: Task 1's `OutingPages`, `ShownPages`, `holdsLive`; `ObserveEncounters()` (`Flow<List<Encounter>>`).
- Produces: `EncounterDetailStore(openedId: String, restoredId: String?, observeEncounters: ObserveEncounters,
  observeEncounterPlace: ObserveEncounterPlace, deleteEncounter, undoDelete, setCoat, attachPhoto, stateMapper,
  clock, timeZone)`; Koin `viewModel { (openedId: String, restoredId: String?) -> … }`.

- [ ] **Step 1: The fake mirrors the DAO.** In `CounterStoreTestDoubles.kt`'s `FakeEncounterRepository`:

```kotlin
    /** Delays every emission of an observe call but that call's first, so a `.first()` snapshot stays instant. */
    var observeDelay: Duration = Duration.ZERO

    fun encounters(): List<Encounter> = encounters.value

    // Mirrors the DAO's deletedAt IS NULL filter.
    override fun observeAll(): Flow<List<Encounter>> =
        encounters.map { list -> list.filter { it.deletedAt == null } }.delayedAfterFirst()

    override fun observeById(id: String): Flow<Encounter?> =
        encounters.map { list -> list.firstOrNull { it.id == id && it.deletedAt == null } }.delayedAfterFirst()

    private fun <T> Flow<T>.delayedAfterFirst(): Flow<T> {
        var firstEmission = true
        return onEach { if (firstEmission) firstEmission = false else delay(observeDelay) }
    }
```

Run `./gradlew :presentation:testAndroidHostTest --rerun` before touching the Store: every class still green (no
other test relies on seeing a deleted row), so later failures belong to the Store.

- [ ] **Step 2: Write the failing Store tests.** In `EncounterDetailStoreTest.kt`, both classes' `newStore` become
  `newStore(restoredId: String? = null)` building `EncounterDetailStore(openedId = ID, restoredId = restoredId,
  observeEncounters = ObserveEncounters(repository), observeEncounterPlace = ObserveEncounterPlace(cells), …)` (the
  rest unchanged); likewise `EncounterDetailPickSeveralTest`'s. Companions gain `const val THIRD = "cat-3"` where
  used. `namedCell` takes `countryCode = "ES"`, `countryName = "Spain"`, `locality = "Barcelona"` as defaults.

  Class `EncounterDetailStoreTest` — *the observed cat is the one page, and it is on screen* is rewritten as:

```kotlin
    @Test
    fun `the pages are the opened cat's outing, newest first, with the opened cat on screen`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
            repository.insert(encounterFixture(THIRD, OCCURRED - 1.days))
            val store = newStore()
            runCurrent()

            val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
            assertEquals(listOf(OTHER, ID), state.pages.map { it.id })
            assertEquals(ID, state.currentId)
            assertEquals(2, state.currentNumber)
        }
```

  and these are added:

```kotlin
    @Test
    fun `the screen starts on the restored cat while it is live`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore(restoredId = OTHER)
        runCurrent()

        assertEquals(OTHER, store.shownPage().id)
    }

    @Test
    fun `a restored cat that is gone starts the screen on the opened one`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes, deletedAt = NOW))
        val store = newStore(restoredId = OTHER)
        runCurrent()

        assertEquals(ID, store.shownPage().id)
    }

    @Test
    fun `a cat logged into the outing joins the pages and the screen stays on its cat`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()

        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(OTHER, ID), state.pages.map { it.id })
        assertEquals(ID, state.currentId)
    }

    @Test
    fun `a cat deleted elsewhere hands the screen to the next older cat`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(THIRD, OCCURRED - 10.minutes))
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        repository.softDelete(ID, NOW)
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(OTHER, THIRD), state.pages.map { it.id })
        assertEquals(THIRD, state.currentId)
    }

    @Test
    fun `the oldest cat deleted elsewhere hands the screen to the newer one`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        repository.softDelete(ID, NOW)
        runCurrent()

        assertEquals(OTHER, store.shownPage().id)
    }

    @Test
    fun `a cat deleted elsewhere and brought back returns the screen to it`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        val store = newStore()
        runCurrent()
        repository.softDelete(ID, NOW)
        runCurrent()
        assertEquals(EncounterDetailState.Missing, store.state.value)

        repository.undoDelete(ID)
        runCurrent()

        assertEquals(ID, store.shownPage().id)
    }

    @Test
    fun `delete removes the cat on screen, and undo shows it again`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore(restoredId = OTHER)
        runCurrent()

        store.dispatch(EncounterDetailIntent.DeleteClicked)
        runCurrent()
        assertEquals(listOf(OTHER), repository.softDeletedIds)
        assertEquals(EncounterDetailState.Deleted(undoVisible = true), store.state.value)

        store.dispatch(EncounterDetailIntent.UndoClicked)
        runCurrent()
        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(OTHER, ID), state.pages.map { it.id })
        assertEquals(OTHER, state.currentId)
    }

    @Test
    fun `each page shows its own cat's place`() = runTest(mainDispatcher) {
        repository.insert(
            encounterFixture(ID, OCCURRED, locationSource = LocationSource.CURRENT_FIX)
                .copy(lat = 41.39, lon = 2.17, placeCellId = "sp3e3q"),
        )
        repository.insert(
            encounterFixture(OTHER, OCCURRED + 10.minutes, locationSource = LocationSource.CURRENT_FIX)
                .copy(lat = 38.72, lon = -9.14, placeCellId = "eycs0p"),
        )
        cells.upsert(namedCell("sp3e3q"))
        cells.upsert(namedCell("eycs0p", countryCode = "PT", countryName = "Portugal", locality = "Lisbon"))
        val store = newStore()
        runCurrent()

        val state = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf("Lisbon", "Barcelona"), state.pages.map { it.place?.title })
    }
```

  Class `EncounterDetailStorePhotoTest`:
  - rename *a photo lands on the cat its result names, not on the one the screen observes* → *…, not on the cat on
    screen* (body unchanged);
  - *a tap naming a cat the screen does not show opens neither the viewer nor the map* becomes *a tap naming a cat
    not on the pages opens neither the viewer nor the map*: the second cat is
    `encounterFixture(OTHER, OCCURRED - 1.days, locationSource = LocationSource.CURRENT_FIX).copy(lat = 41.39,
    lon = 2.17).withPhoto(photoPath = "cat-2.jpg")`, and the taps are `PhotoClicked(OTHER, OTHER)` and
    `CoordinatesClicked(OTHER)` — each would open something were `OTHER` on the pages;
  - *set on map names the cat it was tapped for, and a cat the screen does not show opens nothing* becomes *…, and a
    cat not on the pages opens nothing*, with `OTHER` at `OCCURRED - 1.days`;
  - added:

```kotlin
    @Test
    fun `a tap on another page's photo, coordinates or set on map opens it for that cat`() =
        runTest(mainDispatcher) {
            repository.insert(encounterFixture(ID, OCCURRED))
            repository.insert(
                encounterFixture(OTHER, OCCURRED + 10.minutes, locationSource = LocationSource.CURRENT_FIX)
                    .copy(lat = 41.39, lon = 2.17).withPhoto(photoPath = "cat-2.jpg"),
            )
            repository.insert(encounterFixture(THIRD, OCCURRED + 20.minutes))
            val store = newStore()
            runCurrent()

            store.effects.test {
                store.dispatch(EncounterDetailIntent.PhotoClicked(OTHER, OTHER))
                runCurrent()
                assertEquals(EncounterDetailEffect.OpenPhoto(OTHER, OTHER), awaitItem())
                store.dispatch(EncounterDetailIntent.CoordinatesClicked(OTHER))
                runCurrent()
                assertEquals(EncounterDetailEffect.OpenMap(OTHER), awaitItem())
                store.dispatch(EncounterDetailIntent.SetLocationClicked(THIRD))
                runCurrent()
                assertEquals(EncounterDetailEffect.OpenLocationPicker(THIRD), awaitItem())
            }
        }

    @Test
    fun `one camera or picker at a time across the pages`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        val store = newStore()
        runCurrent()

        store.effects.test {
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(ID))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(OTHER))
            store.dispatch(EncounterDetailIntent.TakePhotoClicked(OTHER))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenCamera(ID), awaitItem())
            expectNoEvents()

            store.dispatch(EncounterDetailIntent.PhotoTaken(ID, null))
            store.dispatch(EncounterDetailIntent.PickPhotoClicked(OTHER))
            runCurrent()
            assertEquals(EncounterDetailEffect.OpenPhotoPicker(OTHER), awaitItem())
        }
    }

    @Test
    fun `a photo attached to another page shows the attempt on that page alone`() = runTest(mainDispatcher) {
        repository.insert(encounterFixture(ID, OCCURRED))
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotosPicked(OTHER, listOf(PICKED)))
        runCurrent()
        val attaching = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(AddPhoto.ATTACHING, AddPhoto.READY), attaching.pages.map { it.addPhoto })

        advanceTimeBy(2.seconds)
        runCurrent()
        val landed = assertIs<EncounterDetailState.Loaded>(store.state.value)
        assertEquals(listOf(AddPhoto.READY, AddPhoto.READY), landed.pages.map { it.addPhoto })
        assertEquals(listOf(1, 0), landed.pages.map { it.photos.size })
    }
```

  `EncounterDetailPickSeveralTest`: rename *a pick lands every photo on the cat it names, not on the one the screen
  observes* → *…, not on the cat on screen*; add:

```kotlin
    @Test
    fun `a pick carries on with its count when its cat comes on screen`() = runTest(mainDispatcher) {
        repository.insert(catWithPhotosOf())
        repository.insert(encounterFixture(OTHER, OCCURRED + 10.minutes))
        resizer.storeDelay = 1.seconds
        val store = newStore()
        runCurrent()

        store.dispatch(EncounterDetailIntent.PhotosPicked(OTHER, listOf(FIRST, SECOND)))
        runCurrent()
        repository.softDelete(ID, NOW)
        runCurrent()
        val page = shownPage(store)
        assertEquals(OTHER, page.id)
        assertEquals(AttachProgress(done = 0, total = 2), page.attachProgress)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(listOf(FIRST, SECOND), photoSources(OTHER))
        assertEquals(AddPhoto.READY, shownPage(store).addPhoto)
    }
```

  `EncounterDetailStateMapperTest`, added (with the class's own fixtures for one cat and `today`):

```kotlin
    @Test
    fun `a cat on screen that is not on the pages is refused`() {
        assertFailsWith<IllegalArgumentException> {
            mapper.map(OutingWindow(listOf(cat), newer = null, older = null), currentId = "elsewhere", today = TODAY)
        }
    }
```

  Run the detail classes: compilation fails (`openedId` unknown).

- [ ] **Step 3: `PhotoAttempts.kt`**

```kotlin
package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.Encounter

/** Each cat's photo attempt: it runs while its photos are attached and lasts until the cat carries them. */
internal class PhotoAttempts {
    private class Attempt(var progress: AttachProgress) {
        var running = true

        // Attached but not emitted yet: until they arrive the cat's page shows the attempt, not a bare offer.
        val arriving = mutableSetOf<String>()
    }

    private val attempts = mutableMapOf<String, Attempt>()
    private var carried: Map<String, Set<String>> = emptyMap()

    /** Each cat attaching, or waiting for a photo it attached, with how far its attempt got. */
    val progress: Map<String, AttachProgress> get() = attempts.mapValues { it.value.progress }

    fun running(catId: String, progress: AttachProgress) {
        val attempt = attempts.getOrPut(catId) { Attempt(progress) }
        attempt.progress = progress
        attempt.running = true
    }

    fun attached(catId: String, photoId: String) {
        val carries = carried[catId] ?: return
        if (photoId !in carries) attempts[catId]?.arriving?.add(photoId)
    }

    fun finished(catId: String, progress: AttachProgress) {
        attempts[catId]?.let { attempt ->
            attempt.progress = progress
            attempt.running = false
        }
        prune()
    }

    /** Settles every attempt against [cats], the cats on the pages; a cat not among them waits for nothing. */
    fun arrived(cats: List<Encounter>) {
        carried = cats.associate { cat -> cat.id to cat.photos.mapTo(mutableSetOf()) { it.id } }
        attempts.forEach { (catId, attempt) ->
            val carries = carried[catId]
            if (carries == null) attempt.arriving.clear() else attempt.arriving -= carries
        }
        prune()
    }

    private fun prune() {
        attempts.values.removeAll { !it.running && it.arriving.isEmpty() }
    }
}
```

- [ ] **Step 4: The Store.** `EncounterDetailStore.kt` becomes:

```kotlin
@Suppress("LongParameterList") // one parameter per collaborator; a holder type would exist only to lower the count
class EncounterDetailStore(
    openedId: String,
    restoredId: String?,
    observeEncounters: ObserveEncounters,
    observeEncounterPlace: ObserveEncounterPlace,
    private val deleteEncounter: DeleteEncounter,
    private val undoDelete: UndoDelete,
    private val setCoat: SetCoat,
    private val attachPhoto: AttachPhoto,
    private val stateMapper: EncounterDetailStateMapper,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : Store<EncounterDetailState, EncounterDetailIntent, EncounterDetailEffect>(EncounterDetailState.Loading) {

    private val pages = OutingPages(openedId, restoredId)
    private val attempts = PhotoAttempts()
    private var shown: ShownPages? = null
    private var places: Map<String, EncounterPlace?> = emptyMap()

    // While the cat deleted here is gone, an emission without it is that delete taking effect.
    private var deletedId: String? = null
    private var undoTimeoutJob: Job? = null
    private var awaitingPhoto = false
    private var leaving = false

    init {
        observeEncounters()
            .filter { live -> deletedId.let { it == null || live.holdsLive(it) } }
            .map { live -> pages.update(live) }
            .distinctUntilChanged()
            .flatMapLatest { next -> observeEncounterPlace.placesOn(next?.window).map { found -> next to found } }
            .onEach { (next, found) ->
                shown = next
                places = found
                attempts.arrived(next?.window?.cats.orEmpty())
                setState { pagesState() }
            }
            .launchIn(viewModelScope)
    }

    private fun pagesState(): EncounterDetailState = shown?.let { onScreen ->
        stateMapper.map(
            onScreen.window,
            currentId = onScreen.currentId,
            today = clock.today(timeZone),
            attaching = attempts.progress,
            places = places,
        )
    } ?: EncounterDetailState.Missing
```

  `handle` keeps its branches; `TakePhotoClicked`/`PickPhotoClicked` call `requestPhoto(intent.catId, opener)`;
  `PhotoClicked`, `CoordinatesClicked`, `SetLocationClicked` call `emitIfOffered` (renamed from
  `emitIfShownAndOffered`). The rest:

```kotlin
    private suspend fun emitIfOffered(catId: String, effect: EncounterDetailEffect, offered: CatPage.() -> Boolean) {
        if (state.value.page(catId)?.offered() == true) emit(effect)
    }

    private suspend fun requestPhoto(catId: String, opener: EncounterDetailEffect) {
        val offered = state.value.page(catId)?.addPhoto == AddPhoto.READY
        if (awaitingPhoto || !offered) return
        awaitingPhoto = true
        emit(opener)
    }

    private suspend fun onPhotosChosen(catId: String, uris: List<String>, source: PhotoSource) {
        awaitingPhoto = false
        if (uris.isEmpty()) return
        // Null for an attempt whose storage write failed.
        val outcomes = mutableListOf<AttachResult?>()
        for (uri in uris) {
            attempts.running(catId, AttachProgress(done = outcomes.size, total = uris.size))
            refresh()
            var result: AttachResult? = null
            runStorageWrite { result = attachPhoto(catId, uri, source) }
            (result as? AttachResult.Attached)?.let { attempts.attached(catId, it.photoId) }
            outcomes += result
        }
        attempts.finished(catId, AttachProgress(done = outcomes.size, total = uris.size))
        refresh()
        outcomes.message()?.let { emit(it) }
        if (source == PhotoSource.CAMERA) uris.forEach { emit(EncounterDetailEffect.DiscardCapture(it)) }
    }

    private fun refresh() {
        setState { if (this is EncounterDetailState.Loaded) pagesState() else this }
    }

    private suspend fun onDeleteClicked() {
        val onScreen = (state.value as? EncounterDetailState.Loaded)?.currentId
        // Set before the suspending call: a second tap in flight must see it and no-op.
        if (deletedId != null || onScreen == null) return
        deletedId = onScreen
        setState { EncounterDetailState.Deleted(undoVisible = true) }
        startUndoWindow()
        val restore = {
            undoTimeoutJob?.cancel()
            deletedId = null
            setState { pagesState() }
        }
        runStorageWrite(onFailure = restore) { deleteEncounter(onScreen) }
    }

    private suspend fun onUndoClicked() {
        val current = state.value
        val deleted = deletedId
        if (current !is EncounterDetailState.Deleted || !current.undoVisible || deleted == null) return
        undoTimeoutJob?.cancel()
        runStorageWrite(onFailure = { startUndoWindow() }) {
            undoDelete(deleted)
            deletedId = null
            setState { pagesState() }
        }
    }
```

  `startUndoWindow` and `navigateBack` are unchanged. Top level: `Encounter.photoIds()` goes (its job is in
  `PhotoAttempts`); `page(catId)` and `message()` stay; added:

```kotlin
private fun ObserveEncounterPlace.placesOn(window: OutingWindow?): Flow<Map<String, EncounterPlace?>> {
    val cats = window?.cats.orEmpty()
    if (cats.isEmpty()) return flowOf(emptyMap())
    return combine(cats.map { cat -> invoke(cat).map { place -> cat.id to place } }) { it.toMap() }
}
```

- [ ] **Step 5: The mapper enforces the cat on screen.** In `map`:

```kotlin
        val currentNumber = pages.indexOfFirst { it.id == currentId } + 1
        require(currentNumber > 0) { "The cat on screen, $currentId, is not on the pages" }
```

- [ ] **Step 6: Koin, the destination, the resolution test.**

```kotlin
    viewModel { (openedId: String, restoredId: String?) ->
        EncounterDetailStore(
            openedId = openedId,
            restoredId = restoredId,
            observeEncounters = get(),
            observeEncounterPlace = get(),
            …
```

  `EncounterDetailDestination`: `koinViewModel<EncounterDetailStore> { parametersOf(key.id, null) }`.
  `KoinRuntimeResolutionTest`: `parametersOf("any-id", null)` and a second line with `parametersOf("any-id",
  "restored-id")`.

- [ ] **Step 7: Run** `:presentation:testAndroidHostTest --rerun :app:testDebugUnitTest --rerun` and
  `:presentation:detekt :app:detekt`; all green, and the Store has 10 functions.

- [ ] **Step 8: Commit** — `feat: one detail Store serves the cat's whole outing`.

---

### Task 3: Docs

- [ ] `docs/features/encounter-detail.md`:
  - the pages paragraph (`:40-45`): the pages are the opened cat's outing, newest first, the screen draws the cat
    on screen, it starts on the restored cat while live else the opened one, a cat logged into the outing joins,
    cats leave only by deletion so a split outing stays whole, each page has its own place, the mapper refuses a
    cat on screen that is not on the pages — each with its test;
  - *Delete and undo*: Delete removes the cat on screen; the removed state shows even beside other cats of its
    outing, and Undo returns to that cat;
  - *Its photos*: taps act on any cat on the pages; one camera or picker for the screen at a time; the attempt is
    its cat's, shown on its page alone, carrying on whichever cat is on screen; "the observed cat" becomes "its
    cat";
  - *At the edges*: deleted elsewhere hands the screen to the next older cat, else the newer, *Missing* only with no
    page left, a cat brought back returns it; the own-delete flag reworded for several pages;
  - *Where the code lives*: `OutingPages.kt`, `PhotoAttempts.kt`, `ObserveEncounters.kt`;
  - *Not built yet*: swiping between the pages, their position, and moving to the next outing.
- [ ] `docs/features/outings.md`: the detail screen's pages among the consumers (`outingWindow`, in `OutingPages`),
  in the prose list and *Where the code lives*.
- [ ] The map: row P3a-2 `in-review` with the measured size when the PR opens; a decision-log line for this slice.
- [ ] Commit — `docs: the detail screen's pages are its cat's outing`.

---

### Task 4: Verification

- [ ] `CI=true ./gradlew check :app:assembleRelease` green (detached, rc file).
- [ ] Mutations, each committed state restored after (`git checkout -- <file>`), each failing its named test in the
  XML:
  - **M1** `handOver` tries newer before older → *the cat on screen deleted hands the screen to the next older page*
    and *a cat deleted elsewhere hands the screen to the next older cat*;
  - **M2** the start ignores `restoredId` → *it starts on the restored cat while that cat is live*, *the screen
    starts on the restored cat while it is live*;
  - **M3** `emitIfOffered` requires `catId == state.currentId` → *a tap on another page's photo, coordinates or set
    on map opens it for that cat*;
  - **M4** `PhotoAttempts.progress` reports the attempt for every page → *a photo attached to another page shows
    the attempt on that page alone*;
  - **M5** the Store's `filter` removed → *delete removes the cat on screen, and undo shows it again*;
  - **M6** the mapper's `require` removed → *a cat on screen that is not on the pages is refused*;
  - **M7** `placesOn` returns only the cat on screen's place → *each page shows its own cat's place*;
  - **M8** `OutingPages.update` builds the window around the cat on screen alone → *an outing a delete splits in
    two stays whole on the pages*, *a cat deleted elsewhere hands the screen to the next older cat*.
- [ ] `/code-review` on the PR; fix, re-run, re-report. Acceptance gate against `outing-pager-p3a2.md`.

## Size

Reviewable lines (`git diff --numstat`, `*.md` excluded), estimated: `OutingPages` 70, `PhotoAttempts` 50, Store
~110 changed, mapper 3, fake 12, Koin/destination/resolution 8 — about 250 of code; `OutingPagesTest` 170, Store
tests ~250 new plus ~40 migrated, pick-several 25, mapper 7 — about 490 of tests. **~740**, over the 600 target and
under the 1,000 cap; the map row says ~700. Nothing moves out: the Store, its helpers and the tests that pin them
cannot land apart without a commit that fails to compile or a Store without its tests.
