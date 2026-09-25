package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.presentation.statistics.RateState
import dev.catsradar.presentation.statistics.RateUnit
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Config(qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class CurrentOutingLineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `an outing with a rate reads count, time, rate, with the same gap between each`() {
        show(withRate)

        val count = part(CATS)
        val elapsed = part(ELAPSED)
        val rate = part(RATE)

        assertTrue(count.right < elapsed.left && elapsed.right < rate.left, "the parts run count, time, rate")
        assertEquals(elapsed.left - count.right, rate.left - elapsed.right, 1f)
    }

    @Test
    fun `an outing with no rate yet is the count and the time, centred with nothing after them`() {
        show(withRate.copy(rate = null))
        val screenCentre = compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x

        val count = part(CATS)
        val elapsed = part(ELAPSED)

        assertEquals(screenCentre, (count.left + elapsed.right) / 2, with(compose.density) { 1.dp.toPx() })
        compose.onNodeWithText(RATE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `an outing opening leaves the walk button where it was and the count its size`() {
        var outing by mutableStateOf<CurrentOutingState?>(null)
        show { outing }
        val before = walkButtonTop() to countHeight()

        outing = withRate
        compose.waitForIdle()

        assertEquals(before, walkButtonTop() to countHeight())
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 1.75f)
    fun `short of room the count gives way first, and the time and the rate stay whole`() {
        show(longOuting)

        assertFalse(isWhole(CATS), "the count is cut short")
        assertTrue(isWhole(LONG_ELAPSED), "the time is whole")
        assertTrue(isWhole(FAST_RATE), "the rate is whole")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun `shorter still the time gives way too, and the rate stays whole`() {
        show(longOuting)

        assertFalse(isWhole(LONG_ELAPSED), "the time is cut short")
        assertTrue(isWhole(FAST_RATE), "the rate is whole")
    }

    @Test
    fun `talkback reads the whole line as one item`() {
        show(withRate)

        compose.onNode(hasText(CATS) and hasText(ELAPSED) and hasText(RATE)).assertExists()
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the count declines in russian`() {
        val cats = { n: Int -> context.resources.getQuantityString(R.plurals.counter_outing_cats, n, n) }

        assertEquals(listOf("1 котик", "3 котика", "5 котиков"), listOf(cats(1), cats(3), cats(5)))
    }

    private fun show(outing: CurrentOutingState) = show { outing }

    private fun show(outing: () -> CurrentOutingState?) {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "12", count = 12, undoVisible = false, currentOuting = outing()),
                )
            }
        }
    }

    private fun part(text: String): Rect =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    // didOverflowWidth misreads a text that does not wrap, so the text's own width is compared instead.
    private fun isWhole(text: String): Boolean {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        return layout.size.width >= layout.multiParagraph.intrinsics.maxIntrinsicWidth
    }

    private fun walkButtonTop(): Float =
        compose.onNodeWithText(context.getString(R.string.counter_walk_start), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top

    private fun countHeight(): Float =
        compose.onNodeWithContentDescription("12").fetchSemanticsNode().boundsInRoot.height

    private companion object {
        const val CATS = "4 cats"
        const val ELAPSED = "35 min"
        const val RATE = "6.9 / h"
        const val LONG_ELAPSED = "1 h 20 min"
        const val FAST_RATE = "12.5 / min"
        val withRate = CurrentOutingState(
            count = 4,
            elapsedLabel = ELAPSED,
            rate = RateState(value = "6.9", unit = RateUnit.PER_HOUR),
        )
        val longOuting = CurrentOutingState(
            count = 4,
            elapsedLabel = LONG_ELAPSED,
            rate = RateState(value = "12.5", unit = RateUnit.PER_MINUTE),
        )
    }
}
