package dev.catsradar.app.coat

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CoatPrompt
import dev.catsradar.ui.counter.CoatTrayFaceTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
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
class CoatPromptLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `every counted cat sits on a filled coat shape`() {
        lateinit var scheme: ColorScheme
        val tray = persistentListOf(CoatOption.BLACK, null, CoatOption.GINGER)
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                CoatPrompt(prompt = CoatPromptState("cat", "cat", thumbPath = null, counting = CoatCountState(tray)))
            }
        }

        val faces = compose.onAllNodesWithTag(CoatTrayFaceTestTag, useUnmergedTree = true)
        faces.fetchSemanticsNodes().indices.forEach { index ->
            val pixels = faces[index].captureToImage().toPixelMap()
            val filled = (0 until pixels.width).sumOf { x ->
                (0 until pixels.height).count { y -> pixels[x, y] == scheme.surfaceContainerHighest }
            }
            assertTrue(filled * 10 > pixels.width * pixels.height, "tray cat $index is ${filled}px filled")
        }
        assertTrue(faces.fetchSemanticsNodes().size == tray.size)
    }

    @Test
    fun `the chosen mode is tonal, so Save is the one filled button`() {
        lateinit var scheme: ColorScheme
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                CoatPrompt(
                    prompt = CoatPromptState(
                        catId = "cat",
                        photoId = "cat",
                        thumbPath = null,
                        counting = CoatCountState(persistentListOf(CoatOption.GINGER)),
                    ),
                )
            }
        }
        val several = context.getString(R.string.counter_coat_prompt_several)
        val save = context.resources.getQuantityString(R.plurals.counter_coat_count_save, 1, 1)

        assertEquals(scheme.secondaryContainer, compose.onNodeWithText(several).pixelInsideStart())
        assertEquals(scheme.primary, compose.onNodeWithText(save).pixelInsideStart())
    }

    private fun SemanticsNodeInteraction.pixelInsideStart(): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[with(compose.density) { 10.dp.roundToPx() }, pixels.height / 2]
    }
}
