package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DetailPhotoPagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `a cat with several photos shows where the pager is`() {
        show(catWith("cover", "second"))

        position(1, of = 2).assertIsDisplayed()
    }

    @Test
    fun `a cat with one photo shows no position`() {
        show(catWith("cover"))

        position(1, of = 1).assertDoesNotExist()
    }

    @Test
    fun `a photo that arrives brings the pager to it`() {
        var cat by mutableStateOf(catWith("cover", "second"))
        compose.setContent { CatsRadarTheme { EncounterDetailScreen(state = cat) } }
        position(1, of = 2).assertIsDisplayed()

        cat = catWith("cover", "second", "third")
        compose.waitForIdle()

        position(3, of = 3).assertIsDisplayed()
    }

    @Test
    fun `a tap on a photo reports which photo it was`() {
        val tapped = mutableListOf<String>()
        show(catWith("cover", "second"), onPhotoClick = { tapped += it })

        compose.onAllNodesWithContentDescription(context.getString(R.string.detail_photo_description))
            .onFirst()
            .performClick()

        assertEquals(listOf("cover"), tapped)
    }

    @Test
    fun `a tap on the photo swiped to reports that photo, not the cover`() {
        val tapped = mutableListOf<String>()
        show(catWith("cover", "second"), onPhotoClick = { tapped += it })
        compose.onAllNodesWithContentDescription(context.getString(R.string.detail_photo_description))
            .onFirst()
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        position(2, of = 2).assertIsDisplayed()

        val photos = compose.onAllNodesWithContentDescription(context.getString(R.string.detail_photo_description))
        val onScreen = photos.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f }
        photos[onScreen].performClick()

        assertEquals(listOf("second"), tapped)
    }

    private fun show(state: EncounterDetailState, onPhotoClick: (String) -> Unit = {}) {
        compose.setContent { CatsRadarTheme { EncounterDetailScreen(state = state, onPhotoClick = onPhotoClick) } }
    }

    private fun position(page: Int, of: Int) =
        compose.onNodeWithText(context.getString(R.string.viewer_position, page, of))

    private fun catWith(vararg photoIds: String) = EncounterDetailState.Loaded(
        dayLabel = "Today",
        timeLabel = "14:32",
        location = LocationLabel.NONE,
        coordinatesLabel = null,
        accuracyMeters = null,
        photos = photoIds.map { DetailPhoto(id = it, path = "/data/photos/$it.jpg") }.toImmutableList(),
    )
}
