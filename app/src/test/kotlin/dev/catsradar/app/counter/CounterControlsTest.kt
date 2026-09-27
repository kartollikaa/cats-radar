package dev.catsradar.app.counter

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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
    private var holdsReleased = 0

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
        assertEquals(0, holdsReleased)
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
        val perFrame = List(HOLD_FRAMES + 10) {
            val before = haptics.size
            compose.mainClock.advanceTimeByFrame()
            haptics.drop(before)
        }
        val tickFrames = perFrame.indices.filter { HapticFeedbackType.SegmentFrequentTick in perFrame[it] }
        val confirmFrames = perFrame.indices.filter { HapticFeedbackType.Confirm in perFrame[it] }

        assertEquals(1, confirmFrames.size, "Confirm on frames $confirmFrames")
        val confirmFrame = confirmFrames.single()
        assertEquals(listOf(HapticFeedbackType.Confirm), perFrame[confirmFrame])
        assertTrue(tickFrames.all { it < confirmFrame }, "ticks $tickFrames, Confirm $confirmFrame")
        assertTrue(tickFrames.first() < HOLD_FRAMES / 5, "first tick on frame ${tickFrames.first()}")
        assertTrue(tickFrames.last() > HOLD_FRAMES * 4 / 5, "last tick on frame ${tickFrames.last()}")
        assertTrue(tickFrames.zipWithNext().all { (a, b) -> b - a > 1 }, "ticks on neighbouring frames: $tickFrames")
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

        compose.onNodeWithText(string(R.string.counter_walk_hold)).assertIsDisplayed()
    }

    @Test
    fun `a press let go before the hold is up says it has to be held`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS - 200)
        walkButton(walking = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(1, holdsReleased)
        assertEquals(emptyList<Boolean>(), requested)
    }

    @Test
    fun `a press that drifts off the button stops nothing and raises no hint`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, -height * 4f))
        }
        compose.mainClock.advanceTimeBy(HOLD_MS - 200)
        walkButton(walking = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(0, holdsReleased)
        assertEquals(emptyList<Boolean>(), requested)
    }

    @Test
    fun `an accessibility click stops the walk without the hold`() {
        show(walking = true)

        walkButton(walking = true).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(false), requested)
    }

    @Test
    fun `Undo sits in the cookie's bottom-end corner and a tap on it takes a cat back, never logs one`() {
        var undos = 0
        var tallies = 0
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = true),
                    onUndoClick = { undos++ },
                    onTallyClick = { tallies++ },
                )
            }
        }
        val block = compose.onNodeWithContentDescription("3").getUnclippedBoundsInRoot()
        val side = minOf(block.width, block.height)
        val cookieRight = (block.left + block.right) / 2 + side / 2
        val cookieBottom = (block.top + block.bottom) / 2 + side / 2
        val undo = compose.onNodeWithText(string(R.string.counter_undo))

        val bounds = undo.getUnclippedBoundsInRoot()
        assertEquals(cookieRight.value, bounds.right.value, 1f)
        assertEquals(cookieBottom.value, bounds.bottom.value, 1f)
        undo.performClick()
        assertEquals(1, undos)
        assertEquals(0, tallies)
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

    private fun show(walking: Boolean) {
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
                        ),
                        onWalkingModeChange = { requested += it },
                        onWalkHoldRelease = { holdsReleased++ },
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
        compose.onNodeWithText(string(if (walking) R.string.counter_walk_hold else R.string.counter_walk))

    private fun countHeight() = compose.onNodeWithContentDescription("3").getUnclippedBoundsInRoot().height

    private fun string(@StringRes id: Int) = context.getString(id)

    private companion object {
        const val HOLD_MS = 1_000L
        const val HOLD_FRAMES = (HOLD_MS / 16).toInt()
    }
}
