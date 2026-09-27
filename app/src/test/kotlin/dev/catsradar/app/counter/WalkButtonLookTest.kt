package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.app.testing.isWhole
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
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class WalkButtonLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var walking by mutableStateOf(false)
    private lateinit var colors: ColorScheme

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                colors = MaterialTheme.colorScheme
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = false, walkingMode = walking),
                )
            }
        }
    }

    private fun walkButton(): SemanticsNodeInteraction =
        compose.onNodeWithText(context.getString(if (walking) R.string.counter_walk_hold else R.string.counter_walk))

    private fun photo() = compose.onNodeWithText(context.getString(R.string.counter_camera))

    // Clear of the cat past the side padding; at the top corner, inside a 16 dp curve and outside a pill's.
    private fun SemanticsNodeInteraction.pixelNearStart(atTopCorner: Boolean): Color {
        val pixels = captureToImage().toPixelMap()
        val inset = with(compose.density) { 6.dp.roundToPx() }
        return pixels[inset, if (atTopCorner) inset else pixels.height / 2]
    }

    private fun assertNoOldHint() {
        compose.onAllNodesWithText("press and hold", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("tap to begin", substring = true).assertCountEquals(0)
    }

    @Test
    fun `Walk stands at the start of Photo's row, as tall as the split button`() {
        show()
        val block = compose.onNodeWithContentDescription("3").getUnclippedBoundsInRoot()

        for (state in listOf(false, true)) {
            walking = state
            compose.waitForIdle()
            val walk = walkButton().getUnclippedBoundsInRoot()
            val camera = photo().getUnclippedBoundsInRoot()
            assertEquals(block.left.value, walk.left.value, 1f)
            assertEquals(56.dp, walk.height)
            assertEquals(walk.top, camera.top)
            assertEquals((walk.right + 8.dp).value, camera.left.value, 1f)
        }
    }

    @Test
    fun `at rest the button wears the secondary container and reads Walk`() {
        show()

        assertEquals(colors.secondaryContainer, walkButton().pixelNearStart(atTopCorner = false))
        walkButton().assertIsDisplayed()
        assertNoOldHint()
    }

    @Test
    fun `during a walk it wears the tertiary container, squarer corners, and reads Hold to end`() {
        walking = true
        show()

        assertEquals(colors.tertiaryContainer, walkButton().pixelNearStart(atTopCorner = false))
        assertEquals(colors.tertiaryContainer, walkButton().pixelNearStart(atTopCorner = true))
        walkButton().assertIsDisplayed()
        assertNoOldHint()
    }

    @Config(fontScale = 1.5f)
    @Test
    fun `at a large font both labels stay whole`() {
        walking = true
        show()

        assertTrue(compose.isWhole(context.getString(R.string.counter_walk_hold)))
        assertTrue(compose.isWhole(context.getString(R.string.counter_camera)))
    }
}
