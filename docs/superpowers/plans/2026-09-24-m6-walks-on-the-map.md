# M6 — Walks on the map, and cats per km — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a walk's recorded track on the map when its outing is focused, and show distance walked and cats per kilometre in Statistics next to cats per hour.

**Architecture:** A read model, `WalkTrack` (a walk with its points), is observed through a new `ObserveWalkTracks` use case over one new repository stream. `StatsCalculator` derives walked distance and cats per km from it as pure functions; `MapStateMapper` chooses the walked tracks over the cat-to-cat line for a focused outing. No schema change, no new permission.

**Tech Stack:** Kotlin Multiplatform (`:domain`, `:data`, `:presentation`), Room 3 (`androidx.room3`), Koin, Jetpack Compose + Material 3, maplibre-compose, kotlinx-coroutines, turbine.

**Spec:** `docs/superpowers/specs/2026-09-21-cats-radar-design.md` (§5 statistics definitions, §9 item 2.3) and the slice in `docs/tbd/decompositions/2026-09-23-map-epic.md` (M6).

## Global Constraints

- Rules in `docs/rules/` are binding: `module-structure.md`, `mvi-architecture.md`, `compose-patterns.md`, `compose-preview-patterns.md`, `date-time.md`, `code-commenting-standards.md`, `static-analysis.md`.
- `:domain` imports no `android.*`/`androidx.*`; `:presentation` imports no `androidx.compose.*` and no `dev.catsradar.data`; `:ui` imports no `dev.catsradar.data` (Konsist enforces).
- State is data: immutable collections (`kotlinx.collections.immutable`), no function types, strings already formatted. Mappers build state and are tested with `assertEquals` on the whole result where practical.
- No user-facing string in `:presentation`/`:ui` source: a presentation enum token in State, words in `ui/src/main/res/values/strings.xml` **and** `values-ru/strings.xml`. Russian uses «Котиков» for cats, as the existing statistics strings do.
- Time types: `kotlin.time.Instant`, `kotlin.time.Duration`; no `java.time` outside `androidMain`.
- Every constant lives in `domain/…/Tuning.kt`.
- Comments: default none; one line, two at most; only facts a reader could not get from the code (see `code-commenting-standards.md`). No suppressions without a same-line reason; no detekt baseline.
- Tests first. Robolectric only for Room/resources (`androidHostTest`).
- **Gradle:** Bash commands containing the word `gradle` are blocked by a hook. Run Gradle through the `mcp__plugin_context-mode_context-mode__ctx_execute` tool (`language: "shell"`, `cwd` = the worktree root, a timeout of at least `900000`), printing only a tail of the output. Test tasks: `:domain:testAndroidHostTest`, `:data:testAndroidHostTest`, `:presentation:testAndroidHostTest`, `:ui:testDebugUnitTest`, `:app:testDebugUnitTest`. `--rerun` applies only to the task written just before it. `./gradlew check --console=plain` must be green at the end of every task.
- Commits end with the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Rulings behind this plan

1. **The track reaches the map through M3's outing focus.** A focused outing draws the recorded tracks of the walks that overlap its time span instead of the cat-to-cat line; the cat-to-cat line stays when no overlapping walk has a track of at least two points. Cost if wrong: the route choice in `MapStateMapper`.
2. **A walk's whole track is drawn**, not clipped to the outing's first-to-last-cat window: the way to the first cat is part of the route walked. Cost if wrong: a clip in the mapper.
3. **A walk still on has no end yet**: it covers every moment from its start, so no clock is needed. Cost if wrong: one comparison in `WalkSpan.kt`.
4. **Distance walked** is the sum of every walk's track length. **Cats per km** pools cats and kilometres over walks at least `Tuning.MIN_RATE_DISTANCE_METERS` (500 m) long, counting live cats whose `occurredAt` falls within the walk; a walk with no cats counts and lowers the rate. This mirrors how the overall rate pools cats and time over eligible outings. Cost if wrong: `StatsCalculator` and one constant.
5. **Display:** whole metres below one kilometre, else kilometres with one decimal; cats per km with one decimal as "x / km"; kilometres only. The two rows are left out when nothing walked has any length, so a user without location permission never reads "0 m". Cost if wrong: the mapper and five strings.
6. **A focused outing's view fits around its track as well as its cats.** Cost if wrong: `areaAround`'s input.
7. **One line style** for both kinds of route; `map.md` says which one is drawn. Cost if wrong: a second `LineLayer`.
8. **Every point is read into memory**, as the spec's in-memory statistics already are. Cost if wrong: a per-walk query later.

## File structure

| File | Responsibility |
|---|---|
| `domain/…/model/Walk.kt` | + `WalkTrack` |
| `domain/…/walk/WalkSpan.kt` (new) | `Walk.covers(at)`, `Walk.overlaps(from, to)` |
| `domain/…/repository/WalkRepository.kt` | + `observeEveryPoint()` |
| `domain/…/usecase/ObserveWalkTracks.kt` (new) | walks joined with their points |
| `data/…/db/TrackPointDao.kt`, `data/…/repository/WalkRepositoryImpl.kt` | the stream behind `observeEveryPoint()` |
| `domain/…/Tuning.kt`, `domain/…/stats/Stats.kt`, `domain/…/stats/StatsCalculator.kt`, `domain/…/usecase/ObserveStats.kt` | distance walked, cats per km |
| `presentation/…/statistics/StatisticsState.kt`, `StatisticsStateMapper.kt`; `ui/…/statistics/StatisticsScreen.kt`; strings | the two Statistics rows |
| `presentation/…/map/MapState.kt`, `MapStateMapper.kt`, `MapStore.kt`; `ui/…/map/MapFeatures.kt`, `MapScreen.kt` | the track on a focused map |
| `app/…/di/DomainModule.kt` | `ObserveWalkTracks`, `ObserveStats` wiring |
| `docs/features/*.md`, spec §5, map epic | docs |

---

### Task 1: Walk tracks as a read model

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/model/Walk.kt`
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/walk/WalkSpan.kt`
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/repository/WalkRepository.kt`
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/ObserveWalkTracks.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/db/TrackPointDao.kt`
- Modify: `data/src/commonMain/kotlin/dev/catsradar/data/repository/WalkRepositoryImpl.kt`
- Modify every `WalkRepository` / `TrackPointDao` test double so the build compiles:
  `domain/src/commonTest/…/testing/FakeWalkRepository.kt`,
  `presentation/src/commonTest/…/counter/CounterStoreTestDoubles.kt` (`FakeWalkRepository`),
  `app/src/test/…/notification/WalkTestDoubles.kt` (`OneWalkRepository`),
  `app/src/test/…/notification/WalkingNotificationSyncTest.kt` (`OpenWalkOnly`),
  `data/src/commonTest/…/repository/FakeWalkDao.kt` (`FakeTrackPointDao`).
- Test: `domain/src/commonTest/kotlin/dev/catsradar/domain/walk/WalkSpanTest.kt` (new),
  `domain/src/commonTest/kotlin/dev/catsradar/domain/usecase/ObserveWalkTracksTest.kt` (new),
  `data/src/androidHostTest/kotlin/dev/catsradar/data/db/TrackPointDaoTest.kt`,
  `data/src/commonTest/kotlin/dev/catsradar/data/repository/WalkRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `Walk`, `TrackPoint`, `WalkRepository.observeAll()` (newest start first).
- Produces:
  - `data class WalkTrack(val walk: Walk, val points: List<TrackPoint>)` in `dev.catsradar.domain.model`
  - `fun Walk.covers(at: Instant): Boolean` and `fun Walk.overlaps(from: Instant, to: Instant): Boolean` in `dev.catsradar.domain.walk`
  - `WalkRepository.observeEveryPoint(): Flow<List<TrackPoint>>`
  - `class ObserveWalkTracks(walkRepository: WalkRepository)` with `operator fun invoke(): Flow<List<WalkTrack>>`, walks newest start first, each walk's points in route order.

- [ ] **Step 1: Write the failing tests for the span functions**

`WalkSpanTest.kt`:

```kotlin
package dev.catsradar.domain.walk

import dev.catsradar.domain.model.Walk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class WalkSpanTest {

    private fun walk(endedAt: Instant? = END) = Walk("w", START, endedAt, "device", START, endedAt ?: START)

    @Test
    fun `a walk covers its start, its end and every moment between`() {
        assertTrue(walk().covers(START))
        assertTrue(walk().covers(START + 30.minutes))
        assertTrue(walk().covers(END))
    }

    @Test
    fun `a walk covers nothing before its start or after its end`() {
        assertFalse(walk().covers(START - 1.milliseconds))
        assertFalse(walk().covers(END + 1.milliseconds))
    }

    @Test
    fun `a walk still on covers every moment from its start`() {
        assertTrue(walk(endedAt = null).covers(START + 30.days))
        assertFalse(walk(endedAt = null).covers(START - 1.milliseconds))
    }

    @Test
    fun `a span touching either end of a walk overlaps it`() {
        assertTrue(walk().overlaps(START - 1.hours, START))
        assertTrue(walk().overlaps(END, END + 1.hours))
        assertTrue(walk().overlaps(START - 1.hours, END + 1.hours))
    }

    @Test
    fun `a span wholly before or after a walk does not overlap it`() {
        assertFalse(walk().overlaps(START - 2.hours, START - 1.milliseconds))
        assertFalse(walk().overlaps(END + 1.milliseconds, END + 1.hours))
    }

    @Test
    fun `a walk still on overlaps any span that ends after its start`() {
        assertTrue(walk(endedAt = null).overlaps(START + 10.days, START + 11.days))
        assertFalse(walk(endedAt = null).overlaps(START - 2.hours, START - 1.milliseconds))
    }

    private companion object {
        val START = Instant.parse("2026-09-24T10:00:00Z")
        val END = Instant.parse("2026-09-24T11:00:00Z")
    }
}
```

- [ ] **Step 2: Run it and see it fail to compile**

Run (via `ctx_execute`): `./gradlew :domain:testAndroidHostTest --tests 'dev.catsradar.domain.walk.WalkSpanTest' --console=plain 2>&1 | tail -30`
Expected: compilation failure, `covers`/`overlaps` unresolved.

- [ ] **Step 3: Add `WalkTrack` and the span functions**

Append to `Walk.kt`:

```kotlin
/** A walk with its route, [points] in the order they were recorded. */
data class WalkTrack(val walk: Walk, val points: List<TrackPoint>)
```

`WalkSpan.kt`:

```kotlin
package dev.catsradar.domain.walk

import dev.catsradar.domain.model.Walk
import kotlin.time.Instant

/** Whether [at] falls within this walk, both ends included; a walk still on has no end yet. */
fun Walk.covers(at: Instant): Boolean = at >= startedAt && endedAt.let { it == null || at <= it }

/** Whether this walk and the span [from]..[to] share a moment, ends included; a walk still on has no end yet. */
fun Walk.overlaps(from: Instant, to: Instant): Boolean = startedAt <= to && endedAt.let { it == null || it >= from }
```

- [ ] **Step 4: Run `WalkSpanTest`; expect PASS.**

- [ ] **Step 5: Write the failing use-case test**

`ObserveWalkTracksTest.kt` uses the domain `FakeWalkRepository` (`dev.catsradar.domain.testing`) and `runTest` with `first()` or turbine, as `ObserveOpenWalkTest` does. Cases:

```kotlin
@Test
fun `each walk carries only its own points, in route order, newest walk first`() = runTest {
    val repository = FakeWalkRepository()
    repository.upsert(walk("morning", START))
    repository.upsert(walk("evening", START + 8.hours))
    repository.appendPoints(
        listOf(point("evening", minute = 1), point("morning", minute = 2), point("morning", minute = 1)),
    )

    val tracks = ObserveWalkTracks(repository)().first()

    assertEquals(listOf("evening", "morning"), tracks.map { it.walk.id })
    assertEquals(listOf(point("evening", 1)), tracks[0].points)
    assertEquals(listOf(point("morning", 1), point("morning", 2)), tracks[1].points)
}

@Test
fun `a walk with no recorded point has an empty route`() = runTest { /* one walk, no points → points == emptyList() */ }

@Test
fun `a new point reaches the walk's route`() = runTest { /* turbine: first emission empty route; appendPoint; next emission has it */ }
```

with helpers `walk(id, startedAt)` (ended an hour later) and `point(walkId, minute)` = `TrackPoint(walkId, START + minute.minutes, 41.39, 2.17, 5f)`. Write the two bodies above in full in the test file; the comments here only name what they assert.

- [ ] **Step 6: Run it and see it fail** (`ObserveWalkTracks`, `observeEveryPoint` unresolved).

- [ ] **Step 7: Add the stream and the use case**

`WalkRepository.kt`, after `loadEveryPoint()`:

```kotlin
    /** Every point of every walk, each walk's in route order, again whenever one is added. */
    fun observeEveryPoint(): Flow<List<TrackPoint>>
```

`TrackPointDao.kt`:

```kotlin
    @Query("SELECT * FROM track_points ORDER BY walkId, at, rowId")
    fun observeEvery(): Flow<List<TrackPointEntity>>
```

`WalkRepositoryImpl.kt`:

```kotlin
    override fun observeEveryPoint(): Flow<List<TrackPoint>> =
        points.observeEvery().map { every -> every.map { it.toDomain() } }
```

`ObserveWalkTracks.kt`:

```kotlin
package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Every walk, newest start first, each with its route; again whenever a walk or a point changes. */
class ObserveWalkTracks(private val walkRepository: WalkRepository) {
    operator fun invoke(): Flow<List<WalkTrack>> =
        combine(walkRepository.observeAll(), walkRepository.observeEveryPoint()) { walks, points ->
            val routes = points.groupBy { it.walkId }
            walks.map { walk -> WalkTrack(walk, routes[walk.id].orEmpty()) }
        }
}
```

Test doubles: the domain `FakeWalkRepository` returns its stored points sorted by `walkId`, then `at`:
`override fun observeEveryPoint(): Flow<List<TrackPoint>> = points.map { all -> all.sortedWith(compareBy({ it.walkId }, { it.at })) }`.
Every other double returns its stored points if it keeps any, else `flowOf(emptyList())`. **Each must emit at least once**: `combine` over a stream that never emits never emits, and later tasks combine this stream into statistics the Counter tests read. `FakeTrackPointDao` gets `observeEvery()` the same way.

- [ ] **Step 8: Add the data-layer tests**

`TrackPointDaoTest.kt`, in its existing style:

```kotlin
    @Test
    fun everyPointComesBackGroupedByWalkInTimeOrderAndAgainAfterAnInsert() = runTest {
        walks.upsert(walkEntity("b"))
        walks.upsert(walkEntity("a"))
        dao.insert(trackPointEntity("b", second = 10, lat = 41.3))
        dao.insert(trackPointEntity("a", second = 20, lat = 41.2))
        dao.insert(trackPointEntity("a", second = 10, lat = 41.1))

        assertEquals(listOf(41.1, 41.2, 41.3), dao.observeEvery().first().map { it.lat })

        dao.insert(trackPointEntity("b", second = 20, lat = 41.4))

        assertEquals(listOf(41.1, 41.2, 41.3, 41.4), dao.observeEvery().first().map { it.lat })
    }
```

(Use whatever `walkEntity` helper signature `WalkEntityFixtures.kt` offers; a second open walk may be refused by `startIfNoneOpen`, so store the walks with `upsert` or with ended entities.)

`WalkRepositoryImplTest.kt`: one test that a point stored in `FakeTrackPointDao` comes out of `observeEveryPoint()` as the equal domain `TrackPoint`.

- [ ] **Step 9: Run** `:domain:testAndroidHostTest --rerun :data:testAndroidHostTest --rerun`, then `./gradlew check --console=plain`. Expected: green.

- [ ] **Step 10: Commit**

```bash
git add -A domain data presentation app
git commit -m "Walk tracks as a read model: every walk with its route"
```

---

### Task 2: Distance walked and cats per km, in the statistics

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/Tuning.kt`
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/stats/Stats.kt`
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/stats/StatsCalculator.kt`
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/usecase/ObserveStats.kt`
- Modify: `app/src/main/kotlin/dev/catsradar/app/di/DomainModule.kt`
- Modify the `ObserveStats(...)` call sites in tests: `app/src/test/…/notification/WalkingActionReceiverTest.kt:59`, `app/src/test/…/notification/WalkingNotificationSyncTest.kt:38`, `presentation/src/commonTest/…/counter/CounterStoreFixture.kt:59`
- Test: `domain/src/commonTest/kotlin/dev/catsradar/domain/stats/StatsCalculatorTest.kt`

**Interfaces:**
- Consumes (Task 1): `WalkTrack`, `Walk.covers(at)`, `ObserveWalkTracks`, and existing `trackLengthMeters(points)` in `dev.catsradar.domain.geo`.
- Produces:
  - `Tuning.MIN_RATE_DISTANCE_METERS: Double = 500.0`
  - `Stats.walkedMeters: Double` (default `0.0`) and `Stats.catsPerKm: Double?` (default `null`), the last two properties of `Stats`
  - `StatsCalculator.calculate(encounters, today, now, walks: List<WalkTrack> = emptyList(), gap, minRateDuration, minRateDistanceMeters = Tuning.MIN_RATE_DISTANCE_METERS)`
  - `ObserveStats(encounterRepository, observeWalkTracks: ObserveWalkTracks, clock, timeZone, ticks)`

- [ ] **Step 1: Write the failing calculator tests**

In `StatsCalculatorTest.kt`, add helpers and tests. A track of `km` kilometres runs due north from Barcelona; its expected length is read back from `trackLengthMeters`, so no test depends on the Earth's radius:

```kotlin
    private fun walk(id: String, start: Instant, end: Instant? = start + 1.hours) =
        Walk(id, start, end, "device", start, end ?: start)

    // One degree of latitude is about 111.195 km on the mean Earth radius the distance uses.
    private fun track(walk: Walk, km: Double) = WalkTrack(
        walk,
        listOf(
            TrackPoint(walk.id, walk.startedAt, 41.39, 2.17, 5f),
            TrackPoint(walk.id, walk.startedAt + 1.minutes, 41.39 + km / 111.195, 2.17, 5f),
        ),
    )

    private fun walked(encounters: List<Encounter>, vararg walks: WalkTrack) =
        StatsCalculator.calculate(encounters, today = TODAY, now = NOW, walks = walks.toList())

    @Test
    fun `with no walk, nothing is walked and cats per km is unmeasured`() {
        val stats = stats(listOf(at(NOON)))

        assertEquals(0.0, stats.walkedMeters)
        assertNull(stats.catsPerKm)
    }

    @Test
    fun `distance walked adds up every walk's route, a short one and one still on included`() {
        val long = track(walk("long", NOON - 3.hours), km = 2.0)
        val short = track(walk("short", NOON - 1.hours), km = 0.1)
        val open = track(walk("open", NOON, end = null), km = 1.0)

        val stats = walked(emptyList(), long, short, open)

        val expected = listOf(long, short, open).sumOf { trackLengthMeters(it.points) }
        assertEquals(expected, stats.walkedMeters, absoluteTolerance = 1e-6)
    }

    @Test
    fun `cats per km pools cats and distance across walks rather than averaging them`() {
        val first = track(walk("first", NOON - 3.hours), km = 1.0)
        val second = track(walk("second", NOON - 1.hours), km = 3.0)
        val cats = listOf(
            at(NOON - 170.minutes, "a"), at(NOON - 160.minutes, "b"), at(NOON - 150.minutes, "c"),
            at(NOON - 30.minutes, "d"),
        )

        val stats = walked(cats, first, second)

        val km = (trackLengthMeters(first.points) + trackLengthMeters(second.points)) / 1000
        assertEquals(4 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }

    @Test
    fun `a walk shorter than the minimum adds distance but is left out of cats per km`() {
        val short = track(walk("short", NOON - 1.hours), km = 0.3)

        val stats = walked(listOf(at(NOON - 30.minutes)), short)

        assertTrue(stats.walkedMeters > 0.0)
        assertNull(stats.catsPerKm)
    }

    @Test
    fun `a walk with no cats lowers cats per km`() {
        val busy = track(walk("busy", NOON - 3.hours), km = 1.0)
        val quiet = track(walk("quiet", NOON - 1.hours), km = 1.0)
        val cats = listOf(at(NOON - 170.minutes, "a"), at(NOON - 160.minutes, "b"))

        val stats = walked(cats, busy, quiet)

        val km = (trackLengthMeters(busy.points) + trackLengthMeters(quiet.points)) / 1000
        assertEquals(2 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }

    @Test
    fun `only live cats inside a walk count, its first and last moments included`() {
        val route = track(walk("w", NOON - 1.hours, end = NOON), km = 1.0)
        val cats = listOf(
            at(NOON - 1.hours, "at start"),
            at(NOON, "at end"),
            at(NOON - 61.minutes, "before"),
            at(NOON + 1.minutes, "after"),
            at(NOON - 30.minutes, "deleted").copy(deletedAt = NOW),
        )

        val stats = walked(cats, route)

        val km = trackLengthMeters(route.points) / 1000
        assertEquals(2 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }

    @Test
    fun `a walk still on counts every cat since it started`() {
        val open = track(walk("open", NOON - 1.hours, end = null), km = 1.0)

        val stats = walked(listOf(at(NOON - 30.minutes, "a"), at(NOW, "b")), open)

        val km = trackLengthMeters(open.points) / 1000
        assertEquals(2 / km, assertNotNull(stats.catsPerKm), absoluteTolerance = 1e-9)
    }
```

- [ ] **Step 2: Run** `:domain:testAndroidHostTest --tests 'dev.catsradar.domain.stats.StatsCalculatorTest'`; expect compile failure.

- [ ] **Step 3: Implement**

`Tuning.kt`, beside the other track constants:

```kotlin
    /** A walk shorter than this is left out of cats per km, as an outing too short is left out of the rate. */
    const val MIN_RATE_DISTANCE_METERS: Double = 500.0
```

`Stats.kt`, as the last two properties:

```kotlin
    /** The length of every walk's recorded route, in metres. */
    val walkedMeters: Double = 0.0,
    /** Null when no walk was long enough to measure — see [Tuning.MIN_RATE_DISTANCE_METERS]. */
    val catsPerKm: Double? = null,
```

(import `dev.catsradar.domain.Tuning` for the KDoc link.)

`StatsCalculator.kt`: add the `walks` and `minRateDistanceMeters` parameters and:

```kotlin
            walkedMeters = walks.sumOf { trackLengthMeters(it.points) },
            catsPerKm = catsPerKm(live, walks, minRateDistanceMeters),
```

```kotlin
    // Pooled like the overall rate: one short lucky walk must not outweigh a long ordinary one.
    private fun catsPerKm(live: List<Encounter>, walks: List<WalkTrack>, minDistanceMeters: Double): Double? {
        val measured = walks
            .map { it to trackLengthMeters(it.points) }
            .filter { (_, meters) -> meters >= minDistanceMeters }
        if (measured.isEmpty()) return null
        val cats = measured.sumOf { (track, _) -> live.count { track.walk.covers(it.occurredAt) } }
        val kilometres = measured.sumOf { (_, meters) -> meters } / METERS_PER_KM
        return cats / kilometres
    }

    private const val METERS_PER_KM = 1000.0
```

`ObserveStats.kt`:

```kotlin
class ObserveStats(
    private val encounterRepository: EncounterRepository,
    private val observeWalkTracks: ObserveWalkTracks,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    // (keep the existing comment on ticks)
    private val ticks: Flow<Unit> = ticker(TickPeriod),
) {
    operator fun invoke(): Flow<Stats> =
        combine(encounterRepository.observeAll(), observeWalkTracks(), ticks) { encounters, walks, _ ->
            StatsCalculator.calculate(encounters, today = clock.today(timeZone), now = clock.now(), walks = walks)
        }
}
```

`DomainModule.kt`: `factoryOf(::ObserveWalkTracks)` and
`factory { ObserveStats(encounterRepository = get(), observeWalkTracks = get(), clock = get(), timeZone = get()) }`.

Test call sites pass `ObserveWalkTracks(<that test's walk repository double>)`; `CounterStoreFixture` already has a `walkRepository` parameter.

- [ ] **Step 4: Run** `:domain:testAndroidHostTest --rerun :presentation:testAndroidHostTest --rerun :app:testDebugUnitTest --rerun`, then `./gradlew check --console=plain`. Expected: green.

- [ ] **Step 5: Commit** — `git commit -m "Statistics: distance walked and cats per km"`

---

### Task 3: Distance and cats per km on the Statistics screen

**Files:**
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/statistics/StatisticsState.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/statistics/StatisticsStateMapper.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/statistics/StatisticsScreen.kt`
- Modify: `ui/src/main/res/values/strings.xml`, `ui/src/main/res/values-ru/strings.xml`
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/statistics/StatisticsStateMapperTest.kt`

**Interfaces:**
- Consumes (Task 2): `Stats.walkedMeters`, `Stats.catsPerKm`.
- Produces:
  - `StatisticsState.walked: WalkedState? = null`
  - `data class WalkedState(val distance: DistanceState, val catsPerKm: String?)`
  - `data class DistanceState(val value: String, val unit: DistanceUnit)`, `enum class DistanceUnit { METERS, KILOMETERS }`

- [ ] **Step 1: Write the failing mapper tests**

Give the test's `stats(...)` helper `walkedMeters: Double = 0.0` and `catsPerKm: Double? = null` parameters passed through to `Stats`. Then:

```kotlin
    @Test
    fun `with nothing walked the walk rows are left out`() {
        assertNull(mapper.map(stats(total = 3, walkedMeters = 0.0)).walked)
    }

    @Test
    fun `under a kilometre the distance reads in whole metres`() {
        val state = mapper.map(stats(total = 1, walkedMeters = 350.4))

        assertEquals(WalkedState(DistanceState("350", DistanceUnit.METERS), catsPerKm = null), state.walked)
    }

    @Test
    fun `a distance that rounds to a thousand metres reads as a kilometre`() {
        assertEquals(DistanceState("999", DistanceUnit.METERS), mapper.map(stats(total = 1, walkedMeters = 999.4)).walked?.distance)
        assertEquals(DistanceState("1.0", DistanceUnit.KILOMETERS), mapper.map(stats(total = 1, walkedMeters = 999.6)).walked?.distance)
    }

    @Test
    fun `from a kilometre the distance reads in kilometres to one decimal`() {
        assertEquals(DistanceState("12.4", DistanceUnit.KILOMETERS), mapper.map(stats(total = 1, walkedMeters = 12_449.0)).walked?.distance)
    }

    @Test
    fun `cats per km reads to one decimal, and stays unmeasured without a long enough walk`() {
        assertEquals("3.3", mapper.map(stats(total = 1, walkedMeters = 4_000.0, catsPerKm = 3.26)).walked?.catsPerKm)
        assertNull(mapper.map(stats(total = 1, walkedMeters = 300.0, catsPerKm = null)).walked?.catsPerKm)
    }
```

(`kotlin.math.round` rounds ties to even: keep test values off exact halves.) The metres and kilometres cases are the token test the rules ask for: each unit asserted, and they cannot collapse onto one token.

- [ ] **Step 2: Run** `:presentation:testAndroidHostTest --tests '*StatisticsStateMapperTest'`; expect compile failure.

- [ ] **Step 3: Implement**

`StatisticsState.kt`: add after `overallRate`

```kotlin
    /** Null when no walk has a recorded route, so the screen leaves the walk rows out. */
    val walked: WalkedState? = null,
```

and

```kotlin
/** [catsPerKm] already carries one decimal; null when no walk was long enough to measure. */
data class WalkedState(val distance: DistanceState, val catsPerKm: String?)

/** [value] is the number alone; [unit] says which unit the screen labels it with. */
data class DistanceState(val value: String, val unit: DistanceUnit)

enum class DistanceUnit { METERS, KILOMETERS }
```

`StatisticsStateMapper.kt`: in `map`

```kotlin
        walked = stats.walkedMeters.takeIf { it > 0.0 }?.let { meters ->
            WalkedState(distance = meters.toDistanceState(), catsPerKm = stats.catsPerKm?.oneDecimal())
        },
```

and beside `toRateState`

```kotlin
private const val METERS_PER_KM = 1000

private fun Double.toDistanceState(): DistanceState {
    val wholeMeters = round(this).toLong()
    return if (wholeMeters < METERS_PER_KM) {
        DistanceState(value = wholeMeters.toString(), unit = DistanceUnit.METERS)
    } else {
        DistanceState(value = (this / METERS_PER_KM).oneDecimal(), unit = DistanceUnit.KILOMETERS)
    }
}
```

Strings, after `statistics_rate_unavailable`:

| name | EN | RU |
|---|---|---|
| `statistics_walked` | `Walked` | `Пройдено` |
| `statistics_cats_per_km` | `Cats per km` | `Котиков на км` |
| `statistics_distance_meters` | `%1$s m` | `%1$s м` |
| `statistics_distance_kilometers` | `%1$s km` | `%1$s км` |
| `statistics_rate_per_km` | `%1$s / km` | `%1$s / км` |

`StatisticsScreen.kt`: in the Outings `SectionCard`, right after the overall-rate row:

```kotlin
            state.walked?.let { walked ->
                StatRow(R.string.statistics_walked, walked.distance.label())
                StatRow(R.string.statistics_cats_per_km, walked.catsPerKmLabel())
            }
```

with private composables next to `RateState?.label()`:

```kotlin
@Composable
private fun DistanceState.label(): String = when (unit) {
    DistanceUnit.METERS -> stringResource(R.string.statistics_distance_meters, value)
    DistanceUnit.KILOMETERS -> stringResource(R.string.statistics_distance_kilometers, value)
}

@Composable
private fun WalkedState.catsPerKmLabel(): String =
    catsPerKm?.let { stringResource(R.string.statistics_rate_per_km, it) }
        ?: stringResource(R.string.statistics_rate_unavailable)
```

Add `walked = WalkedState(DistanceState("42.7", DistanceUnit.KILOMETERS), catsPerKm = "3.1")` to the preview's sample state.

- [ ] **Step 4: Run** `:presentation:testAndroidHostTest --rerun`, then `./gradlew check --console=plain` (Lint checks the RU strings). Expected: green.

- [ ] **Step 5: Commit** — `git commit -m "Statistics screen: distance walked and cats per km"`

---

### Task 4: A focused outing draws the route actually walked

**Files:**
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/map/MapState.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/map/MapStateMapper.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/map/MapStore.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/map/MapFeatures.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/map/MapScreen.kt` (the `routeLine` call at line 102)
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/map/MapStateMapperTest.kt`,
  `presentation/src/commonTest/kotlin/dev/catsradar/presentation/map/MapStoreTest.kt`,
  `ui/src/test/kotlin/dev/catsradar/ui/map/MapFeaturesTest.kt`

**Interfaces:**
- Consumes (Task 1): `WalkTrack`, `Walk.overlaps(from, to)`, `ObserveWalkTracks` (bound in Koin by Task 2).
- Produces:
  - `data class MapPosition(val latitude: Double, val longitude: Double)`
  - `data class MapLine(val positions: ImmutableList<MapPosition>)`
  - `MapFocus(outingId: String, label: String, lines: ImmutableList<MapLine>)` — replaces `route: ImmutableList<MapPoint>`
  - `MapStateMapper.map(encounters, today, choices = MapChoices(), walks: List<WalkTrack> = emptyList())`
  - `MapStore(observeEncounters, observeWalkTracks: ObserveWalkTracks, stateMapper, clock, timeZone)`
  - `internal fun routeLines(lines: ImmutableList<MapLine>): FeatureCollection<LineString, JsonObject>?` — replaces `routeLine`

- [ ] **Step 1: Write the failing mapper tests**

Update the existing focus tests to the new shape: where they build `route = …` from `MapPoint`s, the expected focus becomes `lines = persistentListOf(MapLine(<those points as MapPosition, in time order>))`; the coat-filter test asserts the line's positions rather than ids. Add, with a helper `walkTrack(id, start, end, vararg positions: Pair<Double, Double>)` building `WalkTrack` with points a minute apart:

```kotlin
    @Test
    fun `a focused outing on a recorded walk draws the walk's track instead of joining its cats`() {
        val first = located("first", 41.39, 2.17).copy(occurredAt = BASE)
        val second = located("second", 41.40, 2.18).copy(occurredAt = BASE + 10.minutes)
        val walk = walkTrack("w", start = BASE - 5.minutes, end = BASE + 20.minutes, 41.388 to 2.168, 41.395 to 2.175, 41.401 to 2.181)

        val state = assertIs<MapState.Located>(mapper.map(listOf(first, second), TODAY, MapChoices(focus = "first"), listOf(walk)))

        assertEquals(
            persistentListOf(MapLine(persistentListOf(MapPosition(41.388, 2.168), MapPosition(41.395, 2.175), MapPosition(41.401, 2.181)))),
            state.focus?.lines,
        )
    }

    @Test
    fun `every walk the outing overlaps is drawn, oldest first, and one outside it is not`() { /* walks before, across the start, across the end, after → lines of the middle two, in start order */ }

    @Test
    fun `a walk whose track has fewer than two points leaves the line joining the cats`() { /* one-point track → lines == the cat-to-cat line */ }

    @Test
    fun `with no focus, walks draw nothing`() { /* focus = null, a walk given → focus == null and area unchanged */ }

    @Test
    fun `a focused outing's view takes in its track as well as its cats`() { /* a track point north of every cat → area.north >= that point's latitude */ }
```

Write each commented body in full in the test file; the comment names what it asserts.

- [ ] **Step 2: Run** `:presentation:testAndroidHostTest --tests '*MapStateMapperTest'`; expect compile failure.

- [ ] **Step 3: Implement the state and the mapper**

`MapState.kt`:

```kotlin
/** An outing shown alone, with the [lines] drawn for it — see [MapStateMapper] for which route that is. */
data class MapFocus(val outingId: String, val label: String, val lines: ImmutableList<MapLine>)

data class MapLine(val positions: ImmutableList<MapPosition>)

data class MapPosition(val latitude: Double, val longitude: Double)
```

`MapStateMapper.kt`: `map` takes `walks: List<WalkTrack> = emptyList()` as its last parameter; when focused:

```kotlin
        val lines = outing?.let { routeOf(it, located, walks) }
        ...
            area = areaAround(located.map { MapPosition(it.latitude, it.longitude) } + lines.orEmpty().flatMap { it.positions }),
            focus = outing?.let { MapFocus(outingId = it.first().id, label = headerLabel(it, today), lines = checkNotNull(lines)) },
```

```kotlin
    // The walks' own tracks are the route actually walked; the line from cat to cat stands in without one.
    private fun routeOf(outing: List<Encounter>, located: List<MapPoint>, walks: List<WalkTrack>): ImmutableList<MapLine> {
        val from = outing.minOf { it.occurredAt }
        val to = outing.maxOf { it.occurredAt }
        val tracks = walks
            .filter { it.walk.overlaps(from, to) && it.points.size >= 2 }
            .sortedBy { it.walk.startedAt }
            .map { track -> track.points.map { MapPosition(it.lat, it.lon) } }
        val lines = tracks.ifEmpty { listOf(located.map { MapPosition(it.latitude, it.longitude) }) }
        return lines.map { MapLine(it.toImmutableList()) }.toImmutableList()
    }
```

`areaAround` takes `List<MapPosition>` instead of `List<MapPoint>`; its body reads `latitude`/`longitude` as before.

`MapStore.kt`: constructor gains `observeWalkTracks: ObserveWalkTracks` after `observeEncounters`;

```kotlin
        combine(observeEncounters(), observeWalkTracks(), choices) { encounters, walks, chosen ->
            val mapped = stateMapper.map(encounters, clock.today(timeZone), chosen, walks)
            ...
```

Koin: `viewModelOf(::MapStore)` resolves the new parameter from `factoryOf(::ObserveWalkTracks)` (Task 2); nothing else changes. `MapStoreTest.newStore()` passes `ObserveWalkTracks(FakeWalkRepository())` (the presentation test double in `…/counter/CounterStoreTestDoubles.kt`, `internal`, visible module-wide).

- [ ] **Step 4: Update the UI**

`MapFeatures.kt`, replacing `routeLine`:

```kotlin
/** One line per entry of [lines] with at least two positions, or null when none has. */
internal fun routeLines(lines: ImmutableList<MapLine>): FeatureCollection<LineString, JsonObject>? {
    val drawable = lines.filter { it.positions.size >= 2 }
    if (drawable.isEmpty()) return null
    return FeatureCollection(
        drawable.map { line -> Feature(LineString(line.positions.map { Position(it.longitude, it.latitude) }), buildJsonObject {}) },
    )
}
```

`MapScreen.kt` line 102: `val route = remember(state.focus) { state.focus?.let { routeLines(it.lines) } }`.

`MapFeaturesTest.kt`: replace the two `routeLine` tests with: two lines become two features whose coordinates are longitude-first in order; a line of one position is dropped; all lines too short gives null.

- [ ] **Step 5: Add a store test**: focusing an outing while its walk has a track gives `focus.lines` from the track (seed the fake walk repository with an ended walk and two points).

- [ ] **Step 6: Run** `:presentation:testAndroidHostTest --rerun :ui:testDebugUnitTest --rerun :app:testDebugUnitTest --rerun`, then `./gradlew check --console=plain`. Expected: green.

- [ ] **Step 7: Commit** — `git commit -m "Map: a focused outing draws the route actually walked"`

---

### Task 5: Docs

**Files:**
- Modify: `docs/features/map.md` — *An outing's route*: the line is the recorded track of every walk the outing overlaps, drawn whole, oldest first; the cat-to-cat line stands in when no such walk has a track of two points or more; the view fits the track too; the coat filter never thins it. Replace "It is not the route actually walked, which is the walk tracks' job." Update *Where the code lives* (`MapLine`, `MapPosition`, `routeLines`) and *Not built yet* (no walk tracks slice left; nothing shows a walk with no cats).
- Modify: `docs/features/statistics.md` — a section *Walks: distance and cats per km* stating rulings 4 and 5 (sum of every walk; pooled over walks of at least `Tuning.MIN_RATE_DISTANCE_METERS`; a walk with no cats lowers it; a walk still on counts from its start; metres below a kilometre; rows left out when nothing was walked; "—" when unmeasured). *Where the code lives* gains `WalkSpan.kt`, `ObserveWalkTracks.kt`.
- Modify: `docs/features/walking-mode.md` — *Recording the route*: one sentence that the route appears on the map with its outing and in Statistics' distance; *Where the code lives*: `ObserveWalkTracks.kt`.
- Modify: `docs/superpowers/specs/2026-09-21-cats-radar-design.md` §5 table — two rows after *Overall rate*:
  - `Distance walked` — `Σ trackLength(w)` over every walk, the great-circle length of its route
  - `Cats per km` — `Σ cats(w) / Σ km(w)` over walks with `trackLength ≥ MIN_RATE_DISTANCE_METERS`, `cats(w)` = live encounters with `w.startedAt ≤ occurredAt ≤ (w.endedAt ?: ∞)`; "—" when none
  and add `MIN_RATE_DISTANCE_METERS` to the §8 constant list.
- Modify: `docs/tbd/decompositions/2026-09-23-map-epic.md` — M6 status `in-review`; a decision-log entry dated 2026-09-24 listing rulings 1–8 above in one line each with their cost if wrong.

- [ ] **Step 1:** Edit the five documents as listed, in the existing voice of each (present tense, behaviour first, no code narration).
- [ ] **Step 2:** `./gradlew check --console=plain` (docs do not affect it, but the branch must stay green).
- [ ] **Step 3: Commit** — `git commit -m "Docs: walks on the map, distance and cats per km"`
