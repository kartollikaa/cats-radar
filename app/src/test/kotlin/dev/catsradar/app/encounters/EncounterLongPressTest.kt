package dev.catsradar.app.encounters

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersLayout
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class EncounterLongPressTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val oneCat = persistentListOf(
        OutingHeader(key = "header-c1", label = "Today, 14:32"),
        EncountersRow.Single(EncounterCell("c1", "14:32", LocationLabel.CURRENT), GroupPosition.ONLY),
    )

    @Test
    fun `a cat in the Encounters tab is selected by a long press`() {
        val pressed = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncountersScreen(
                    state = EncountersState(rows = oneCat, layout = EncountersLayout.LIST),
                    onEncounterLongClick = { pressed += it },
                )
            }
        }

        compose.onNodeWithText("14:32").performSemanticsAction(SemanticsActions.OnLongClick)

        assertEquals(listOf("c1"), pressed)
    }

    @Test
    fun `a cat in Places offers no long press, having nothing to select`() {
        compose.setContent { CatsRadarTheme { RegionsScreen(state = RegionsState.Cats(header = null, rows = oneCat)) } }

        compose.onNodeWithText("14:32").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }
}
