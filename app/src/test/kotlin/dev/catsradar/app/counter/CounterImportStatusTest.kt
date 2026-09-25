package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
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
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CounterImportStatusTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var undos = 0
    private var dismissals = 0

    @Test
    fun `a running import names itself and how far it got`() {
        show(counter.copy(importProgress = ImportProgressState(done = 7, total = 23)))

        compose.onNodeWithText(context.getString(R.string.counter_import_running)).assertExists()
        compose.onNodeWithText(context.getString(R.string.counter_import_progress, 7, 23)).assertExists()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(7f / 23, 0f..1f))).assertExists()
    }

    @Test
    fun `a finished import says what it added and offers Undo`() {
        val mixed = ImportSummaryState(added = 9, skipped = 3, failed = 1, undoable = true)
        show(counter.copy(importSummary = mixed))

        listOf(
            plural(R.plurals.counter_import_added, 9),
            plural(R.plurals.counter_import_skipped, 3),
            plural(R.plurals.counter_import_failed, 1),
        ).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText(context.getString(R.string.counter_undo)).performClick()

        assertEquals(1 to 0, undos to dismissals)
    }

    @Test
    fun `an undone import offers OK, which dismisses`() {
        val undone = ImportSummaryState(added = 9, skipped = null, failed = null, undoable = false)
        show(counter.copy(importSummary = undone))

        compose.onNodeWithText(context.getString(R.string.counter_import_ok)).performClick()

        assertEquals(0 to 1, undos to dismissals)
    }

    private fun plural(id: Int, count: Int) = context.resources.getQuantityString(id, count, count)

    private fun show(state: CounterState) {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = state,
                    onUndoImportClick = { undos++ },
                    onImportSummaryDismiss = { dismissals++ },
                )
            }
        }
    }

    private val counter = CounterState(totalLabel = "147", count = 147, undoVisible = false)
}
