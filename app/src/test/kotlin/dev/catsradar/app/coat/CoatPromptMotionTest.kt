package dev.catsradar.app.coat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.ui.counter.CoatPromptPawTestTag
import dev.catsradar.ui.counter.CoatTrayTestTag
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The real sheet, whose top edge has to follow its content frame by frame for the photo beside the title to glide.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CoatPromptMotionTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val asking = CoatPromptState("cat", "cat", thumbPath = null)
    private var prompt by mutableStateOf(asking)

    @Test
    fun `switching to Several raises the sheet over several frames`() {
        show(asking)

        assertGlides(to = counting(0), up = true)
    }

    @Test
    fun `the first counted cat raises the sheet over several frames`() {
        show(counting(0))

        assertGlides(to = counting(1), up = true)
    }

    @Test
    fun `a cat that opens a row raises the sheet over several frames`() {
        show(counting(1))
        val opensRow = (2..CoatCountState.MOST_CATS).first { cats ->
            prompt = counting(cats)
            compose.waitForIdle()
            trayRows() > 1
        }
        prompt = counting(opensRow - 1)
        compose.waitForIdle()

        assertGlides(to = counting(opensRow), up = true)
    }

    @Test
    fun `going back to One cat lowers the sheet over several frames, the closing tray still showing its cats`() {
        show(counting(3))

        assertGlides(to = asking, up = false) { assertEquals(3, trayCats().size, "cats in the closing tray") }
    }

    private fun assertGlides(to: CoatPromptState, up: Boolean, whileMoving: () -> Unit = {}) {
        val before = photoTop()
        compose.mainClock.autoAdvance = false
        prompt = to
        compose.waitForIdle()
        val path = mutableListOf<Float>()
        repeat(FRAMES_TO_SETTLE) {
            compose.mainClock.advanceTimeByFrame()
            path += photoTop()
            if (path.last() != before && path.count { it != before } == 1) whileMoving()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val after = photoTop()

        assertTrue(if (up) after < before else after > before, "the photo went from $before to $after")
        val between = path.filter { it in minOf(before, after) + 1f..maxOf(before, after) - 1f }.distinct()
        assertTrue(between.size >= 2, "the photo passed through $between on its way from $before to $after")
    }

    private fun show(state: CoatPromptState) {
        prompt = state
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = false, coatPrompt = prompt),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun counting(cats: Int) = asking.copy(
        counting = CoatCountState(List(cats) { CoatOption.entries[it % CoatOption.entries.size] }.toImmutableList()),
    )

    private fun photoTop(): Float =
        compose.onNodeWithTag(CoatPromptPawTestTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    private fun trayCats() = compose.onAllNodes(hasAnyAncestor(hasTestTag(CoatTrayTestTag))).fetchSemanticsNodes()

    private fun trayRows(): Int = trayCats().map { it.boundsInRoot.top }.distinct().size

    private companion object {
        const val FRAMES_TO_SETTLE = 60
    }
}
