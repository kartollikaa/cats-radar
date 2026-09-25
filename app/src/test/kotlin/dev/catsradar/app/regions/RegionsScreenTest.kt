package dev.catsradar.app.regions

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import dev.catsradar.presentation.regions.RegionsEmptyHint
import dev.catsradar.presentation.regions.RegionsEmptyLabel
import dev.catsradar.presentation.regions.RegionsHeader
import dev.catsradar.presentation.regions.RegionsSection
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.presentation.regions.RegionsTitle
import dev.catsradar.ui.R
import dev.catsradar.ui.components.CenterAppBarDefaults
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RegionsScreenTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var state: RegionsState by mutableStateOf(RegionsState.Loading)

    @Test
    fun `a loading level draws no words, where the same level found empty draws one line`() {
        show(RegionsState.Loading)
        val words = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        words.assertCountEquals(0)

        state = RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_HERE)

        words.assertCountEquals(1)
    }

    @Test
    fun `each kind of empty level reads in words of its own`() {
        val words = mapOf(
            RegionsEmptyLabel.NO_PLACES_YET to R.string.regions_no_places_yet,
            RegionsEmptyLabel.NO_PLACES_HERE to R.string.regions_no_places_here,
            RegionsEmptyLabel.NO_CATS_HERE to R.string.regions_no_cats_here,
        )
        assertEquals(RegionsEmptyLabel.entries.toSet(), words.keys)
        show(RegionsState.Loading)

        words.forEach { (label, text) ->
            state = RegionsState.Empty(label)
            compose.onNodeWithText(context.getString(text)).assertExists()
        }
    }

    @Test
    fun `a level of places names itself, counts its cats and titles its rows`() {
        show(
            RegionsState.Places(
                header = RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Spain")), count = 128),
                section = RegionsSection.CITIES,
                rows = persistentListOf(
                    RegionRowState(RegionRowKey.City("ES", "Girona"), RegionRowLabel.Named("Girona"), "128", 1f, false),
                ),
            ),
        )

        compose.onNodeWithText("Spain").assert(isHeading)
        val count = context.resources.getQuantityString(R.plurals.regions_cat_count, 128, 128)
        compose.onNodeWithText(count).assertExists()
        compose.onNodeWithText(context.getString(R.string.regions_section_cities)).assert(isHeading)
    }

    @Test
    fun `the top level is called Places`() {
        show(RegionsState.Places(RegionsHeader(RegionsTitle.AllPlaces, 3), RegionsSection.COUNTRIES, oneCountry))

        compose.onNodeWithText(context.getString(R.string.regions_title)).assert(isHeading)
    }

    @Test
    fun `an area's cats are drawn with their times under the area's headline`() {
        show(RegionsState.Cats(RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Gràcia")), 2), twoCats))

        compose.onNodeWithText("Gràcia").assert(isHeading)
        listOf("19:18", "18:57").forEach { compose.onNodeWithText(it).assertHasClickAction() }
    }

    @Test
    fun `an outing header's On the map hands back the outing`() {
        val asked = mutableListOf<String>()
        state = RegionsState.Cats(header = null, rows = twoCats)
        compose.setContent {
            CatsRadarTheme { RegionsScreen(state = state, onOutingMapClick = { asked += it }) }
        }

        compose.onNodeWithText(context.getString(R.string.encounters_outing_on_map)).performClick()

        assertEquals(listOf("c3"), asked)
    }

    @Test
    fun `an empty level shows its hint when it has one, and none otherwise`() {
        val hint = context.getString(R.string.regions_no_places_yet_hint)
        show(RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_YET, RegionsEmptyHint.HOW_PLACES_APPEAR))
        compose.onNodeWithText(context.getString(R.string.regions_no_places_yet)).assertExists()
        compose.onNodeWithText(hint).assertExists()

        listOf(RegionsEmptyLabel.NO_PLACES_HERE, RegionsEmptyLabel.NO_CATS_HERE).forEach { label ->
            state = RegionsState.Empty(label)
            compose.onNodeWithText(hint).assertDoesNotExist()
        }
    }

    @Test
    fun `every kind of level offers the back arrow, and a tap on it reports once`() {
        var backs = 0
        state = RegionsState.Loading
        compose.setContent { CatsRadarTheme { RegionsScreen(state = state, onBackClick = { backs++ }) } }
        val levels = listOf(
            RegionsState.Loading,
            RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_HERE),
            RegionsState.Places(RegionsHeader(RegionsTitle.AllPlaces, 3), RegionsSection.COUNTRIES, oneCountry),
            RegionsState.Cats(RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Gràcia")), 2), twoCats),
        )

        levels.forEach { level ->
            state = level
            compose.onNodeWithContentDescription(context.getString(R.string.regions_back)).performClick()
        }

        assertEquals(levels.size, backs)
    }

    @Test
    fun `a level's headline starts below the status bar and the back arrow's bar`() {
        val statusBar = 24.dp
        state = RegionsState.Places(RegionsHeader(RegionsTitle.AllPlaces, 3), RegionsSection.COUNTRIES, oneCountry)
        compose.setContent {
            CatsRadarTheme { RegionsScreen(state = state, contentPadding = PaddingValues(top = statusBar)) }
        }

        val headlineTop = compose.onNodeWithText(context.getString(R.string.regions_title))
            .getUnclippedBoundsInRoot().top
        val barBottom = compose.onNodeWithContentDescription(context.getString(R.string.regions_back))
            .getUnclippedBoundsInRoot().bottom

        assertTrue(headlineTop >= statusBar + CenterAppBarDefaults.Height, "headline at $headlineTop")
        assertTrue(headlineTop >= barBottom, "headline at $headlineTop, arrow ends at $barBottom")
    }

    @Test
    fun `an area's headline also starts below the back arrow's bar`() {
        val statusBar = 24.dp
        state = RegionsState.Cats(RegionsHeader(RegionsTitle.Of(RegionRowLabel.Named("Gràcia")), 2), twoCats)
        compose.setContent {
            CatsRadarTheme { RegionsScreen(state = state, contentPadding = PaddingValues(top = statusBar)) }
        }

        val headlineTop = compose.onNodeWithText("Gràcia").getUnclippedBoundsInRoot().top

        assertTrue(headlineTop >= statusBar + CenterAppBarDefaults.Height, "headline at $headlineTop")
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private val oneCountry = persistentListOf(
        RegionRowState(RegionRowKey.Country("ES"), RegionRowLabel.Named("Spain"), "3", 1f, pseudo = false),
    )

    private val twoCats = persistentListOf(
        OutingHeader(key = "header-c2", label = "Today, 18:57", mapOutingId = "c3"),
        EncountersRow.Single(EncounterCell("c3", "19:18", LocationLabel.FROM_PHOTO), GroupPosition.FIRST),
        EncountersRow.Single(EncounterCell("c2", "18:57", LocationLabel.CURRENT), GroupPosition.LAST),
    )

    private fun show(initial: RegionsState) {
        state = initial
        compose.setContent { CatsRadarTheme { RegionsScreen(state = state) } }
    }
}
