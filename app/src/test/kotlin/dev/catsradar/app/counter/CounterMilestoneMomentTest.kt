package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.counter.MilestoneMomentState
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CountNumberTestTag
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.RungRingTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@Config(qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class CounterMilestoneMomentTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val hundred = MilestoneMomentState(value = 100)
    private val toTwoFifty =
        CounterMilestoneState(MilestoneState(valueLabel = "250", remainingLabel = "150"), fraction = 0f)
    private val outing = CurrentOutingState(count = 4, elapsedLabel = "35 min", rate = null)
    private val atRest = CounterState(totalLabel = "100", count = 100, undoVisible = true, milestone = toTwoFifty)
    private var state by mutableStateOf(atRest)
    private lateinit var scheme: ColorScheme

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                CounterScreen(state = state)
            }
        }
    }

    private val cheer get() = context.resources.getQuantityString(R.plurals.counter_milestone, 100, 100)

    private fun number() = compose.onNodeWithTag(CountNumberTestTag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun `the moment's pill names the rung and TalkBack hears it politely`() {
        state = atRest.copy(milestoneMoment = hundred)
        show()

        compose.onNodeWithText(cheer)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `the goal tag steps aside for the moment, and comes back after it`() {
        state = atRest.copy(milestoneMoment = hundred)
        show()
        compose.onNodeWithContentDescription("150 more to reach 250").assertDoesNotExist()

        state = atRest
        compose.waitForIdle()

        compose.onNodeWithContentDescription("150 more to reach 250").assertExists()
    }

    @Test
    fun `the outing's tag gives its place to the moment`() {
        state = atRest.copy(currentOuting = outing, milestoneMoment = hundred)
        show()

        compose.onNodeWithText("35 min", substring = true).assertDoesNotExist()
        compose.onNodeWithText(cheer, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the number keeps its size through the moment, with an outing and without`() {
        show()
        val alone = number()
        state = atRest.copy(milestoneMoment = hundred)
        compose.waitForIdle()
        val celebrating = number()
        state = atRest.copy(currentOuting = outing)
        compose.waitForIdle()
        val onAnOuting = number()
        state = atRest.copy(currentOuting = outing, milestoneMoment = hundred)
        compose.waitForIdle()

        assertEquals(alone to onAnOuting, celebrating to number())
    }

    @Test
    fun `a moment that ends mid-bounce lets the cookie settle back to its size`() {
        show()
        val resting = number()
        compose.mainClock.autoAdvance = false
        state = atRest.copy(milestoneMoment = hundred)
        compose.mainClock.advanceTimeBy(120)

        state = atRest
        compose.mainClock.advanceTimeBy(3_000)

        assertEquals(resting, number())
    }

    @Test
    fun `the ring stands full for the moment`() {
        state = atRest.copy(milestoneMoment = hundred)
        show()
        compose.mainClock.advanceTimeBy(2_000)

        val pixels = compose.onNodeWithTag(RungRingTestTag, useUnmergedTree = true).captureToImage().toPixelMap()
        val halfStroke = (pixels.width * 0.026f / 2).toInt().coerceAtLeast(1)

        assertEquals(scheme.primary, pixels[pixels.width - halfStroke, pixels.height / 2])
    }
}
