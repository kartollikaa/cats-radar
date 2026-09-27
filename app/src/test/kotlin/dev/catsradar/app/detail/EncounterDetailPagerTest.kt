package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.DetailPagesTestTag
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
@Config(qualifiers = "w360dp-h640dp")
class EncounterDetailPagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `a swipe to the next page reports the older cat, once`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(state = loadedOn(newer, newer, older), onPageSettle = { settled += it })
            }
        }

        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(listOf(older.id), settled)
    }

    @Test
    fun `the pager follows the cat on screen when the state moves it`() {
        var state by mutableStateOf(loadedOn(newer, newer, older))
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = state, onPageSettle = { settled += it }) }
        }
        compose.onNodeWithText(NEWER_TIME).assertIsDisplayed()

        state = loadedOn(older, newer, older)
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(emptyList(), settled)
    }

    @Test
    fun `a cat logged while watching keeps the cat on screen and reports nothing`() {
        var state by mutableStateOf(loadedOn(older, newer, older))
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = state, onPageSettle = { settled += it }) }
        }
        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()

        state = loadedOn(older, logged, newer, older)
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(emptyList(), settled)
    }

    @Test
    fun `several pages show the position of the cat on screen, read as Cat n of m`() {
        compose.setContent { CatsRadarTheme { EncounterDetailScreen(state = loadedOn(newer, logged, newer, older)) } }

        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.detail_position_description, 2, 3))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `a cat swiped away from and back to keeps its photo`() {
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = loadedOn(photographed, photographed, older)) }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description))
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description))
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.onNodeWithText(NEWER_TIME).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.viewer_position_description, 2, 2))
            .assertExists()
    }

    @Test
    fun `a single cat shows no position`() {
        compose.setContent { CatsRadarTheme { EncounterDetailScreen(state = loadedOn(older, older)) } }

        compose.onNodeWithText("1 / 1").assertDoesNotExist()
        compose.onNode(hasContentDescription(context.getString(R.string.detail_position_description, 1, 1)))
            .assertDoesNotExist()
    }

    @Test
    fun `a drag past a cat's last photo moves on to the next cat`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = loadedOn(photographed, photographed, older),
                    onPageSettle = { settled += it },
                )
            }
        }
        val photo = compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description))

        photo.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.viewer_position_description, 2, 2))
            .assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description))
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(listOf(older.id), settled)
    }

    private companion object {
        const val LOGGED_TIME = "14:40"
        const val NEWER_TIME = "14:32"
        const val OLDER_TIME = "13:58"

        val logged = page("cat-0", LOGGED_TIME)
        val newer = page("cat-1", NEWER_TIME)
        val older = page("cat-2", OLDER_TIME)
        val photographed = newer.copy(
            photos = persistentListOf(
                DetailPhoto(id = "photo-1", path = "/data/photos/photo-1.jpg"),
                DetailPhoto(id = "photo-2", path = "/data/photos/photo-2.jpg"),
            ),
        )

        fun page(id: String, time: String) = CatPage(
            id = id,
            dayLabel = "Today",
            timeLabel = time,
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        )
    }
}
