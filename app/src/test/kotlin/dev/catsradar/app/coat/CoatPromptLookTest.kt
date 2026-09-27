package dev.catsradar.app.coat

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
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
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class CoatPromptLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

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
}
