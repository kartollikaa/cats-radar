package dev.catsradar.app.counter

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.ImportProgressState
import dev.catsradar.presentation.counter.ImportSummaryState
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.ImportThumbTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h760dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class ImportIslandLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private var state by mutableStateOf(CounterState(totalLabel = "147", count = 147, undoVisible = false))
    private lateinit var colors: ColorScheme

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                colors = MaterialTheme.colorScheme
                CounterScreen(state = state)
            }
        }
    }

    // The photos cannot load here and show their placeholder, so across the second photo's left edge, which lies
    // over the first photo, the row meets the card's colour only in the second photo's ring.
    private fun ringWhereTheSecondPhotoCoversTheFirst(): Boolean {
        val second = compose.onAllNodesWithTag(ImportThumbTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes()[1]
            .boundsInRoot.center
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val (halfSide, reach) = with(compose.density) { 20.dp.toPx() to 4.dp.toPx() }
        val edge = second.x - halfSide
        val y = second.y.roundToInt()
        return ((edge - reach).roundToInt()..(edge + reach).roundToInt()).any { x ->
            pixels[x, y] == colors.surfaceContainerHigh
        }
    }

    @Test
    fun `a running card's photos are set apart by a ring in the card's colour`() {
        state = state.copy(
            importProgress = ImportProgressState(
                done = 1,
                total = 12,
                previewUris = persistentListOf("content://a", "content://b"),
            ),
        )
        show()

        assertTrue(ringWhereTheSecondPhotoCoversTheFirst())
    }

    @Test
    fun `a finished card's photos are set apart by a ring in the card's colour`() {
        state = state.copy(
            importSummary = ImportSummaryState(
                added = 9,
                skipped = null,
                failed = null,
                undoable = true,
                thumbPaths = persistentListOf("/p/a.jpg", "/p/b.jpg"),
            ),
        )
        show()

        assertTrue(ringWhereTheSecondPhotoCoversTheFirst())
    }
}
