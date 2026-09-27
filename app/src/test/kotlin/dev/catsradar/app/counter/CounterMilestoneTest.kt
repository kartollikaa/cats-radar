package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.MilestoneArcTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class CounterMilestoneTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val sixtyTwoOfHundred =
        CounterMilestoneState(MilestoneState(valueLabel = "100", remainingLabel = "38"), fraction = 0.24f)

    private fun counter(milestone: CounterMilestoneState?) =
        CounterState(totalLabel = "62", count = 62, undoVisible = false, milestone = milestone)

    private fun walkButton() = compose.onNodeWithText(context.getString(R.string.counter_walk_start))

    @Test
    fun `the count says how many more reach the next milestone`() {
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred)) } }

        compose.onNodeWithText("38 more to reach 100").assertIsDisplayed()
    }

    @Test
    fun `during an outing its line takes the milestone's place`() {
        val outing = CurrentOutingState(count = 4, elapsedLabel = "35 min", rate = null)
        compose.setContent {
            CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred).copy(currentOuting = outing)) }
        }

        compose.onNodeWithText("38 more to reach 100").assertDoesNotExist()
        compose.onNodeWithText("35 min").assertIsDisplayed()
    }

    @Test
    fun `the arc is drawn while there is a milestone to reach, and not past the last one`() {
        var milestone by mutableStateOf<CounterMilestoneState?>(sixtyTwoOfHundred)
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter(milestone)) } }

        compose.onNodeWithTag(MilestoneArcTestTag, useUnmergedTree = true).assertExists()
        milestone = null
        compose.onNodeWithTag(MilestoneArcTestTag, useUnmergedTree = true).assertDoesNotExist()
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
        compose.setContent {
            CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred), onTallyClick = { taps++ }) }
        }

        compose.onNodeWithContentDescription("62").performTouchInput { click(Offset(8f, 8f)) }

        assertEquals(1, taps)
    }

    @Test
    fun `the block still reads to TalkBack as the total alone`() {
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred)) } }

        val block = compose.onNodeWithContentDescription("62").fetchSemanticsNode().config
        assertEquals(listOf("62"), block.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(emptyList(), block.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
    }

    @Test
    fun `under the number the count names what it counts`() {
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred)) } }

        compose.onNodeWithText("cats", useUnmergedTree = true).assertIsDisplayed()
    }
}
