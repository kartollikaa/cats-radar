package dev.catsradar.app.navigation

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionRowLabel
import dev.catsradar.presentation.regions.RegionRowState
import dev.catsradar.presentation.regions.RegionsHeader
import dev.catsradar.presentation.regions.RegionsSection
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.presentation.regions.RegionsTitle
import dev.catsradar.ui.R
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class RegionsDrillDownTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `every row kind hands back its own key when tapped, an area's too`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rowsWithShownText = listOf(
            row(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "12") to "Spain",
            row(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "9") to "Barcelona",
            row(RegionRowKey.Area("sp3e3", barcelona), RegionRowLabel.Named("Gràcia"), "5") to "Gràcia",
            row(RegionRowKey.Unresolved, RegionRowLabel.Unresolved, "3") to
                context.getString(R.string.regions_unresolved),
            row(RegionRowKey.NoCity("ES"), RegionRowLabel.NoCity, "2") to
                context.getString(R.string.regions_no_city),
            row(RegionRowKey.NoLocation, RegionRowLabel.NoLocation, "1") to
                context.getString(R.string.regions_no_location),
        )
        val rows = rowsWithShownText.map { (row, _) -> row }.toPersistentList()
        val tapped = mutableListOf<RegionRowKey>()
        val level = RegionsState.Places(header = null, section = RegionsSection.AREAS, rows = rows)
        compose.setContent {
            CatsRadarTheme { RegionsScreen(state = level, onRegionClick = { tapped += it }) }
        }

        rowsWithShownText.forEach { (row, text) ->
            compose.onNodeWithText(text).assertHasClickAction().assertTextContains(row.countLabel).performClick()
        }

        assertEquals(rows.map { it.key }, tapped)
    }

    @Test
    fun `every cat hands back its own id when tapped, and an outing header hands back nothing`() {
        val cats = persistentListOf(
            OutingHeader(key = "header-evening", label = "Today, 18:40"),
            EncountersRow.Single(EncounterCell("c3", "19:18", LocationLabel.FROM_PHOTO), GroupPosition.FIRST),
            EncountersRow.Single(EncounterCell("c2", "18:57", LocationLabel.CURRENT), GroupPosition.LAST),
            OutingHeader(key = "header-morning", label = "Yesterday, 08:15"),
            EncountersRow.Single(EncounterCell("c1", "08:22", LocationLabel.FROM_OUTING), GroupPosition.ONLY),
        )
        val tapped = mutableListOf<String>()
        val area = RegionsState.Cats(RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Gràcia")), 3), cats)
        compose.setContent {
            CatsRadarTheme { RegionsScreen(state = area, onEncounterClick = { tapped += it }) }
        }

        listOf("08:22", "Today, 18:40", "19:18", "Yesterday, 08:15", "18:57").forEach { text ->
            compose.onNodeWithText(text).performClick()
        }

        assertEquals(listOf("c1", "c3", "c2"), tapped)
        listOf("Today, 18:40", "Yesterday, 08:15").forEach { header ->
            compose.onNodeWithText(header).assertHasNoClickAction()
        }
    }

    private val barcelona = RegionRowKey.City("ES", "Barcelona")

    private fun row(key: RegionRowKey, label: RegionRowLabel, count: String) =
        RegionRowState(key, label, countLabel = count, share = 0.5f, pseudo = false)
}
