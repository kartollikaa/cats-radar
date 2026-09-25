package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.EncounterDetailState
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class EncounterDetailScreenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `a cat on screen offers back, and the tap reports`() {
        var backs = 0
        show(loaded, onBackClick = { backs++ })

        back().assertIsDisplayed().performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `a removed cat still offers back, before and after the undo window closes`() {
        var removed by mutableStateOf(EncounterDetailState.Deleted(undoVisible = true))
        show { removed }
        back().assertIsDisplayed()

        removed = EncounterDetailState.Deleted(undoVisible = false)

        back().assertIsDisplayed()
    }

    @Test
    fun `a cat that is gone still offers back`() {
        show(EncounterDetailState.Missing)

        back().assertIsDisplayed()
    }

    @Test
    fun `the list runs under the status bar while its first line starts below the bar`() {
        show(loaded)

        val list = compose.onNode(scrollsVertically).fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description))
            .fetchSemanticsNode().boundsInRoot

        assertEquals(0f, list.top)
        assertTrue(photo.top >= (STATUS_BAR + 56.dp).px(), "the photo starts at ${photo.top}px, under the bar")
    }

    @Test
    fun `scrolled to the end, delete clears the bottom bar`() {
        show(loaded)
        val screenBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom

        compose.onNode(scrollsVertically).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val delete = compose.onNodeWithText(context.getString(R.string.detail_delete)).fetchSemanticsNode()

        assertEquals(screenBottom - (BOTTOM_BAR + 16.dp).px(), delete.boundsInRoot.bottom, 1f)
    }

    @Test
    fun `a cat with no location offers set on map, and the tap reports`() {
        var taps = 0
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(state = loaded.copy(setsLocation = true), onSetLocationClick = { taps++ })
            }
        }

        setOnMap().performScrollTo().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `a cat with a location offers no set on map`() {
        show(loaded.copy(location = LocationLabel.CURRENT, coordinatesLabel = "41.39000, 2.17000"))

        setOnMap().assertDoesNotExist()
    }

    private fun show(state: EncounterDetailState, onBackClick: () -> Unit = {}) = show(onBackClick) { state }

    private fun show(onBackClick: () -> Unit = {}, state: () -> EncounterDetailState) {
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = state(),
                    contentPadding = PaddingValues(top = STATUS_BAR, bottom = BOTTOM_BAR),
                    onBackClick = onBackClick,
                )
            }
        }
    }

    private val scrollsVertically = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun back() = compose.onNodeWithContentDescription(context.getString(R.string.detail_back))

    private fun setOnMap() = compose.onNodeWithText(context.getString(R.string.detail_set_location))

    private fun Dp.px(): Float = with(compose.density) { toPx() }

    private companion object {
        const val DAY = "Today"
        val STATUS_BAR = 24.dp
        val BOTTOM_BAR = 80.dp

        // The photo makes the list taller than the screen, so it has somewhere to scroll.
        val loaded = EncounterDetailState.Loaded(
            dayLabel = DAY,
            timeLabel = "14:32",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            photos = persistentListOf(DetailPhoto(id = "cat-1", path = "/data/photos/cat-1.jpg")),
        )
    }
}
