package dev.catsradar.app.navigation

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.regions.RegionRowKey
import dev.catsradar.presentation.regions.RegionRowLabel
import dev.catsradar.presentation.regions.RegionRowState
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.ui.R
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
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
            RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "12") to "Spain",
            RegionRowState(RegionRowKey.City("ES", "Barcelona"), RegionRowLabel.Named("Barcelona"), "9") to "Barcelona",
            RegionRowState(
                RegionRowKey.Area("sp3e3", RegionRowKey.City("ES", "Barcelona")),
                RegionRowLabel.Named("Gràcia"),
                "5",
            ) to "Gràcia",
            RegionRowState(RegionRowKey.Unresolved, RegionRowLabel.Unresolved, "3") to
                context.getString(R.string.regions_unresolved),
            RegionRowState(RegionRowKey.NoCity("ES"), RegionRowLabel.NoCity, "2") to
                context.getString(R.string.regions_no_city),
            RegionRowState(RegionRowKey.NoLocation, RegionRowLabel.NoLocation, "1") to
                context.getString(R.string.regions_no_location),
        )
        val rows = rowsWithShownText.map { (row, _) -> row }.toPersistentList()
        val tapped = mutableListOf<RegionRowKey>()
        compose.setContent {
            CatsRadarTheme { RegionsScreen(state = RegionsState(rows = rows), onRegionClick = { tapped += it }) }
        }

        rowsWithShownText.forEach { (_, text) -> compose.onNodeWithText(text).performClick() }

        assertEquals(rows.map { it.key }, tapped)
    }
}
