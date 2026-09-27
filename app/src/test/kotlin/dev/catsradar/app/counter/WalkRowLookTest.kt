package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class WalkRowLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun counter(walking: Boolean) =
        CounterState(totalLabel = "3", count = 3, undoVisible = false, walkingMode = walking)

    private fun walkButton(walking: Boolean): SemanticsNodeInteraction =
        compose.onNodeWithText(context.getString(if (walking) R.string.counter_walk_stop else R.string.counter_walk_start))

    // Inside the button near its start edge, clear of the cat, the words and the fill.
    private fun SemanticsNodeInteraction.edgePixel(): Color {
        val pixels = captureToImage().toPixelMap()
        val inset = with(compose.density) { 6.dp.roundToPx() }
        return pixels[inset, pixels.height / 2]
    }

    // Inside a 16 dp corner's curve, outside a pill's.
    private fun SemanticsNodeInteraction.cornerPixel(): Color {
        val pixels = captureToImage().toPixelMap()
        val inset = with(compose.density) { 6.dp.roundToPx() }
        return pixels[inset, inset]
    }

    @Test
    fun `the walk button is 56 dp tall, starting and stopping alike`() {
        var walking by mutableStateOf(false)
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter(walking)) } }

        assertEquals(56.dp, walkButton(walking = false).getUnclippedBoundsInRoot().height)
        walking = true
        assertEquals(56.dp, walkButton(walking = true).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `the walk button wears the tertiary container whether or not a walk is on`() {
        var walking by mutableStateOf(false)
        var expected = Color.Unspecified
        compose.setContent {
            CatsRadarTheme {
                expected = MaterialTheme.colorScheme.tertiaryContainer
                CounterScreen(state = counter(walking))
            }
        }

        assertEquals(expected, walkButton(walking = false).edgePixel())
        walking = true
        assertEquals(expected, walkButton(walking = true).edgePixel())
    }

    @Test
    fun `the walk button has the extended FAB's corners, not a pill's`() {
        var expected = Color.Unspecified
        compose.setContent {
            CatsRadarTheme {
                expected = MaterialTheme.colorScheme.tertiaryContainer
                CounterScreen(state = counter(walking = false))
            }
        }

        assertEquals(expected, walkButton(walking = false).cornerPixel())
    }

    @Test
    fun `Undo stands as tall as a button`() {
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter(walking = false).copy(undoVisible = true)) } }

        val undo = compose.onNodeWithText(context.getString(R.string.counter_undo))
        assertEquals(40.dp, undo.getUnclippedBoundsInRoot().height)
    }
}
