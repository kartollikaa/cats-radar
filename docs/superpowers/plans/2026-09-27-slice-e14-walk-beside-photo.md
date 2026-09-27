# Slice E14 — The walk beside Photo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Walk is a tonal button beside Photo that turns warm and reads *Hold to end* while a walk is on; the cookie wears the walk and breathes; Undo floats in the count block; the walk row and the walk's own time on the Counter go.

**Architecture:** `WalkRow` goes. `WalkButton` keeps its gesture (the hold, its fill and ticks, the accessibility click) and gets a new surface: one line, Medium height, `secondaryContainer` at rest and `tertiaryContainer` with the toggle's checked corners during a walk. `CounterScreen`'s `below` ends in a `Row` of `WalkButton` and `PhotoButton(weight 1)`. `TallyBlock` takes `walking`, `undoVisible` and `onUndoClick`: its fill animates between the two containers, `rememberCookieBreath` swells the shape while a walk is on and animations are enabled, and `UndoButton` sits in the block's top-start corner across from the badge. The Store answers a press let go early with a toast effect. `CounterState.walkElapsedLabel` and its mapper go, since nothing shows the walk's time on the Counter.

**Tech Stack:** Compose Material 3 Expressive (`ButtonDefaults.MediumContainerHeight`, `ToggleButtonDefaults` shapes, `animateColorAsState`, `rememberInfiniteTransition`), Robolectric `@GraphicsMode(NATIVE)` pixel samples, `Settings.Global.ANIMATOR_DURATION_SCALE`.

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 2 *The walk*, *Photo*. Map: row E14 and *Slice E14*. Criteria: `expressive-e14.md`.

## Global Constraints

- "A tap starts a walk. While a walk is on it takes `tertiaryContainer` and `onTertiaryContainer`, the toggle's checked corners, … and *Hold to end*; the held press with its fill and haptic ticks moves here from the old button unchanged."
- "The button carries no time: the outing's tag on the ring has the outing's, and the notification's chronometer has the walk's."
- "While a walk is on, the cookie's fill is `tertiaryContainer` and the number and 'cats' `onTertiaryContainer`; the ring, its dot and the tags keep their colours."
- "The shape alone … swells two per cent and settles over about three seconds … for as long as the walk lasts; it stands still when no walk is on, and when the system's animator scale is zero."
- "Undo … appears and goes without moving anything, and a tap on it takes a cat back and never logs one."
- Strings EN and RU; no user-facing text in code. Gradle only through `ctx_execute`; `--rerun --no-build-cache`; counts from JUnit XML; per-commit compiles.

## Deviations from the prototype, decided here

- **The walking cat stays on the button in both states**, walking during a walk as `walking-mode.md` describes, in place of the prototype's stop glyph: a moving cat says "on a walk" better than a static square, and *Hold to end* already says what the press does. `WalkingCatAnimationTest` stays.
- **Undo sits in the block's top-start corner**, across from the "+N" badge at the top end, not the bottom-end corner the prototype drew: on a narrow phone the outing tag at the ring's bottom can reach the bottom corners, and the top corners are free of tags.

---

### Task 0: Freeze the criteria (`expressive-e14.md`)

### Task 1: The presentation loses the walk's time

**Files:** `presentation/…/counter/CounterState.kt`, `CounterStateMapper.kt`, `CounterStore.kt`, `CounterIntent.kt`, `CounterEffect.kt`; tests `CounterStateMapperTest.kt`, `CounterStoreWalkTest.kt`.

- [ ] **Step 1: Failing tests.** In `CounterStoreWalkTest`: `a press let go before the hold is up raises the hint` — dispatch `CounterIntent.WalkHoldReleased`, expect `CounterEffect.ShowWalkHint` on `effects`. In `CounterStateMapperTest`: the mapper's `walkElapsedLabel` tests go, and the whole-state assertions lose the field.
- [ ] **Step 2:** red.
- [ ] **Step 3: Implement.** Remove `walkElapsedLabel` from `CounterState`, the mapper's function and the Store's elapsed collection (the walk's start is still read for the notification elsewhere; only the Counter's label goes). Add `WalkHoldReleased` and `ShowWalkHint`.
- [ ] **Step 4:** green; `:presentation:testDebugUnitTest` re-executed.
- [ ] **Step 5: Commit** `feat: the Counter stops telling the walk's time, and answers an early release with a hint`.

### Task 2: The button beside Photo

**Files:** `ui/…/counter/WalkButton.kt` (surface rewritten), `WalkRow.kt` (deleted), `CounterScreen.kt`, `ui/src/main/res/values{,-ru}/strings.xml`; tests `app/…/counter/WalkButtonLookTest.kt` (new, replaces `WalkRowLookTest.kt`), `CounterControlsTest.kt`, `ui/…/counter/WalkRowTest.kt` (deleted).

- [ ] **Step 1: Failing tests** (`@GraphicsMode(NATIVE)`, `w411dp-h891dp`):
  - `Walk stands at the start of Photo's row, as tall as the split button` — the walk button's bounds: left edge at the screen's start padding, height 56 dp, top equal to Photo's top; Photo's left is the walk's right plus 8 dp.
  - `at rest the button wears the secondary container and reads Walk` — a pixel 6 dp in at mid-height is `secondaryContainer`; the text `counter_walk` is shown; nothing shows `counter_walk_start_hint` (the string is gone).
  - `during a walk it wears the tertiary container, squarer corners, and reads Hold to end` — mid-height pixel `tertiaryContainer`; a pixel 3 dp in at the top corner is `tertiaryContainer` (a 16 dp corner covers it, a pill does not); text `counter_walk_stop`.
  - `at a large font both labels stay whole` (`fontScale = 1.5f`): `isWhole(counter_walk_stop)` and `isWhole(counter_camera)`.
  - In `CounterControlsTest`: `a press let go before the hold is up says it has to be held` — the press ends early, `onWalkHoldReleased` fires once; the `walk button sits centred`, `is as tall stopping as starting`, `shows how long it has lasted`, `in Russian the walk's time…` tests go; the hold tests stay.
- [ ] **Step 2:** red.
- [ ] **Step 3: Implement.** `WalkButton(walking, modifier, onWalkingChange, onHoldReleased)`: `Surface` with `shape = animateShapeAsState`-free approach: `RoundedCornerShape(animateDpAsState(if (walking) 16.dp else 28.dp))`; colours `secondaryContainer`/`onSecondaryContainer` or `tertiaryContainer`/`onTertiaryContainer` via `animateColorAsState`; `Row(heightIn(min = 56.dp), padding(horizontal = 16.dp))` with `WalkingCat(walking, 20.dp)` and one `Text` (`counter_walk` / `counter_walk_stop`, `labelLarge`). The hold's fill stays `tertiary` at 0.4. `waitForUpOrCancellation()` returning with `hold.isActive` calls `onHoldReleased()`. Strings: `counter_walk` = "Walk" / «Прогулка»; `counter_walk_stop` = "Hold to end" / «Удерживайте»; delete `counter_walk_start`, `counter_walk_start_hint`, `counter_walk_stop_hint`, `counter_walk_stop_hint_timed`. `CounterScreen.below`: `CoatGrid` then `Row(spacedBy(8.dp)) { WalkButton(...); PhotoButton(Modifier.weight(1f)) }`; the `onWalkHoldReleased` callback is new. Delete `WalkRow.kt`, `WalkRowTest.kt`, `WalkRowLookTest.kt`.
- [ ] **Step 4:** green; `CounterMilestoneTest` needs its *nothing sits between the count and the walk button* test renamed to the coats (Task 4).
- [ ] **Step 5: Mutations** (after commit): the button in `tertiaryContainer` at rest; the walking corners at 28 dp; `onHoldReleased` never called.
- [ ] **Step 6: Commit** `feat: Walk is a tonal button beside Photo`.

### Task 3: The cookie wears the walk, and Undo floats in the block

**Files:** `ui/…/counter/CookieBreath.kt` (new), `TallyBlock.kt`, `UndoButton.kt` (unchanged), `CounterScreen.kt`; tests `app/…/counter/CookieBreathTest.kt` (new), `CounterWalkLookTest.kt` (new), `CounterControlsTest.kt`, `CounterMilestoneTest.kt`.

- [ ] **Step 1: Failing tests.**
  - `CookieBreathTest` (`mainClock.autoAdvance = false`, as `CookieTurnAnimationTest`): `while breathing the shape swells and settles between one and one and a fiftieth` — over 3 000 ms of frames the value takes at least two distinct values, all in `[1f, 1.02f]`; `without a walk the shape stands still` — 1f on every frame; `with the animator scale at zero the shape stands still during a walk` — `Settings.Global.putFloat(resolver, ANIMATOR_DURATION_SCALE, 0f)` then 1f on every frame.
  - `CounterWalkLookTest` (NATIVE): `during a walk the cookie's fill is the tertiary container, and the number wears its colour` — a pixel on the number's row 0.33 of the square's side from its centre is `tertiaryContainer` during a walk and `primaryContainer` without; `the ring keeps its colour on the warm cookie` — a pixel on the arc's head dot is `primary` in both states.
  - `CounterControlsTest`: `Undo sits in the block's top-start corner and a tap on it takes a cat back, never logs one` — Undo's bounds inside the block's bounds at its top start; a click calls `onUndoClick` once and `onTallyClick` never; `Undo appearing and going leaves the count the same size` stays as is.
  - `CounterMilestoneTest`: `nothing sits between the count and the coats, with or without an outing`.
- [ ] **Step 2:** red.
- [ ] **Step 3: Implement.** `CookieBreath.kt`: `fun rememberCookieBreath(breathing: Boolean): State<Float>` — `if (!breathing) return remember { mutableStateOf(1f) }`; else `rememberInfiniteTransition().animateFloat(1f, 1.02f, infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse))`. `@Composable fun animationsEnabled(): Boolean` reads `Settings.Global.getFloat(LocalContext.current.contentResolver, ANIMATOR_DURATION_SCALE, 1f) > 0f`, remembered. `TallyBlock(…, walking: Boolean = false, undoVisible: Boolean = false, onUndoClick)`: `val fill by animateColorAsState(if (walking) tertiaryContainer else primaryContainer, colorScheme spec)`, `val ink by animateColorAsState(on…)`; the background box uses `fill`, its `graphicsLayer` multiplies `scale` by the breath; `CookieContent` provides `ink` as `LocalContentColor`, the arc's track is `ink` at 0.15 and its dot's rim is `fill`; `UndoButton(visible = undoVisible, onClick = onUndoClick, modifier = Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-8).dp))` in the outer box. `CounterScreen` passes `walkingMode`, `undoVisible`, `onUndoClick` to the block.
- [ ] **Step 4:** green; `CurrentOutingTest`, `CounterMilestoneTest`, `RollingCount` tests pass with `autoAdvance` untouched (the infinite transition is cancelled on idle, never awaited).
- [ ] **Step 5: Mutations** (after commit): the breath at 1.05; the breath running without a walk; the animator scale ignored; the fill `primaryContainer` during a walk; Undo at the bottom end.
- [ ] **Step 6: Commit** `feat: the cookie wears the walk, and Undo floats in the block`.

### Task 4: The hint reaches the screen, and the record

**Files:** `app/…/navigation/CounterDestination.kt`; `docs/features/counting-cats.md`, `walking-mode.md`; the map.

- [ ] **Step 1:** `CounterDestination` forwards `onWalkHoldReleased` as `CounterIntent.WalkHoldReleased` and shows `CounterEffect.ShowWalkHint` as the milestones' toast with `counter_walk_hold_hint` ("Hold to end the walk" / «Удерживайте, чтобы закончить прогулку»). `CounterDestinationTest` (or the existing entry test) covers the effect → toast if the milestone toast is covered; else the Store test is the proof.
- [ ] **Step 2: Docs.** `counting-cats.md` § *The controls do not jump* (Walk beside Photo, Undo in the block, the row gone), `walking-mode.md` § *Started from the Counter* and § *How long the walk has lasted* (the Counter's button shows no time; the outing tag and the notification do), § *Stopping takes a hold* (the hint on an early release; the button's words), § *The button's cat walks*. Map row E14 `in-review`; spec § 2 *The walk*: the two deviations above.
- [ ] **Step 3: Commit** `docs: the walk beside Photo`.

### Task 5: Renders, review and the gate

Counter harness before (`62a46dbe`) and after: light, dark, font 1.5, at rest, during a walk with Undo up. The running app on the emulator. `/code-review`; the gate in a scratch worktree with the mutations; the draft PR stacked on #222; the acceptance gate.
