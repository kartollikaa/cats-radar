package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.ImportProgressState
import dev.catsradar.presentation.counter.ImportSummaryState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.ImportCheckTestTag
import dev.catsradar.ui.counter.ImportThumbTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@Config(qualifiers = "w411dp-h760dp")
@RunWith(AndroidJUnit4::class)
class ImportIslandTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var state by mutableStateOf(CounterState(totalLabel = "147", count = 147, undoVisible = false))
    private var undos = 0
    private var dismissals = 0
    private var tallyUndos = 0

    private val mixed = ImportSummaryState(added = 9, skipped = 2, failed = 1, undoable = true)

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = state,
                    onUndoClick = { tallyUndos++ },
                    onUndoImportClick = { undos++ },
                    onImportSummaryDismiss = { dismissals++ },
                )
            }
        }
    }

    private fun plural(id: Int, count: Int) = context.resources.getQuantityString(id, count, count)

    private fun added() = compose.onNodeWithText(plural(R.plurals.counter_import_added, 9))

    private fun countHeight() = compose.onNodeWithContentDescription("147").getUnclippedBoundsInRoot()

    @Test
    fun `the count keeps its size under a running import's card and a finished one's`() {
        show()
        val alone = countHeight()

        state = state.copy(importProgress = ImportProgressState(done = 7, total = 12))
        compose.waitForIdle()
        assertEquals(alone, countHeight())
        state = state.copy(importProgress = null, importSummary = mixed)
        compose.waitForIdle()
        assertEquals(alone, countHeight())
    }

    @Test
    fun `a running import says how far it got, with nothing to press`() {
        state = state.copy(importProgress = ImportProgressState(done = 7, total = 12))
        show()

        compose.onNodeWithText(context.getString(R.string.counter_import_running_count, 7, 12)).assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(7f / 12, 0f..1f))).assertExists()
        compose.onAllNodesWithText(context.getString(R.string.counter_undo)).assertCountEquals(0)
        allDescribedAs(R.string.counter_import_close).assertCountEquals(0)
    }

    @Test
    fun `a finished import says what it added, and in one line only the rest that applies`() {
        val skipped = plural(R.plurals.counter_import_skipped, 2)
        val failed = plural(R.plurals.counter_import_failed, 1)
        state = state.copy(importSummary = mixed)
        show()

        added().assertIsDisplayed()
        val both = context.getString(R.string.counter_import_parts, skipped, failed)
        compose.onNodeWithText(both, useUnmergedTree = true).assertIsDisplayed()
        state = state.copy(importSummary = mixed.copy(skipped = null))
        compose.waitForIdle()
        compose.onNodeWithText(failed, useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText(skipped, substring = true, useUnmergedTree = true).assertCountEquals(0)
        state = state.copy(importSummary = mixed.copy(skipped = null, failed = null))
        compose.waitForIdle()
        compose.onAllNodesWithText(failed, substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `Undo takes the batch back and × closes, and neither touches the tally's Undo`() {
        state = state.copy(undoVisible = true, importSummary = mixed)
        show()

        compose.onNodeWithContentDescription(context.getString(R.string.counter_import_undo)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.counter_import_close)).performClick()

        assertEquals(Triple(1, 1, 0), Triple(undos, dismissals, tallyUndos))
    }

    @Test
    fun `an undone import offers no Undo and no OK, and × closes it`() {
        state = state.copy(importSummary = mixed.copy(undoable = false))
        show()

        allDescribedAs(R.string.counter_import_undo).assertCountEquals(0)
        compose.onAllNodesWithText("OK").assertCountEquals(0)
        compose.onNodeWithContentDescription(context.getString(R.string.counter_import_close)).performClick()

        assertEquals(0 to 1, undos to dismissals)
    }

    @Test
    fun `a swipe to either side closes a finished card`() {
        state = state.copy(importSummary = mixed)
        show()

        added().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, dismissals)

        state = state.copy(importSummary = null)
        compose.waitForIdle()
        state = state.copy(importSummary = mixed)
        compose.waitForIdle()
        added().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(2, dismissals)
    }

    @Test
    fun `the card's body does nothing when tapped`() {
        state = state.copy(importSummary = mixed)
        show()

        added().assertHasNoClickAction()
        added().performClick()

        assertEquals(Triple(0, 0, 0), Triple(undos, dismissals, tallyUndos))
    }

    @Test
    fun `a finished card shows up to three of the added cats' photos, and a check without any`() {
        val five = persistentListOf("/p/a.jpg", "/p/b.jpg", "/p/c.jpg", "/p/d.jpg", "/p/e.jpg")
        state = state.copy(importSummary = mixed.copy(thumbPaths = five))
        show()

        compose.onAllNodesWithTag(ImportThumbTestTag, useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithTag(ImportCheckTestTag, useUnmergedTree = true).assertCountEquals(0)
        state = state.copy(importSummary = mixed)
        compose.waitForIdle()
        compose.onAllNodesWithTag(ImportThumbTestTag, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag(ImportCheckTestTag, useUnmergedTree = true).assertCountEquals(1)
    }

    private fun allDescribedAs(id: Int) =
        compose.onAllNodes(hasContentDescription(context.getString(id)), useUnmergedTree = true)
}
