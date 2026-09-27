package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
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
    private val hintActions = mutableListOf<LocationHintAction>()

    @Test
    fun `the location hint offers Grant and Dismiss`() {
        show(counter.copy(locationPermissionHintVisible = true))

        compose.onNodeWithText(context.getString(R.string.counter_location_hint)).assertHasNoClickAction()
        compose.onNodeWithText(context.getString(R.string.counter_location_grant)).performClick()
        compose.onNodeWithText(context.getString(R.string.counter_location_dismiss)).performClick()

        assertEquals(listOf(LocationHintAction.GRANT, LocationHintAction.DISMISS), hintActions)
    }

    private fun show(state: CounterState) {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = state,
                    onLocationHintAction = { hintActions += it },
                )
            }
        }
    }

    private val counter = CounterState(totalLabel = "147", count = 147, undoVisible = false)
}
