package dev.catsradar.app.coat

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.ui.coat.CoatShapeTestTag
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class CoatGridLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun counter(lastCoat: CoatOption? = null) =
        CounterState(totalLabel = "3", count = 3, undoVisible = false, lastCoat = lastCoat)

    // The grid draws the coats in their own order, so a coat's ordinal is its shape's place in the tree.
    private fun shapeOf(coat: CoatOption) =
        compose.onAllNodesWithTag(CoatShapeTestTag, useUnmergedTree = true)[coat.ordinal]

    private fun SemanticsNodeInteraction.pixelFromStart(inset: Dp): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[with(compose.density) { inset.roundToPx() }, pixels.height / 2]
    }

    @Test
    fun `every coat sits in a 52 dp shape`() {
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter()) } }

        val sizes = compose.onAllNodesWithTag(CoatShapeTestTag, useUnmergedTree = true).fetchSemanticsNodes()
            .map { with(compose.density) { it.size.width.toDp() to it.size.height.toDp() } }
        assertEquals(List(CoatOption.entries.size) { 52.dp to 52.dp }, sizes)
    }

    @Test
    fun `the ringed coat's shape takes the primary container and a ring, the rest the highest container`() {
        lateinit var scheme: ColorScheme
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                CounterScreen(state = counter(lastCoat = CoatOption.GINGER))
            }
        }

        assertEquals(scheme.primaryContainer, shapeOf(CoatOption.GINGER).pixelFromStart(4.dp))
        assertEquals(scheme.primary, shapeOf(CoatOption.GINGER).pixelFromStart(1.dp))
        assertEquals(scheme.surfaceContainerHighest, shapeOf(CoatOption.GINGER_WHITE).pixelFromStart(4.dp))
    }

    private fun gridHeight(): Dp {
        compose.setContent { CatsRadarTheme { CounterScreen(state = counter()) } }
        val cells = CoatOption.entries.map {
            compose.onNodeWithText(context.getString(it.labelRes())).getUnclippedBoundsInRoot()
        }
        return cells.maxOf { it.bottom } - cells.minOf { it.top }
    }

    @Test
    fun `the grid is no taller than it was`() {
        val height = gridHeight()
        assertTrue(height <= GridHeightBefore, "the grid is $height tall, it was $GridHeightBefore")
    }

    @Test
    @Config(fontScale = 1.5f)
    fun `at a large font the grid is no taller than it was`() {
        val height = gridHeight()
        assertTrue(height <= LargeFontGridHeightBefore, "the grid is $height tall, it was $LargeFontGridHeightBefore")
    }

    private companion object {
        // Measured on the Counter at the branch base, 411 dp wide, at the default font and at 1.5.
        val GridHeightBefore = 282.dp
        val LargeFontGridHeightBefore = 362.dp
    }
}
