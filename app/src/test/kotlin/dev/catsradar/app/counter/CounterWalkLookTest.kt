package dev.catsradar.app.counter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterMilestoneState
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.statistics.MilestoneState
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class CounterWalkLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private var walking by mutableStateOf(false)
    private lateinit var colors: androidx.compose.material3.ColorScheme

    private val milestone =
        CounterMilestoneState(MilestoneState(valueLabel = "100", remainingLabel = "38"), fraction = 0.24f)

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                colors = MaterialTheme.colorScheme
                CounterScreen(
                    state = CounterState(
                        totalLabel = "62",
                        count = 62,
                        undoVisible = false,
                        walkingMode = walking,
                        milestone = milestone,
                    ),
                )
            }
        }
    }

    // On the number's row, a third of the square's side from its centre: inside the ring, clear of the digits.
    private fun cookieFill(): Color {
        val pixels = compose.onNodeWithContentDescription("62").captureToImage().toPixelMap()
        val side = minOf(pixels.width, pixels.height)
        return pixels[pixels.width / 2 - side / 3, pixels.height / 2]
    }

    private fun ImageBitmap.holds(color: Color): Boolean {
        val pixels = toPixelMap()
        return (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y] == color } }
    }

    private fun number() = compose.onNodeWithTag(CountNumberTestTag, useUnmergedTree = true).captureToImage()

    private fun arc() = compose.onNodeWithTag(MilestoneArcTestTag, useUnmergedTree = true).captureToImage()

    @Test
    fun `during a walk the cookie's fill is the tertiary container, and the number wears its colour`() {
        show()
        assertEquals(colors.primaryContainer, cookieFill())
        assertTrue(number().holds(colors.onPrimaryContainer))

        walking = true
        compose.waitForIdle()

        assertEquals(colors.tertiaryContainer, cookieFill())
        val warmNumber = number()
        assertTrue(warmNumber.holds(colors.onTertiaryContainer))
        assertFalse(warmNumber.holds(colors.onPrimaryContainer))
    }

    @Test
    fun `the ring keeps its colour on the warm cookie`() {
        show()
        assertTrue(arc().holds(colors.primary))

        walking = true
        compose.waitForIdle()

        assertTrue(arc().holds(colors.primary))
    }
}
