package dev.catsradar.app.coat

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CoatPromptAction
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// The sheet's content scrolls, and a swipe down on it must still reach the sheet.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CoatPromptSheetDismissTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val asking = CoatPromptState("cat", "cat", thumbPath = null)

    @Test
    fun `a swipe down on the question closes it`() {
        val title = context.getString(R.string.counter_coat_prompt_title)

        assertEquals(listOf<CoatPromptAction>(CoatPromptAction.Dismissed), swipeDownOn(asking, title))
    }

    @Test
    fun `a swipe down on a count closes it`() {
        val counting = asking.copy(counting = CoatCountState(persistentListOf(CoatOption.GINGER, CoatOption.BLACK)))

        val title = context.resources.getQuantityString(R.plurals.counter_coat_count_title, 2, 2)

        assertEquals(listOf<CoatPromptAction>(CoatPromptAction.Dismissed), swipeDownOn(counting, title))
    }

    private fun swipeDownOn(prompt: CoatPromptState, title: String): List<CoatPromptAction> {
        val actions = mutableListOf<CoatPromptAction>()
        val state = CounterState(totalLabel = "3", count = 3, undoVisible = false, coatPrompt = prompt)
        compose.setContent {
            CatsRadarTheme { CounterScreen(state = state, onCoatPromptAction = { actions += it }) }
        }
        compose.waitForIdle()

        compose.onNodeWithText(title).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 500.dp.toPx(), durationMillis = 400)
        }
        compose.waitForIdle()
        return actions
    }
}
