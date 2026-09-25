package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.ImportProgressState
import dev.catsradar.presentation.counter.ImportSummaryState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.LocationHintAction
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CounterNoticesTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var undos = 0
    private var dismissals = 0
    private var tallyUndos = 0
    private val hintActions = mutableListOf<LocationHintAction>()

    @Test
    fun `a running import names itself and how far it got, read as one notice`() {
        show(counter.copy(importProgress = ImportProgressState(done = 7, total = 23)))

        compose.onNodeWithText(context.getString(R.string.counter_import_running))
            .assertTextContains(context.getString(R.string.counter_import_progress, 7, 23))
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(7f / 23, 0f..1f))).assertExists()
    }

    @Test
    fun `a finished import says what it added, read as one notice, and offers its own Undo`() {
        val mixed = ImportSummaryState(added = 9, skipped = 3, failed = 1, undoable = true)
        show(counter.copy(undoVisible = true, importSummary = mixed))
        val added = plural(R.plurals.counter_import_added, 9)

        compose.onNodeWithText(added)
            .assertTextContains(plural(R.plurals.counter_import_skipped, 3))
            .assertTextContains(plural(R.plurals.counter_import_failed, 1))
        val importUndo = hasText(context.getString(R.string.counter_undo)) and hasAnySibling(hasText(added))
        compose.onNode(importUndo).performClick()

        assertEquals(Triple(1, 0, 0), Triple(undos, dismissals, tallyUndos))
    }

    @Test
    fun `an undone import offers OK, which dismisses`() {
        val undone = ImportSummaryState(added = 9, skipped = null, failed = null, undoable = false)
        show(counter.copy(importSummary = undone))

        compose.onNodeWithText(context.getString(R.string.counter_import_ok)).performClick()

        assertEquals(0 to 1, undos to dismissals)
    }

    @Test
    fun `the location hint offers Grant and Dismiss`() {
        show(counter.copy(locationPermissionHintVisible = true))

        compose.onNodeWithText(context.getString(R.string.counter_location_hint)).assertHasNoClickAction()
        compose.onNodeWithText(context.getString(R.string.counter_location_grant)).performClick()
        compose.onNodeWithText(context.getString(R.string.counter_location_dismiss)).performClick()

        assertEquals(listOf(LocationHintAction.GRANT, LocationHintAction.DISMISS), hintActions)
    }

    private fun plural(id: Int, count: Int) = context.resources.getQuantityString(id, count, count)

    private fun show(state: CounterState) {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = state,
                    onUndoClick = { tallyUndos++ },
                    onLocationHintAction = { hintActions += it },
                    onUndoImportClick = { undos++ },
                    onImportSummaryDismiss = { dismissals++ },
                )
            }
        }
    }

    private val counter = CounterState(totalLabel = "147", count = 147, undoVisible = false)
}
