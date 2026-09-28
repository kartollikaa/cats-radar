package dev.catsradar.app.counter

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.statistics.MilestoneState
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.TapBurstTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse

abstract class CounterBurstBadgeTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(
                        totalLabel = "62",
                        count = 62,
                        undoVisible = true,
                        tapBurst = 12,
                        milestone = CounterMilestoneState(MilestoneState("100", "38"), fraction = 0.24f),
                    ),
                )
            }
        }
    }

    private fun badge() = compose.onNodeWithTag(TapBurstTestTag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun goalTag() = compose.onNodeWithContentDescription("38 more to reach 100").getUnclippedBoundsInRoot()

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    @Test
    fun `the badge keeps clear of the goal tag`() {
        show()

        assertFalse(badge().overlaps(goalTag()), "${badge()} over ${goalTag()}")
    }

    @Test
    fun `the badge sits at the block's end, level with the cookie's top`() {
        show()
        val block = compose.onNodeWithContentDescription("62").getUnclippedBoundsInRoot()
        val side = minOf(block.width, block.height)
        val cookieTop = (block.top + block.bottom) / 2 - side / 2

        assertEquals(block.right.value, badge().right.value, 1f)
        assertEquals(cookieTop.value, badge().top.value, 1f)
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp")
@RunWith(AndroidJUnit4::class)
class CounterBurstBadgeNarrowTest : CounterBurstBadgeTest()

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp", fontScale = 1.3f)
@RunWith(AndroidJUnit4::class)
class CounterBurstBadgeNarrowLargerFontTest : CounterBurstBadgeTest()

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h640dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CounterBurstBadgeNarrowLargestFontTest : CounterBurstBadgeTest()

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CounterBurstBadgeMidLargestFontTest : CounterBurstBadgeTest()

// Tall enough that the cookie fills the block's width, so the block's end is the cookie's.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h900dp")
@RunWith(AndroidJUnit4::class)
class CounterBurstBadgeFullWidthTest : CounterBurstBadgeTest()
