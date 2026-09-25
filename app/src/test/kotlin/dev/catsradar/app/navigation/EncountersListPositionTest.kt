package dev.catsradar.app.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersLayout
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.navigation.BottomNavTab
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.toPersistentList
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EncountersListPositionTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val backStack = BottomNavBackStack(NavBackStack<NavKey>(Counter))
    private var state by mutableStateOf(outings(1..OUTINGS))

    @Before
    fun openTheList() {
        compose.setContent {
            CatsRadarTheme {
                CatsRadarNavDisplay(
                    backStack = backStack,
                    entryProvider = entryProvider {
                        entry<Counter>(metadata = tabRootMetadata()) { Text(COUNTER) }
                        entry<Encounters>(metadata = tabRootMetadata()) { EncountersScreen(state = state) }
                        entry<EncounterDetail> { Text(CAT) }
                    },
                )
            }
        }
        settle { backStack.selectTab(BottomNavTab.ENCOUNTERS) }
    }

    @Test
    fun `back from a cat returns to the list scrolled where it was`() {
        compose.onNode(hasScrollToKeyAction()).performScrollToKey(EncountersRow.Single(cell(FAR), ONLY).key)
        compose.waitForIdle()
        assertTrue(isShown(time(FAR)), "the list is scrolled to the far cat before it opens")

        settle { backStack.push(EncounterDetail("$FAR")) }
        settle { backStack.popOrNull() }

        assertEquals(listOf(Counter, Encounters), backStack.toList())
        assertTrue(isShown(time(FAR)), "the far cat is still on screen")
        assertFalse(isShown(header(1)), "the list did not jump back to the top")
    }

    @Test
    fun `an outing coming back above a list resting at the top is shown`() {
        settle { state = outings(2..OUTINGS) }
        assertTrue(isShown(header(2)), "the list rests at the top")

        settle { state = outings(1..OUTINGS) }

        assertTrue(isShown(header(1)), "the outing that came back above is on screen")
    }

    private fun settle(change: () -> Unit) {
        compose.runOnUiThread(change)
        compose.waitForIdle()
    }

    private fun isShown(text: String) = compose.onNodeWithText(text).isDisplayed()

    private companion object {
        const val COUNTER = "counter screen"
        const val CAT = "cat screen"
        const val OUTINGS = 40
        const val FAR = 30
        val ONLY = GroupPosition.ONLY

        fun header(outing: Int) = "Outing $outing"

        fun time(outing: Int) = "Cat $outing"

        fun cell(outing: Int) = EncounterCell("$outing", time(outing), LocationLabel.NONE)

        fun outings(range: IntRange) = EncountersState(
            rows = range.flatMap { outing ->
                listOf(
                    OutingHeader(key = "header-$outing", label = header(outing)),
                    EncountersRow.Single(cell(outing), ONLY),
                )
            }.toPersistentList(),
            layout = EncountersLayout.LIST,
        )
    }
}
