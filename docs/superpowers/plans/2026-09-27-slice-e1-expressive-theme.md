# Slice E1 — The Expressive theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `CatsRadarTheme` draws with `MaterialExpressiveTheme` and `MotionScheme.expressive()`, keeping its colour scheme, shapes and typography, and `app-shell.md` stops saying the expressive theme is out of reach.

**Architecture:** One call changes in `:ui`: `MaterialTheme(...)` becomes `MaterialExpressiveTheme(colorScheme, MotionScheme.expressive(), shapes, typography, content)`. `MaterialExpressiveTheme` provides the internal `LocalUsingExpressiveTheme = true`, which is what switches components to their Expressive defaults; the public, testable effect is `MaterialTheme.motionScheme`. A Robolectric Compose test in `:app` pins the motion scheme and that the scheme, shapes and typography passed in still reach the content.

**Tech Stack:** Compose Material 3 `1.5.0-alpha27` (pinned in `gradle/libs.versions.toml`), Robolectric, `createComposeRule` (`androidx.compose.ui.test.junit4.v2`).

**Spec:** `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` § 1 The theme. Map: `docs/tbd/decompositions/2026-09-27-expressive-redesign.md` row E1 and *Slice E1*. Criteria: `expressive-e1.md` in this session's acceptance directory (Task 0).

## Global Constraints

- **Same colours, shapes and type.** The spec: "with the same colour scheme, shapes and typography." `CatsRadarColors.kt`, `CatsRadarShapes`, `CatsRadarTypography` do not change.
- **Colour policy unchanged.** "Dynamic colour on Android 12 and later, the teal palette below it and in every preview: unchanged." `:app` keeps passing `rememberDeviceColorScheme()`; `:ui` never sees the platform.
- **Screen transitions keep their own specs**; `NavTransitionTimingTest` does not change.
- **Verified APIs** (sources of `material3-android-1.5.0-alpha27`): `public fun MaterialExpressiveTheme(colorScheme: ColorScheme? = null, motionScheme: MotionScheme? = null, shapes: Shapes? = null, typography: Typography? = null, content)`, no opt-in; `MotionScheme.expressive()` returns a singleton, no opt-in; `MaterialTheme.motionScheme` is public. `LocalUsingExpressiveTheme` is **internal**: tests cannot read it.
- **Gradle** runs only through the context-mode `ctx_execute` tool (`language: "shell"`, `timeout: 900000`); a hook refuses it in Bash. Start with `cd "/Users/dmitrijmaksimov/Projects/Cats Radar/.claude/worktrees/imported-cats-notification-timeout-2638cd" &&` and end with `| tail -30`. Counts come from the JUnit XML under `app/build/test-results/testDebugUnitTest/`, never from the console.
- **Every commit compiles** `:ui` and `:app` main and test sources.
- **Comments:** default none (`docs/rules/code-commenting-standards.md`).
- **Docs in the same PR:** `docs/features/app-shell.md` § Look.

---

### Task 0: Freeze the acceptance criteria

Run `acceptance:acceptance-criteria` (auto) into `expressive-e1.md` before any code, naming each item with its evidence:
- the three tests of Task 1, by name, and their red run on main's theme;
- a mutation: passing `MotionScheme.standard()` fails the motion test;
- the full `:app:testDebugUnitTest` and `CI=true ./gradlew check :app:assembleRelease` green;
- the `app-shell.md` paragraph of Task 2, and the map row E1 at `in-review`;
- before and after renders of every tab, light and dark (Task 3), inspected; any visible change listed in the PR;
- every commit compiling.

### Task 1: The theme is expressive

**Files:**
- Modify: `ui/src/main/kotlin/dev/catsradar/ui/theme/CatsRadarTheme.kt`
- Create: `app/src/test/kotlin/dev/catsradar/app/theme/CatsRadarThemeTest.kt`

**Interfaces:**
- Consumes: `CatsRadarTheme(colorScheme: ColorScheme = catsRadarColorScheme(isSystemInDarkTheme()), content)`; `ComponentActivityRegistered` in `app/src/test/kotlin/dev/catsradar/app/testing/`.
- Produces: `CatsRadarTheme` with the same signature; every later slice relies on `MaterialTheme.motionScheme` being expressive.

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.catsradar.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class CatsRadarThemeTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `the theme moves with the expressive motion scheme`() {
        var motion: MotionScheme? = null
        compose.setContent { CatsRadarTheme { motion = MaterialTheme.motionScheme } }
        compose.waitForIdle()

        assertSame(MotionScheme.expressive(), motion)
    }

    @Test
    fun `the colour scheme it is given is the one the screen draws with`() {
        val given = darkColorScheme(primary = Color(0xFF123456))
        var drawn: ColorScheme? = null
        compose.setContent { CatsRadarTheme(colorScheme = given) { drawn = MaterialTheme.colorScheme } }
        compose.waitForIdle()

        assertSame(given, drawn)
    }

    @Test
    fun `the theme keeps its own corners and weights`() {
        var shapes: Shapes? = null
        var typography: Typography? = null
        compose.setContent {
            CatsRadarTheme {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
            }
        }
        compose.waitForIdle()

        assertEquals(RoundedCornerShape(28.dp), shapes?.large)
        assertEquals(FontWeight.Bold, typography?.displayLarge?.fontWeight)
    }
}
```

- [ ] **Step 2: Run it and watch the motion test fail**

Run (ctx_execute): `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.theme.CatsRadarThemeTest' --console=plain | tail -30`
Expected: FAIL. In `app/build/test-results/testDebugUnitTest/TEST-dev.catsradar.app.theme.CatsRadarThemeTest.xml`, `the theme moves with the expressive motion scheme` fails with an `AssertionError` (the standard scheme); the other two pass, since today's theme already passes them through.

- [ ] **Step 3: Write the implementation**

In `CatsRadarTheme.kt`, replace the `MaterialTheme` import with `MaterialExpressiveTheme` and add `MotionScheme`:

```kotlin
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
```

and the body:

```kotlin
@Composable
fun CatsRadarTheme(
    colorScheme: ColorScheme = catsRadarColorScheme(isSystemInDarkTheme()),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = CatsRadarShapes,
        typography = CatsRadarTypography,
        content = content,
    )
}
```

- [ ] **Step 4: Run the class and then the whole `:app` suite**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.catsradar.app.theme.CatsRadarThemeTest' --console=plain | tail -30`
Expected: PASS, 3 tests in the XML.
Then: `./gradlew :ui:testDebugUnitTest :app:testDebugUnitTest --console=plain | tail -30`
Expected: PASS. Any failure is a component whose Expressive default moved something a screen test measures; read the XML, fix the screen or the assertion's premise only if the spec allows it, and record it in the PR.

- [ ] **Step 5: Mutation**

Commit first (a checkout restores the last commit). Change `MotionScheme.expressive()` to `MotionScheme.standard()`, run the class, read the XML: the motion test fails. `git checkout -- ui/src/main/kotlin/dev/catsradar/ui/theme/CatsRadarTheme.kt`.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/kotlin/dev/catsradar/ui/theme/CatsRadarTheme.kt app/src/test/kotlin/dev/catsradar/app/theme/CatsRadarThemeTest.kt
git commit -m "feat: draw the app with the Material 3 Expressive theme"
```

### Task 2: The record

**Files:**
- Modify: `docs/features/app-shell.md` (§ Look, the *Why not `MaterialExpressiveTheme`* paragraph)
- Modify: `docs/tbd/decompositions/2026-09-27-expressive-redesign.md` (row E1 status)
- Modify: `docs/superpowers/specs/2026-09-27-expressive-redesign-design.md` (status line)

- [ ] **Step 1: Replace the paragraph**

The paragraph starting `**Why not \`MaterialExpressiveTheme\`.**` becomes:

```markdown
**Expressive.** The theme is `MaterialExpressiveTheme` with the expressive motion scheme
(`MotionScheme.expressive()`), over the same colours, shapes and type. Components take their
Expressive defaults: buttons change shape under a press, and motion inside components is a spring.
Screen transitions keep the specs above. The build pins a material3 alpha, where the expressive theme
and its components are public; `CatsRadarThemeTest` fails if the theme stops moving with the
expressive scheme or drops the scheme, shapes or type it is given.
```

- [ ] **Step 2: Status lines**

Map row E1: `planned` becomes `in-review` when the PR opens. Spec status line becomes: "direction chosen by the owner in chat, 2026-09-27 ("I like expressive from version 5. stick to it"); the details below were decided autonomously at the owner's request ("work in autonomous mode"), 2026-09-27."

- [ ] **Step 3: Commit**

```bash
git add docs/features/app-shell.md docs/tbd/decompositions/2026-09-27-expressive-redesign.md docs/superpowers/specs/2026-09-27-expressive-redesign-design.md
git commit -m "docs: the app shell draws with the Expressive theme"
```

### Task 3: Renders of every tab, before and after

A throwaway harness, never committed (the compose render harness in memory): `app/src/test/kotlin/dev/catsradar/app/zzdesign/`, one abstract base with the `@Test`s and two subclasses, `@GraphicsMode(NATIVE)`, `@Config(qualifiers = "w411dp-h891dp-xxhdpi")` and `"w411dp-h891dp-night-xxhdpi"`. It draws the Counter, the Encounters list, a cat's detail, Statistics, Settings and a Places level from sample states, inside `CatsRadarTheme { Surface(Modifier.size(411.dp, 860.dp)) { … } }`, and writes `onRoot().captureToImage()` to `app/build/design-shots/<variant>/`.

- [ ] **Step 1:** Keep the harness source in the scratchpad. Copy it in and take the *before* renders ahead of Task 1 Step 3, while the theme is still main's; take the *after* renders once Task 1 is committed. Copy each set out of `app/build/` to the scratchpad straight away.
- [ ] **Step 2:** Tile before and after with PIL, look at every pair, list every visible difference for the PR.
- [ ] **Step 3:** Remove the harness from the tree before the gate; `git status --porcelain` is empty.

### Task 4: The gate

- [ ] **Step 1:** Detached, as the gate outlives `ctx_execute`: `nohup sh -c 'CI=true ./gradlew check :app:assembleRelease --console=plain > <scratchpad>/e1-gate.log 2>&1; echo $? > <scratchpad>/e1-gate.rc' >/dev/null 2>&1 &`, then wait on the rc file with a background Bash loop.
- [ ] **Step 2:** `rc` is 0; the JUnit XML of `:app:testDebugUnitTest` has zero failures and the new class's three tests.
