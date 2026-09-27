# Slice E2 — The count in a cookie, with its milestone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Counter's number sits in a twelve-sided cookie with an arc toward the next milestone; the goal and the outing ride tags on the ring (Task 7, the owner's amendment); the block stays the button with the same roll, badge and label.

**Architecture:** `:domain`'s `Milestone` gains `reached`, the rung the total has passed. `:presentation`'s `CounterState` gains `milestone: CounterMilestoneState?` (the Statistics labels plus the arc's fraction), built by `CounterStateMapper` from `Stats.nextMilestone`, which the Store already observes. In `:ui`, `TallyBlock` keeps its full-size clickable box and draws inside it a square holding the cookie, the arc, the number with a caption, and the "+N" badge; `CounterScreen` puts the milestone line first under the block. The theme gains the `*Emphasized` styles one weight step above its own.

**Tech Stack:** Kotlin Multiplatform, Compose Material 3 `MaterialShapes`, Robolectric + Compose UI test, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 1 (Type), § 2 The Counter (*The count*, *The milestone arc*, *The milestone line*, *Underneath*). Map: `docs/tbd/decompositions/2026-09-27-expressive-redesign.md` row E2. Criteria: `expressive-e2.md` in the acceptance directory (Task 0).

## Global Constraints

- **The block stays the button.** `counting-cats.md`: "The count sits in a large block that **is** the button. It squashes under a press and springs back … The squash is drawn only: what a press can land on stays the whole block." The clickable box keeps `FillOrScroll`'s fixed constraints; the cookie is drawn inside it.
- **Unchanged:** the roll (`RollingCount`), the badge's counting (`TapBurst`), the block's TalkBack label (`totalLabel`, or *Log a cat* before the total is read), the block's floor (`FillOrScroll(minFill = 120.dp)`), the Undo placement.
- **Milestones:** `Tuning.MILESTONES`; "The next milestone is the first rung **strictly above** the total" (`statistics.md`). `reached` is the last rung at or below the total, or 0.
- **Arc:** "fills from the rung already reached to the next one … at 62 cats, 50 is reached and 100 is next, so the arc stands at 24 %. The track is `onPrimaryContainer` at a low alpha, the arc `primary`. … Before the total is read and past the last rung there is no arc."
- **Line:** "Under the block, the Statistics line … It keeps its line while empty … so nothing under it moves." String `statistics_next_milestone` (EN "%1$s more to reach %2$s", RU "ещё %1$s до %2$s").
- **Colours from the scheme only** (`compose-patterns.md` §3: "a fade of a theme token is that token with its alpha changed").
- **State is data; mappers build state** (`mvi-architecture.md`); no mapping in composables (`compose-patterns.md` §2).
- **Strings:** EN and RU; the Russian voice is «котик» (`counter_outing_cats`, `statistics_total`).
- **Gradle** only through `ctx_execute` (`language: "shell"`, `timeout: 900000`), `cd` into `/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/imported-cats-notification-timeout-2638cd` first; `--rerun` follows each test task; counts from JUnit XML (`<module>/build/test-results/<task>/`).
- **Every commit compiles** every module it touches, main and tests.
- **Comments:** default none; one line at most for a non-derivable fact.
- **Docs in the same PR:** `counting-cats.md`, `statistics.md`, and the spec's corrections (Task 4).

---

### Task 0: Freeze the acceptance criteria

`acceptance:acceptance-criteria` (auto) into `expressive-e2.md`, naming: the domain, mapper, Store and screen tests of Tasks 1–3 by name, each with a mutation that fails it; the full suites; the docs statements; renders before and after (manual); every commit compiling.

### Task 1: The milestone knows the rung it has passed (`:domain`)

**Files:**
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/stats/Stats.kt` (`Milestone`)
- Modify: `domain/src/commonMain/kotlin/dev/catsradar/domain/stats/StatsCalculator.kt` (`nextMilestone`)
- Test: `domain/src/commonTest/kotlin/dev/catsradar/domain/stats/StatsCalculatorTest.kt`
- Modify (constructor): `presentation/src/commonTest/kotlin/dev/catsradar/presentation/statistics/StatisticsStateMapperTest.kt` lines 107 and 165

**Interfaces:**
- Produces: `data class Milestone(val value: Int, val remaining: Int, val reached: Int)`; `reached` has no default.

- [ ] **Step 1: Failing tests.** In `StatsCalculatorTest`, the existing `the next milestone is the first one above the total, with the distance to it` becomes:

```kotlin
assertEquals(Milestone(value = 1, remaining = 1, reached = 0), stats(emptyList()).nextMilestone)
assertEquals(Milestone(value = 10, remaining = 9, reached = 1), stats(listOf(at(NOON))).nextMilestone)
```

and two tests are added:

```kotlin
@Test
fun `the milestone knows the rung the total has already passed`() {
    val sixtyTwo = (1..62).map { at(NOON - (it * 2).hours, id = "e$it") }

    assertEquals(Milestone(value = 100, remaining = 38, reached = 50), stats(sixtyTwo).nextMilestone)
}

@Test
fun `a total sitting on a rung has passed that rung`() {
    val ten = (1..10).map { at(NOON - (it * 2).hours, id = "e$it") }

    assertEquals(Milestone(value = 25, remaining = 15, reached = 10), stats(ten).nextMilestone)
}
```

`StatisticsStateMapperTest` constructs `Milestone(value = 250, remaining = 103, reached = 100)` and `Milestone(value = 25, remaining = 4, reached = 10)`.

- [ ] **Step 2:** `./gradlew :domain:testAndroidHostTest --rerun` fails to compile (no `reached`): the red state.
- [ ] **Step 3: Implement.**

```kotlin
/**
 * [remaining] is how many more cats reach [value]; never zero, because a reached milestone is past.
 * [reached] is the rung the total has already passed, or 0 below the first.
 */
data class Milestone(val value: Int, val remaining: Int, val reached: Int)
```

```kotlin
private fun nextMilestone(total: Int): Milestone? =
    Tuning.MILESTONES.firstOrNull { it > total }?.let { next ->
        Milestone(value = next, remaining = next - total, reached = Tuning.MILESTONES.lastOrNull { it <= total } ?: 0)
    }
```

- [ ] **Step 4:** `./gradlew :domain:testAndroidHostTest --rerun :presentation:testAndroidHostTest --rerun` passes (XML).
- [ ] **Step 5: Commit** `feat: a milestone knows the rung the total has passed`, after compiling `:presentation` tests.

### Task 2: The Counter's state carries the milestone (`:presentation`)

**Files:**
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/counter/CounterState.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/counter/CounterStateMapper.kt`
- Modify: `presentation/src/commonMain/kotlin/dev/catsradar/presentation/counter/CounterStore.kt` (the `observeStats` `map` call)
- Test: `presentation/src/commonTest/kotlin/dev/catsradar/presentation/counter/CounterStateMapperTest.kt`, `CounterStoreTest.kt`

**Interfaces:**
- Consumes: `Milestone(value, remaining, reached)` (Task 1); `MilestoneState(valueLabel, remainingLabel)` from `presentation.statistics`.
- Produces: `data class CounterMilestoneState(val next: MilestoneState, val fraction: Float)`; `CounterState.milestone: CounterMilestoneState? = null`; `CounterStateMapper.map(…, milestone: Milestone? = null, …)`.

- [ ] **Step 1: Failing tests** (mapper):

```kotlin
@Test
fun `the count carries the next milestone and how far it has come from the last one`() {
    assertEquals(
        CounterMilestoneState(MilestoneState(valueLabel = "100", remainingLabel = "38"), fraction = 12f / 50f),
        mapper.map(count = 62, undoVisible = false, milestone = Milestone(value = 100, remaining = 38, reached = 50)).milestone,
    )
}

@Test
fun `below the first rung the arc starts from nothing`() {
    assertEquals(0f, mapper.map(count = 0, undoVisible = false, milestone = Milestone(1, 1, 0)).milestone?.fraction)
}

@Test
fun `past the last rung there is no milestone`() {
    assertNull(mapper.map(count = 10_000, undoVisible = false, milestone = null).milestone)
}
```

and the Store:

```kotlin
@Test
fun `the Counter carries the milestone its stats compute`() = runTest(mainDispatcher) {
    val (store, _) = newStore()
    runCurrent()
    repeat(3) {
        store.dispatch(CounterIntent.TallyClicked)
        runCurrent()
    }

    assertEquals(
        CounterMilestoneState(MilestoneState(valueLabel = "10", remainingLabel = "7"), fraction = 2f / 9f),
        store.state.value.milestone,
    )
}
```

- [ ] **Step 2:** `./gradlew :presentation:testAndroidHostTest --rerun` fails to compile: red.
- [ ] **Step 3: Implement.** In `CounterState.kt`:

```kotlin
    /** Null before the total is read, and past the last milestone. */
    val milestone: CounterMilestoneState? = null,
```

```kotlin
/** [fraction] is how far the total has come from the milestone already reached toward [next]. */
data class CounterMilestoneState(val next: MilestoneState, val fraction: Float)
```

In the mapper, a `milestone: Milestone? = null` parameter after `coatPrompt`, mapped as:

```kotlin
        milestone = milestone?.toState(),
```

```kotlin
    private fun Milestone.toState(): CounterMilestoneState = CounterMilestoneState(
        next = MilestoneState(valueLabel = value.toString(), remainingLabel = remaining.toString()),
        fraction = (value - remaining - reached).toFloat() / (value - reached),
    )
```

In the Store's `observeStats` block, pass `milestone = stats.nextMilestone`.

- [ ] **Step 4:** `:presentation:testAndroidHostTest --rerun` passes; `:ui:compileDebugKotlin` compiles (the field has a default).
- [ ] **Step 5: Mutations** (commit first): the fraction without `reached` (`(value - remaining).toFloat() / value`) fails the first mapper test; dropping `milestone = stats.nextMilestone` fails the Store test. Restore.
- [ ] **Step 6: Commit** `feat: the Counter's state carries the next milestone and the arc's fraction`.

### Task 3: The cookie, the arc and the line (`:ui`)

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/theme/CatsRadarTheme.kt` (emphasized styles)
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/counter/TallyBlock.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/counter/RollingCount.kt` (base style `displayLargeEmphasized`)
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/counter/CounterScreen.kt` (the line; `milestone` passed to the block; previews)
- Modify: `ui/src/main/res/values/strings.xml`, `ui/src/main/res/values-ru/strings.xml` (`counter_count_caption`)
- Test: `app/src/test/kotlin/dev/catsradar/app/theme/CatsRadarThemeTest.kt`, new `app/src/test/kotlin/dev/catsradar/app/counter/CounterMilestoneTest.kt`

**Interfaces:**
- Consumes: `CounterState.milestone` (Task 2).
- Produces: `const val MilestoneArcTestTag = "milestone-arc"` (public, in `TallyBlock.kt`, the pattern of `CatDotTestTag`); `TallyBlock(totalLabel, count, tapBurst, modifier, milestone: CounterMilestoneState? = null, onClick)`.

- [ ] **Step 1: Failing tests.** In `CatsRadarThemeTest`:

```kotlin
@Test
fun `the emphasized styles are a weight above the theme's own`() {
    var typography: Typography? = null
    compose.setContent { CatsRadarTheme { typography = MaterialTheme.typography } }
    compose.waitForIdle()

    val weights = typography?.run {
        listOf(displayLargeEmphasized, headlineMediumEmphasized, titleLargeEmphasized, titleMediumEmphasized).map { it.fontWeight }
    }
    assertEquals(listOf(FontWeight.ExtraBold, FontWeight.Bold, FontWeight.Bold, FontWeight.Bold), weights)
}
```

`CounterMilestoneTest` (`@Config(qualifiers = "w411dp-h891dp")`, the `ComponentActivityRegistered` rule chain of `CounterControlsTest`), with `state = CounterState(totalLabel = "62", count = 62, undoVisible = false, milestone = CounterMilestoneState(MilestoneState("100", "38"), 0.24f))`:

```kotlin
@Test
fun `the count says how many more reach the next milestone`() {
    show(milestone = sixtyTwoOfHundred)

    compose.onNodeWithText("38 more to reach 100").assertIsDisplayed()
}

@Test
fun `the arc is drawn while there is a milestone to reach, and not past the last one`() {
    var milestone by mutableStateOf<CounterMilestoneState?>(sixtyTwoOfHundred)
    compose.setContent { CatsRadarTheme { CounterScreen(state = counter(milestone)) } }

    compose.onNodeWithTag(MilestoneArcTestTag).assertExists()
    milestone = null
    compose.onNodeWithTag(MilestoneArcTestTag).assertDoesNotExist()
}

@Test
fun `with no milestone left the controls under the count do not move`() {
    var milestone by mutableStateOf<CounterMilestoneState?>(sixtyTwoOfHundred)
    compose.setContent { CatsRadarTheme { CounterScreen(state = counter(milestone)) } }
    val before = walkButton().getUnclippedBoundsInRoot()

    milestone = null

    assertEquals(before, walkButton().getUnclippedBoundsInRoot())
}

@Test
fun `a tap in the block's corner, outside the cookie, still logs a cat`() {
    var taps = 0
    compose.setContent { CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred), onTallyClick = { taps++ }) } }

    compose.onNodeWithContentDescription("62").performTouchInput { click(Offset(8f, 8f)) }

    assertEquals(1, taps)
}

@Test
fun `under the number the count names what it counts`() {
    show(milestone = sixtyTwoOfHundred)

    compose.onNodeWithText("cats").assertIsDisplayed()
}
```

(`walkButton()` finds `R.string.counter_walk_start`'s text; `counter(m)` builds the state above with `milestone = m`.)

- [ ] **Step 2:** the `:app` classes fail (no styles, no tag, no line): red, read from XML.
- [ ] **Step 3: Implement.**

Theme, in `CatsRadarTypography`:

```kotlin
    displayLargeEmphasized = BaseTypography.displayLargeEmphasized.copy(fontWeight = FontWeight.ExtraBold),
    headlineMediumEmphasized = BaseTypography.headlineMediumEmphasized.copy(fontWeight = FontWeight.Bold),
    titleLargeEmphasized = BaseTypography.titleLargeEmphasized.copy(fontWeight = FontWeight.Bold),
```

(`titleMediumEmphasized` is Material's Bold already.)

`RollingCount`'s style starts from `MaterialTheme.typography.displayLargeEmphasized`.

`TallyBlock`: the clickable box keeps its modifier, label and interaction source; inside it, centred, a `Modifier.aspectRatio(1f)` square scaled by the press spring holds, in order:
1. the cookie: `matchParentSize()`, turned by `PressTurnDegrees` (8°) on the same spring, clipped to `MaterialShapes.Cookie12Sided.toShape()`, filled `primaryContainer`, with the ripple indication;
2. when `milestone != null`, the arc: a `Canvas` at `fillMaxSize(ArcFraction)` (0.76) tagged `MilestoneArcTestTag`, stroke `size.minDimension * 0.026`, track `onPrimaryContainer.copy(alpha = 0.15f)` full circle, arc `primary` from −90° sweeping `360 * progress` with a round cap; `progress` animates to `fraction` on `MaterialTheme.motionScheme.slowSpatialSpec()`;
3. at `fillMaxSize(NumberFraction)` (0.58), with `LocalContentColor` = `onPrimaryContainer`, a centred `Column`: `RollingCount` with `Modifier.weight(1f, fill = false)` and, once `count != null`, `pluralStringResource(R.plurals.counter_count_caption, count)` in `titleMedium`; both `clearAndSetSemantics {}`;
4. `TapBurst`, aligned `TopEnd`.

`CounterScreen`: `TallyBlock(…, milestone = state.milestone)` and `CurrentOutingLine(state.currentOuting, milestone = state.milestone)`. **Amended during the slice (owner: the cookie is very small):** the milestone takes the outing line's slot instead of a line of its own. With no outing open, `CurrentOutingLine` shows `stringResource(R.string.statistics_next_milestone, next.remainingLabel, next.valueLabel)` in the same `bodyMedium` style, on `onSurfaceVariant`, one line; with neither an outing nor a milestone it keeps its empty line. A test adds: `during an outing its line takes the milestone's place`.

Strings: EN `counter_count_caption` one "cat", other "cats" (`tools:ignore="ImpliedQuantity"`); RU one «котик», few «котика», many «котиков», other «котика».

- [ ] **Step 4:** the classes pass; then `:ui:testDebugUnitTest --rerun :app:testDebugUnitTest --rerun` zero failures (XML).
- [ ] **Step 5: Mutations** (commit first): always draw the arc (drop `milestone != null`) fails the arc test; drop the line fails the line test; make only the cookie clickable (move `clickable` onto the square) fails the corner test. Restore each.
- [ ] **Step 6: Commit** `feat: the Counter's count in a cookie, with its milestone arc and line`.

### Task 4: The record

- `docs/features/counting-cats.md` § Feedback for the tap: the block draws the cookie, the arc and the caption; the line under it; the whole block stays the button.
- `docs/features/statistics.md` § Milestones: the milestone also names the rung already passed, which the Counter's arc starts from.
- The spec: § 2's order is *the notices, the count, the milestone line, the outing line, the walk row, the stat tiles, the coat grid, Photo* (the outing line sits above the walk row today); the stat tiles go *under the walk row*; § 1 *Type* says the theme defines the `*Emphasized` styles a weight step above its own; § 3 and *Decided here* name the Russian voice «котик». The map's E3 purpose line follows.
- Map row E2 `in-review` when the PR opens.
- Commit `docs: the Counter's count in a cookie`.

### Task 5: Renders

The harness (scratchpad `harness/DesignShots.kt`) draws the Counter at 62 with a milestone, empty, and cramped (`@Config` height 600 dp, font scale 1.5), light and dark, before Task 3 and after. Inspect each pair; list what changed.

### Task 6: The gate

`CI=true ./gradlew check :app:assembleRelease` detached, as in E1, with the sha in the log; per-commit compile; PR stacked on #202 (`base: feature/expressive-theme`).

### Task 7: The tags on the ring (amendment, 2026-09-27)

The owner found the line under the cookie foreign and its slot empty, and chose the Ring treatment of prototype version 13. Spec § 2 *The tags on the ring*. Tasks 5 and 6 run again after it.

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/counter/TallyBlock.kt` (`currentOuting` parameter; `GoalTag`, the outing tag and the arc's head; the tags anchored on the ring's edges)
- Rename: `ui/src/main/kotlin/dev/catsradar/ui/counter/CurrentOutingLine.kt` → `CurrentOuting.kt` (`CurrentOutingLine` goes; `CurrentOuting(state)` is the line); create `RingTags.kt`
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/counter/CounterScreen.kt` (no line in `below`; `currentOuting` passed to the block)
- Create: `ui/src/main/res/drawable/ic_flag.xml`
- Test: `app/src/test/kotlin/dev/catsradar/app/counter/CounterMilestoneTest.kt`; rename `CurrentOutingLineTest.kt` → `CurrentOutingTest.kt`
- Docs: `counting-cats.md` (the block, *The controls do not jump*, *The outing in progress*, the file list)

- [ ] **Step 1: Failing tests** in `CounterMilestoneTest`: `the goal is pinned where the ring closes, and TalkBack says how many more reach it` (the node described "38 more to reach 100" is displayed and its centre sits on the arc's top edge within 1 dp); `during an outing its tag sits at the ring's bottom, and the goal stays` (the goal stays displayed; the merged outing node's centre sits on the arc's bottom edge); `nothing sits between the count and the walk button, with or without an outing` (the walk button's top is one column gap, 16 dp, under the block's bottom, before and after an outing opens). `CurrentOutingTest` keeps its cases, renamed; `in a cramped block the number keeps clear of the tags on the ring, and the caption gives way` (w320dp-h640dp, font 1.5, an outing).
- [ ] **Step 2:** red — the line is still under the block; no node carries the description.
- [ ] **Step 3: Implement** (as landed after review). `RingTags.kt` holds the ring's fractions (`RingFraction`, `RingStrokeFraction`, shared with the arc); `RingTags`, a block-sized box that squashes with the cookie, holding `GoalTag` on the ring's top and the outing's `RingPill` on its bottom, both centred on the stroke's centre line by `onRingLine` (the outing's pill may overhang the block by its sides, so the line keeps the block's width); and `clearOfRingTags`, which gives `CountAndCaption` the square's inner 58 % less what the pills reach into, their heights measured with a `TextMeasurer`. `CountAndCaption` drops the caption when keeping it would push the number below `MinCountSize`. `GoalTag` is `semantics(mergeDescendants = true) { contentDescription = next.label() }` with the number cleared: `clearAndSetSemantics` on the pill would merge its label into the block's. `MilestoneArc` draws a dot at the arc's head (`primary`, a `primaryContainer` rim) when `progress > 0`. `CurrentOuting.kt` keeps the outing's line; `CounterScreen` passes `currentOuting` to the block. `CounterMilestoneTest` runs on native graphics, since text geometry is fake otherwise.
- [ ] **Step 4:** green; `CounterControlsTest`, `CurrentOutingTest` (adjust the give-way font scales only if the tag's narrower room moves the stage a rule kicks in at; the order stays) and `CounterMilestoneTest` pass.
- [ ] **Step 5: Mutations** (commit first): no goal tag → AC-6; the outing tag anchored at the top → AC-6b; a line restored under the block → AC-17; the number's box ignoring the tags → AC-18.
- [ ] **Step 6: Docs and commit** `feat: the goal and the outing ride tags on the ring`.
