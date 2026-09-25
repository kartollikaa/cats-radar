package dev.catsradar.app.counter

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A phone-sized screen: on Robolectric's default one the Counter scrolls and the count sits at its floor.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class CounterControlsTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val requested = mutableListOf<Boolean>()
    private val haptics = mutableListOf<HapticFeedbackType>()
    private var undoVisible by mutableStateOf(false)

    @Test
    fun `the walk button sits centred under the count, at least half its width`() {
        show(walking = false)

        val count = compose.onNodeWithContentDescription("3").getUnclippedBoundsInRoot()
        val walk = walkButton(walking = false).getUnclippedBoundsInRoot()
        val coatGrid = compose.onNodeWithText(string(R.string.coat_ginger)).getUnclippedBoundsInRoot()

        assertTrue(walk.top >= count.bottom, "the walk button's top is ${walk.top}, the count's bottom ${count.bottom}")
        assertTrue(walk.bottom <= coatGrid.top, "walk bottom ${walk.bottom}, grid top ${coatGrid.top}")
        assertEquals(centre(count).value, centre(walk).value, absoluteTolerance = 1f)
        assertTrue(walk.width >= count.width / 2 - 1.dp, "the walk button is ${walk.width}, the count ${count.width}")
    }

    @Test
    fun `the walk button is as tall stopping a walk as starting one`() {
        var walking by mutableStateOf(false)
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = false, walkingMode = walking),
                )
            }
        }
        val starting = walkButton(walking = false).getUnclippedBoundsInRoot().height

        walking = true
        compose.waitForIdle()

        assertEquals(starting, walkButton(walking = true).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `a tap with no walk on starts one`() {
        show(walking = false)

        walkButton(walking = false).performClick()

        assertEquals(listOf(true), requested)
    }

    @Test
    fun `a tap during a walk does not stop it`() {
        show(walking = true)

        walkButton(walking = true).performClick()
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(emptyList<Boolean>(), requested)
    }

    @Test
    fun `a press let go before the hold is up does not stop the walk`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS - 200)
        walkButton(walking = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(emptyList<Boolean>(), requested)
    }

    @Test
    fun `a press held through the hold stops the walk once, before the finger lifts`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS + 100)
        val beforeLifting = requested.toList()
        walkButton(walking = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(listOf(false), beforeLifting)
        assertEquals(listOf(false), requested)
    }

    @Test
    fun `a tap after a finished hold does not stop the walk while it is still on`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false
        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS + 100)
        walkButton(walking = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(HOLD_MS)

        walkButton(walking = true).performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS)

        assertEquals(listOf(false), requested)
    }

    @Test
    fun `a held press ticks softly all the way through the hold, then confirms the stop once`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS / 2)
        val halfway = haptics.toList()
        compose.mainClock.advanceTimeBy(HOLD_MS / 2 + 100)
        val ticks = haptics.dropLast(1)

        assertTrue(halfway.isNotEmpty(), "no haptic halfway through the hold")
        assertTrue(halfway.all { it == HapticFeedbackType.SegmentFrequentTick }, "halfway: $halfway")
        assertTrue(ticks.all { it == HapticFeedbackType.SegmentFrequentTick }, "before the stop: $ticks")
        assertTrue(ticks.size > halfway.size, "${ticks.size} ticks by the stop, $halfway halfway")
        assertEquals(HapticFeedbackType.Confirm, haptics.last())
    }

    @Test
    fun `a press let go early stops ticking and never confirms`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS / 2)
        walkButton(walking = true).performTouchInput { up() }
        val atRelease = haptics.toList()
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertTrue(atRelease.isNotEmpty(), "no haptic before the release")
        assertEquals(atRelease, haptics)
    }

    @Test
    fun `during a walk the button says it has to be held`() {
        show(walking = true)

        compose.onNodeWithText(string(R.string.counter_walk_stop_hint)).assertIsDisplayed()
    }

    @Test
    fun `during a walk the button shows how long it has lasted, and still that it has to be held`() {
        show(walking = true, elapsedLabel = "32 min")

        compose.onNodeWithText(context.getString(R.string.counter_walk_stop_hint_timed, "32 min")).assertIsDisplayed()
    }

    @Config(qualifiers = "+ru")
    @Test
    fun `in Russian the walk's time keeps a hint short enough to share the line`() {
        show(walking = true, elapsedLabel = "32 мин")

        compose.onNodeWithText("32 мин · удерживайте").assertIsDisplayed()
    }

    @Test
    fun `an accessibility click stops the walk without the hold`() {
        show(walking = true)

        walkButton(walking = true).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(false), requested)
    }

    @Test
    fun `Undo appearing and going leaves the count the same size`() {
        showUndoHidden()
        val hidden = countHeight()

        undoVisible = true
        compose.waitForIdle()
        val shown = countHeight()
        undoVisible = false
        compose.waitForIdle()

        assertEquals(hidden, shown)
        assertEquals(hidden, countHeight())
    }

    private fun show(walking: Boolean, elapsedLabel: String? = null) {
        val recorder = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                haptics += hapticFeedbackType
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                CatsRadarTheme {
                    CounterScreen(
                        state = CounterState(
                            totalLabel = "3",
                            count = 3,
                            undoVisible = false,
                            walkingMode = walking,
                            walkElapsedLabel = elapsedLabel,
                        ),
                        onWalkingModeChange = { requested += it },
                    )
                }
            }
        }
    }

    private fun showUndoHidden() {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(state = CounterState(totalLabel = "3", count = 3, undoVisible = undoVisible))
            }
        }
    }

    private fun walkButton(walking: Boolean): SemanticsNodeInteraction =
        compose.onNodeWithText(string(if (walking) R.string.counter_walk_stop else R.string.counter_walk_start))

    private fun countHeight() = compose.onNodeWithContentDescription("3").getUnclippedBoundsInRoot().height

    private fun string(@StringRes id: Int) = context.getString(id)

    private fun centre(bounds: DpRect) = (bounds.left + bounds.right) / 2

    private companion object {
        const val HOLD_MS = 1_000L
    }
}
