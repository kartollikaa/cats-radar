package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.ShotCat
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class EncounterDetailShotRowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val onThisPhoto get() = context.getString(R.string.detail_on_this_photo)

    @Test
    fun `the row shows each cat of the photo and reports the one tapped`() {
        val tapped = mutableListOf<String>()
        var state by mutableStateOf(loadedOn(shotPage(onScreen = "s1"), lone, shotPage(onScreen = "s1")))
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = state, onPhotoCatClick = { tapped += it }) }
        }

        compose.onNodeWithText(onThisPhoto).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.coat_ginger)).assertIsSelected()
        compose.onNodeWithContentDescription(label(R.string.coat_not_specified)).assertIsNotSelected()
        compose.onNodeWithContentDescription(label(R.string.coat_black)).assertIsNotSelected().performClick()
        assertEquals(listOf("s2"), tapped)

        state = loadedWith(lone)
        compose.waitForIdle()
        compose.onNodeWithText(onThisPhoto).assertDoesNotExist()
    }

    @Test
    fun `switching the cat of the photo keeps the pager where it is`() {
        val settled = mutableListOf<String>()
        var state by mutableStateOf(loadedOn(shotPage(onScreen = "s1"), lone, shotPage(onScreen = "s1")))
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = state, onPageSettle = { settled += it }) }
        }
        compose.onNodeWithText(SHOT_TIME).assertIsDisplayed()

        state = loadedOn(shotPage(onScreen = "s2"), lone, shotPage(onScreen = "s2"))
        compose.waitForIdle()

        compose.onNodeWithText(SHOT_TIME).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.coat_black)).assertIsSelected()
        assertEquals(emptyList(), settled)
    }

    private fun label(id: Int) = context.getString(id)

    private fun shotPage(onScreen: String) = CatPage(
        id = onScreen,
        dayLabel = "Today",
        timeLabel = SHOT_TIME,
        location = LocationLabel.NONE,
        coordinatesLabel = null,
        accuracyMeters = null,
        onThisPhoto = persistentListOf(
            ShotCat("s1", CoatOption.GINGER, onScreen = onScreen == "s1"),
            ShotCat("s2", CoatOption.BLACK, onScreen = onScreen == "s2"),
            ShotCat("s3", coat = null, onScreen = onScreen == "s3"),
        ),
        pageKey = "s1",
    )

    private val lone = CatPage(
        id = "lone",
        dayLabel = "Today",
        timeLabel = "14:10",
        location = LocationLabel.NONE,
        coordinatesLabel = null,
        accuracyMeters = null,
    )

    private companion object {
        const val SHOT_TIME = "10:00"
    }
}
