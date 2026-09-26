package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.map.SpotMapTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class EncounterDetailPagesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `the screen draws the cat on screen, and its taps name that cat`() {
        val taps = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = secondOnScreen(unlocated),
                    onPhotoClick = { taps += "photo ${it.catId} ${it.photoId}" },
                    onTakePhotoClick = { taps += "take $it" },
                    onPickPhotoClick = { taps += "pick $it" },
                    onSetLocationClick = { taps += "set on map $it" },
                    onCoatClick = { taps += "coat ${it.catId} ${it.coat}" },
                )
            }
        }
        compose.onNodeWithText(SECOND_TIME).assertExists()
        compose.onNodeWithText(FIRST_TIME).assertDoesNotExist()

        compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_take_photo)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_pick_photo)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_set_location)).performScrollTo().performClick()
        // The coat cell sits in CoatPicker's own horizontal row: performScrollTo() on it scrolls
        // that row, not the screen's vertical list, so the list is scrolled directly first.
        scrollListToEnd()
        compose.onNodeWithText(context.getString(R.string.coat_ginger)).performClick()

        assertEquals(
            listOf("photo cat-2 photo-2", "take cat-2", "pick cat-2", "set on map cat-2", "coat cat-2 GINGER"),
            taps,
        )
    }

    @Test
    fun `a tap on the map of the cat on screen names that cat`() {
        val taps = mutableListOf<String>()
        compose.setContent {
            // As in EncounterDetailScreenTest's show(): the map cannot start on the JVM.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CatsRadarTheme {
                    EncounterDetailScreen(state = secondOnScreen(located), onCoordinatesClick = { taps += it })
                }
            }
        }

        compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true).performTouchInput { click() }

        assertEquals(listOf("cat-2"), taps)
    }

    private fun scrollListToEnd() {
        compose.onNode(scrollsVertically).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
    }

    private val scrollsVertically = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun secondOnScreen(second: CatPage) = EncounterDetailState.Loaded(
        pages = persistentListOf(first, second),
        currentId = second.id,
        currentNumber = 2,
    )

    private companion object {
        const val FIRST_TIME = "14:32"
        const val SECOND_TIME = "14:25"

        val first = CatPage(
            id = "cat-1",
            dayLabel = "Today",
            timeLabel = FIRST_TIME,
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        )

        val unlocated = CatPage(
            id = "cat-2",
            dayLabel = "Today",
            timeLabel = SECOND_TIME,
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            photos = persistentListOf(DetailPhoto(id = "photo-2", path = "/data/photos/photo-2.jpg")),
            setsLocation = true,
        )

        val located = unlocated.copy(
            location = LocationLabel.CURRENT,
            coordinatesLabel = "41.39864, 2.17842",
            photos = persistentListOf(),
            mapPosition = MapPosition(latitude = 41.39864, longitude = 2.17842),
            setsLocation = false,
        )
    }
}
