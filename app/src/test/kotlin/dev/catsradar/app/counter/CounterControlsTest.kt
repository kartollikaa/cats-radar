package dev.catsradar.app.counter

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.presentation.counter.CurrentOutingState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A phone-sized screen: on Robolectric's default one the Counter scrolls and the count sits at its floor.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class CounterControlsTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val requested = mutableListOf<Boolean>()

    @Test
    fun `the walk button sits under the Photo button, as wide as it`() {
        show(walking = false)

        val photo = compose.onNodeWithText(string(R.string.counter_camera)).getUnclippedBoundsInRoot()
        val walk = walkButton(walking = false).getUnclippedBoundsInRoot()

        assertTrue(walk.top >= photo.bottom, "the walk button's top is ${walk.top}, Photo's bottom ${photo.bottom}")
        assertEquals(photo.width, walk.width)
    }

    @Test
    fun `a tap with no walk on starts one`() {
        show(walking = false)

        walkButton(walking = false).performClick()

        assertEquals(listOf(true), requested)
    }

    @Test
    fun `a tap during a walk does not stop it`() {
        show(walking = true)

        walkButton(walking = true).performClick()
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(emptyList<Boolean>(), requested)
    }

    @Test
    fun `a press let go before the hold is up does not stop the walk`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS - 200)
        walkButton(walking = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(emptyList<Boolean>(), requested)
    }

    @Test
    fun `a press held through the hold stops the walk once, before the finger lifts`() {
        show(walking = true)
        compose.mainClock.autoAdvance = false

        walkButton(walking = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS + 100)
        val beforeLifting = requested.toList()
        walkButton(walking = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(HOLD_MS * 2)

        assertEquals(listOf(false), beforeLifting)
        assertEquals(listOf(false), requested)
    }

    @Test
    fun `during a walk the button says it has to be held`() {
        show(walking = true)

        compose.onNodeWithText(string(R.string.counter_walk_stop_hint)).assertIsDisplayed()
    }

    @Test
    fun `an accessibility click stops the walk without the hold`() {
        show(walking = true)

        walkButton(walking = true).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(false), requested)
    }

    @Test
    fun `Undo appearing and going leaves the count the same size`() {
        showUndo(visible = false)
        val hidden = countHeight()

        undoVisible = true
        compose.waitForIdle()
        val shown = countHeight()
        undoVisible = false
        compose.waitForIdle()

        assertEquals(hidden, shown)
        assertEquals(hidden, countHeight())
    }

    @Test
    fun `the outing line stays centred when Undo appears beside it`() {
        showUndo(visible = false, outing = CurrentOutingState(count = 4, elapsedLabel = "35 min", rate = null))
        val screenCentre = compose.onRoot().getUnclippedBoundsInRoot().let { (it.left + it.right) / 2 }
        val hidden = outingLineBounds()

        undoVisible = true
        compose.waitForIdle()

        assertEquals(screenCentre.value, ((hidden.left + hidden.right) / 2).value, absoluteTolerance = 1f)
        assertEquals(hidden, outingLineBounds())
    }

    private fun show(walking: Boolean) {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = false, walkingMode = walking),
                    onWalkingModeChange = { requested += it },
                )
            }
        }
    }

    private var undoVisible by mutableStateOf(false)

    private fun showUndo(visible: Boolean, outing: CurrentOutingState? = null) {
        undoVisible = visible
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = undoVisible, currentOuting = outing),
                )
            }
        }
    }

    private fun outingLineBounds() = compose.onNodeWithText(
        context.resources.getQuantityString(R.plurals.counter_outing_now, 4, 4, "35 min"),
    ).getUnclippedBoundsInRoot()

    private fun walkButton(walking: Boolean): SemanticsNodeInteraction =
        compose.onNodeWithText(string(if (walking) R.string.counter_walk_stop else R.string.counter_walk_start))

    private fun countHeight() = compose.onNodeWithContentDescription("3").getUnclippedBoundsInRoot().height

    private fun string(@StringRes id: Int) = context.getString(id)

    private companion object {
        const val HOLD_MS = 2_000L
    }
}
