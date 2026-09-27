# Slice E3 — The walk row Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Counter's walk button is drawn as an extended floating action button in `tertiaryContainer`, 56 dp tall with the FAB's corners, and Undo is a filled tonal button; everything the row does stays as it is.

**Architecture:** Two `:ui` files change their look only. `WalkButtonSurface` in `WalkButton.kt` takes the extended FAB's shape and colour and a fixed height; the gesture, the fill, the walking cat and the hints are untouched. `UndoChip.kt` becomes `UndoButton.kt`, a `FilledTonalButton` under the same `AnimatedVisibility`. `WalkRow` and its layout rules do not change. Two Robolectric tests in `:app` pin the height in both states, the button's colour by sampling a pixel, and Undo's height.

**Tech Stack:** Compose Material 3 (`FloatingActionButtonDefaults.extendedFabShape`, `FilledTonalButton`), Robolectric `@GraphicsMode(NATIVE)` for the pixel sample.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2 *The walk row*. Map: row E3 and *Slice E3*. Criteria: `expressive-e3.md` (Task 0).

## Global Constraints

- **Behaviour unchanged.** `counting-cats.md`: "Undo has a place of its own at the far end of the walk button's row … the walk button sits in the middle of that row, and Undo appearing beside it does not move it … Undo is never squeezed. A tap on the button starts a walk, but stopping one takes a press held until a fill crosses the button … The button keeps one height whether it starts or stops a walk." `CounterControlsTest` holds all of it and must stay green.
- **Look, from the spec:** "an extended floating action button in `tertiaryContainer`: 56 dp tall with the FAB's corners. The walking cat, the fill that a held press drives, the timed hint and the one height stay as they are. Undo becomes a filled tonal button in its own place at the end of the row."
- **Colours from the scheme only**; the fill stays `tertiary` at 0.4 alpha.
- **Gradle** only through `ctx_execute`; per-commit compile of `:app` unit tests; counts from JUnit XML.
- **Docs in the same PR:** `counting-cats.md` (the Undo control's look), `walking-mode.md` if it names the button's colour or shape.

---

### Task 0: Freeze the acceptance criteria

`acceptance:acceptance-criteria` (auto) into `expressive-e3.md`: the two new tests with their mutations, the kept `CounterControlsTest`, the docs, renders before and after, every commit compiling.

### Task 1: The walk button as an extended FAB

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/counter/WalkButton.kt`
- Test: `app/src/test/kotlin/dev/catsradar/app/counter/WalkRowLookTest.kt` (new)

- [ ] **Step 1: Failing tests** (`@RunWith(AndroidJUnit4::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `@Config(qualifiers = "w411dp-h891dp")`, the `ComponentActivityRegistered` rule chain):

```kotlin
@Test
fun `the walk button is 56 dp tall, starting and stopping alike`() {
    var walking by mutableStateOf(false)
    compose.setContent { CatsRadarTheme { CounterScreen(state = counter(walking)) } }

    assertEquals(56.dp, walkButton(walking = false).getUnclippedBoundsInRoot().height)
    walking = true
    assertEquals(56.dp, walkButton(walking = true).getUnclippedBoundsInRoot().height)
}

@Test
fun `the walk button wears the tertiary container whether or not a walk is on`() {
    var walking by mutableStateOf(false)
    var expected = Color.Unspecified
    compose.setContent {
        CatsRadarTheme {
            expected = MaterialTheme.colorScheme.tertiaryContainer
            CounterScreen(state = counter(walking))
        }
    }

    assertEquals(expected, walkButton(walking = false).pixelNearStart(atTopCorner = false))
    walking = true
    assertEquals(expected, walkButton(walking = true).pixelNearStart(atTopCorner = false))
}
```

`counter(walking)` is `CounterState(totalLabel = "3", count = 3, undoVisible = false, walkingMode = walking)`; `walkButton(walking)` finds the button by its label text as `CounterControlsTest` does; `pixelNearStart(atTopCorner)` is `captureToImage().toPixelMap()` read 6 dp in from the button's start edge, at mid-height (clear of the cat, the words and the fill) or 6 dp down from the top for the corner test (Task 1 Step 3).

- [ ] **Step 2:** the height test fails (today's button is about 48 dp); the colour test fails on the idle state (`secondaryContainer`). Read the XML.
- [ ] **Step 3: Implement.** In `WalkButtonSurface`: `shape = ShapeDefaults.Large` (Material's own extended-FAB corner, 16 dp; `extendedFabShape` reads the theme's 28 dp `large`, a pill at this height), `color = MaterialTheme.colorScheme.tertiaryContainer`, `contentColor = onTertiaryContainer`, and `Modifier.heightIn(min = 56.dp)` on the `Row` (a floor, as the FAB's own, so a large font grows it) with `padding(horizontal = 16.dp)`, the small extended FAB's own spacing, which keeps the width the chip-era row had. In `WalkButton`, `modifier.shadow(elevation = 2.dp, shape = …)` replaces the clip: it draws the shadow and clips the gesture's fill and ripple to the same corners. A third test samples a pixel 6 dp in from the top-left corner: `tertiaryContainer` inside a 16 dp curve, background outside a pill's.
- [ ] **Step 4:** the two tests pass; `:app:testDebugUnitTest --tests 'dev.catsradar.app.counter.*'` stays green (`CounterControlsTest` in full).
- [ ] **Step 5: Mutation** (commit first): height 48 dp fails the first test; `secondaryContainer` for the idle state fails the second; a 28 dp corner, what `extendedFabShape` reads, fails the corner test. Restore.
- [ ] **Step 6: Commit** `feat: the walk button as an extended FAB`.

### Task 2: Undo as a filled tonal button

**Files:**
- Rename: `ui/src/main/kotlin/dev/catsradar/ui/counter/UndoChip.kt` → `UndoButton.kt`; `UndoChip(...)` → `UndoButton(...)` and its call in `WalkRow.kt`.
- Test: `WalkRowLookTest.kt`

- [ ] **Step 1: Failing test:**

```kotlin
@Test
fun `Undo stands as tall as a button`() {
    compose.setContent { CatsRadarTheme { CounterScreen(state = counter(walking = false).copy(undoVisible = true)) } }

    assertEquals(40.dp, compose.onNodeWithText(context.getString(R.string.counter_undo)).getUnclippedBoundsInRoot().height)
}
```

- [ ] **Step 2:** fails (the chip is 32 dp).
- [ ] **Step 3: Implement.** `FilledTonalButton(onClick = onClick, contentPadding = ButtonDefaults.SmallContentPadding) { Text(text = stringResource(R.string.counter_undo), maxLines = 1) }` under the existing `AnimatedVisibility`: Material's small (40 dp) button padding, as narrow as the chip was, so at font scale 1.5 the walk's time and hint still fit beside it (`at a large font a walk's time and hint still fit beside Undo`); the preview follows.
- [ ] **Step 4:** the class passes; `CounterControlsTest` in full stays green, including *Undo appearing and going leaves the count the same size* and the no-jump tests.
- [ ] **Step 5: Commit** `feat: Undo on the Counter as a filled tonal button`.

### Task 3: The record

- `counting-cats.md`: where it calls Undo a chip in describing the control (not the undo window), say button; § *The controls do not jump* names the button's look in one clause.
- `walking-mode.md`: only if it names the button's shape or colour.
- Map row E3 `in-review`.
- Commit `docs: the Counter's walk row`.

### Task 4: Renders and the gate

The Counter harness (scratchpad `harness/CounterShots.kt`) before and after, light, dark and font scale 1.5, at 62 with a walk on and off and with Undo up; inspect. Then the gate in a scratch worktree with the sha in every log, per-commit compiles, and the PR stacked on #208.
