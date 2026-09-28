package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.counter.LocationHintAction
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Config(qualifiers = "w411dp-h760dp")
@RunWith(AndroidJUnit4::class)
class LocationIslandTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val atRest = CounterState(totalLabel = "147", count = 147, undoVisible = false)
    private val hinting = atRest.copy(locationPermissionHintVisible = true)
    private var state by mutableStateOf(hinting)
    private val actions = mutableListOf<LocationHintAction>()
    private var tallies = 0

    private fun show() {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(state = state, onTallyClick = { tallies++ }, onLocationHintAction = { actions += it })
            }
        }
    }

    private val words get() = context.getString(R.string.counter_location_hint)

    private fun hint() = compose.onNodeWithText(words)

    private fun block() = compose.onNodeWithContentDescription("147").getUnclippedBoundsInRoot()

    @Test
    fun `the count keeps its size with the hint showing`() {
        state = atRest
        show()
        val alone = block()

        state = hinting
        compose.waitForIdle()

        assertEquals(alone, block())
    }

    @Test
    fun `Grant asks for location`() {
        show()

        compose.onNodeWithText(context.getString(R.string.counter_location_grant)).performClick()

        assertEquals(listOf(LocationHintAction.GRANT), actions)
    }

    @Test
    fun `the close button dismisses the hint and grants nothing`() {
        show()

        compose.onNodeWithContentDescription(context.getString(R.string.counter_location_dismiss)).performClick()

        assertEquals(listOf(LocationHintAction.DISMISS), actions)
    }

    @Test
    fun `a swipe to either side dismisses the hint`() {
        show()
        hint().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        state = atRest
        compose.waitForIdle()
        state = hinting
        compose.waitForIdle()
        hint().performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(listOf(LocationHintAction.DISMISS, LocationHintAction.DISMISS), actions)
    }

    @Test
    fun `the hint's body takes no tap, and its words are one item`() {
        show()

        hint().assertHasNoClickAction()
        hint().performClick()

        assertEquals(emptyList<LocationHintAction>() to 0, actions to tallies)
    }

    @Test
    fun `beside an import both cards show, the import's above the hint's`() {
        state = hinting.copy(importProgress = ImportProgressState(done = 3, total = 9))
        show()
        val import = compose.onNodeWithText(context.getString(R.string.counter_import_running_count, 3, 9))
            .getUnclippedBoundsInRoot()

        val hint = hint().getUnclippedBoundsInRoot()

        assertTrue(import.bottom <= hint.top, "$import over $hint")
    }

    @Test
    fun `TalkBack reaches the hint before the count it covers`() {
        show()

        compose.onNode(
            hasAnyDescendant(hasText(words)) and
                SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, -1f),
        ).assertExists()
    }

    @Test
    fun `no hint shows when none is due`() {
        state = atRest
        show()

        hint().assertDoesNotExist()
    }
}
