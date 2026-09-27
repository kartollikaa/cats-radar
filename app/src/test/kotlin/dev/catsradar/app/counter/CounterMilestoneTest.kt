package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.isWhole
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CountNumberTestTag
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.MilestoneArcTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Config(qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    private fun walkButton() = compose.onNodeWithText(context.getString(R.string.counter_walk))

    private fun coats() = compose.onNodeWithText(context.getString(R.string.coat_ginger))

    private fun goalTag() = compose.onNodeWithContentDescription("38 more to reach 100")

    private fun ring() = compose.onNodeWithTag(MilestoneArcTestTag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun block() = compose.onNodeWithContentDescription("62").getUnclippedBoundsInRoot()

    private val outing = CurrentOutingState(count = 4, elapsedLabel = "35 min", rate = null)

    // The ring's stroke is this share of its diameter; a tag is pinned to the stroke's centre line.
    private val halfStroke = 0.026f / 2

    private fun nodesBetweenCountAndCoats(): List<SemanticsNode> {
        val top = with(compose.density) { block().bottom.toPx() }
        val bottom = with(compose.density) { coats().getUnclippedBoundsInRoot().top.toPx() }
        fun under(node: SemanticsNode): List<SemanticsNode> = node.children.flatMap(::under) +
            listOfNotNull(node.takeIf { it.boundsInRoot.top >= top && it.boundsInRoot.bottom <= bottom })
        return under(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    @Test
    fun `the goal is pinned where the ring closes, and TalkBack says how many more reach it`() {
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred)) } }

        goalTag().assertIsDisplayed()
        compose.onNodeWithText("100", useUnmergedTree = true).assertIsDisplayed()
        val tag = goalTag().getUnclippedBoundsInRoot()
        val ring = ring()
        assertEquals((ring.top + ring.width * halfStroke).value, ((tag.top + tag.bottom) / 2).value, 1f)
    }

    // The room the Counter gets on a 411 × 891 phone once the status bar and the tab bar take theirs.
    @Test
    @Config(qualifiers = "w411dp-h760dp")
    fun `with Undo showing, a long outing tag keeps clear of it and stays centred`() {
        val longOuting = CurrentOutingState(
            count = 14,
            elapsedLabel = "1 h 35 min",
            rate = RateState(value = "12.5", unit = RateUnit.PER_HOUR),
        )
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = counter(sixtyTwoOfHundred).copy(
                        currentOuting = longOuting,
                        undoVisible = true,
                        tapBurst = 1,
                    ),
                )
            }
        }

        val undo = compose.onNodeWithText(context.getString(R.string.counter_undo)).getUnclippedBoundsInRoot()
        val tag = compose.onNodeWithText("1 h 35 min", substring = true).getUnclippedBoundsInRoot()
        val block = block()
        assertTrue(tag.right <= undo.left, "the tag $tag runs under Undo $undo")
        assertEquals(((block.left + block.right) / 2).value, ((tag.left + tag.right) / 2).value, 1f)
    }

    @Test
    @Config(qualifiers = "w411dp-h760dp", fontScale = 1.5f)
    fun `at a large font too, the outing tag keeps clear of Undo`() {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = counter(sixtyTwoOfHundred).copy(currentOuting = outing, undoVisible = true, tapBurst = 1),
                )
            }
        }

        val undo = compose.onNodeWithText(context.getString(R.string.counter_undo)).getUnclippedBoundsInRoot()
        val tag = compose.onNodeWithText("35 min", substring = true).getUnclippedBoundsInRoot()
        val block = block()
        assertTrue(tag.right <= undo.left, "the tag $tag runs under Undo $undo")
        assertEquals(((block.left + block.right) / 2).value, ((tag.left + tag.right) / 2).value, 1f)
        assertTrue(compose.isWhole("35 min"), "the outing's time was squeezed out of its tag $tag")
    }

    @Test
    fun `during an outing its tag sits at the ring's bottom, and the goal stays`() {
        compose.setContent {
            CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred).copy(currentOuting = outing)) }
        }

        goalTag().assertIsDisplayed()
        val tag = compose.onNodeWithText("35 min").getUnclippedBoundsInRoot()
        val ring = ring()
        assertEquals((ring.bottom - ring.width * halfStroke).value, ((tag.top + tag.bottom) / 2).value, 1f)
    }

    @Test
    fun `nothing sits between the count and the coats, with or without an outing`() {
        var current by mutableStateOf<CurrentOutingState?>(null)
        compose.setContent {
            CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred).copy(currentOuting = current)) }
        }

        val coatsTop = coats().getUnclippedBoundsInRoot().top
        assertEquals(emptyList(), nodesBetweenCountAndCoats())
        current = outing
        assertEquals(emptyList(), nodesBetweenCountAndCoats())
        assertEquals(coatsTop, coats().getUnclippedBoundsInRoot().top)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 1.5f)
    fun `in a cramped block the number keeps clear of the tags on the ring, and the caption gives way`() {
        compose.setContent {
            CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred).copy(currentOuting = outing)) }
        }

        val number = compose.onNodeWithTag(CountNumberTestTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val goal = goalTag().getUnclippedBoundsInRoot()
        val outingTag = compose.onNodeWithText("35 min").getUnclippedBoundsInRoot()
        assertTrue(goal.bottom <= number.top, "the goal tag ends at ${goal.bottom}, the number starts at ${number.top}")
        assertTrue(
            number.bottom <= outingTag.top,
            "the number ends at ${number.bottom}, the outing starts at ${outingTag.top}"
        )
        compose.onNodeWithText("cats", useUnmergedTree = true).assertIsNotDisplayed()
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
    fun `the block still reads to TalkBack as the total alone, with Undo showing in it too`() {
        compose.setContent {
            CatsRadarTheme { CounterScreen(state = counter(sixtyTwoOfHundred).copy(undoVisible = true, tapBurst = 1)) }
        }

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
