package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.AttachProgress
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.DetailPlace
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.components.FlagTestTag
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.map.CatDotTestTag
import dev.catsradar.ui.map.SpotMapTestTag
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
        show(loadedWith(cat), onBackClick = { backs++ })

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
        show(loadedWith(cat))

        val list = compose.onNode(scrollsVertically).fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description))
            .fetchSemanticsNode().boundsInRoot

        assertEquals(0f, list.top)
        assertTrue(photo.top >= (STATUS_BAR + 56.dp).px(), "the photo starts at ${photo.top}px, under the bar")
    }

    @Test
    fun `the back button lines up with the content under it`() {
        show(loadedWith(cat))

        val photo = compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_description))
            .fetchSemanticsNode().boundsInRoot
        val button = back().fetchSemanticsNode().boundsInRoot

        assertEquals(photo.left, button.left, 1f)
    }

    @Test
    fun `scrolled to the end, delete clears the bottom bar`() {
        show(loadedWith(cat))
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
                EncounterDetailScreen(
                    state = loadedWith(cat.copy(setsLocation = true)),
                    onSetLocationClick = { taps++ },
                )
            }
        }

        setOnMap().performScrollTo().performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `a cat with a location offers no set on map`() {
        show(loadedWith(cat.copy(location = LocationLabel.CURRENT, coordinatesLabel = "41.39000, 2.17000")))

        setOnMap().assertDoesNotExist()
    }

    @Test
    fun `several photos being attached show how many are through out of how many`() {
        show(loadedWith(cat.copy(addPhoto = AddPhoto.ATTACHING, attachProgress = AttachProgress(done = 2, total = 5))))

        val bar = compose.onNodeWithContentDescription("Attached 2 of 5 photos")
        bar.assertExists()
        assertEquals(0.4f, bar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        compose.onNodeWithText(context.getString(R.string.detail_take_photo)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.detail_pick_photo)).assertIsNotEnabled()
    }

    @Test
    fun `a single photo being attached shows the bar without a count`() {
        show(loadedWith(cat.copy(addPhoto = AddPhoto.ATTACHING)))

        compose.onNodeWithContentDescription(context.getString(R.string.detail_photo_attaching)).assertExists()
    }

    @Test
    fun `the where card names the cat's city and country after its flag, and TalkBack reads them without it`() {
        show(loadedWith(cat.copy(place = DetailPlace(title = "Barcelona", country = "Spain", flag = FLAG))))

        val city = compose.onNodeWithText("Barcelona", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val flag = compose.onNodeWithTag(FlagTestTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(flag.right <= city.left, "the flag ends at ${flag.right}px, past the city at ${city.left}px")
        compose.onNodeWithText("Barcelona")
            .assertTextContains("Spain")
            .assertTextContains(context.getString(R.string.location_none))
            .assert(!hasText(FLAG, substring = true))
    }

    @Test
    fun `a cat with no named place shows no place line`() {
        show(loadedWith(cat))

        compose.onNodeWithTag(FlagTestTag, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Barcelona", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a cat on the map shows a map with the cat's dot at its centre`() {
        show(loadedWith(onTheMap))

        val map = map().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dot = compose.onNodeWithTag(CatDotTestTag, useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertEquals(map.center.x, dot.center.x, 1f)
        assertEquals(map.center.y, dot.center.y, 1f)
    }

    @Test
    fun `a cat not on the map shows no map`() {
        show(loadedWith(cat.copy(location = LocationLabel.CURRENT, coordinatesLabel = "123.40000, 2.17000")))

        map().assertDoesNotExist()
    }

    @Test
    fun `a tap on the map opens the map`() {
        var opened = 0
        show(loadedWith(onTheMap), onCoordinatesClick = { opened++ })

        map().performTouchInput { click() }

        assertEquals(1, opened)
    }

    @Test
    fun `a drag across the map scrolls the screen`() {
        show(loadedWith(onTheMap))
        val before = map().fetchSemanticsNode().boundsInRoot.top

        map().performTouchInput { swipeUp() }

        assertTrue(map().fetchSemanticsNode().boundsInRoot.top < before, "the screen stayed where it was")
    }

    private fun show(
        state: EncounterDetailState,
        onBackClick: () -> Unit = {},
        onCoordinatesClick: () -> Unit = {},
    ) = show(onBackClick, onCoordinatesClick) { state }

    private fun show(
        onBackClick: () -> Unit = {},
        onCoordinatesClick: () -> Unit = {},
        state: () -> EncounterDetailState,
    ) {
        compose.setContent {
            val shown = state()
            // A located cat's map needs MapLibre's native runtime, which the JVM cannot start.
            CompositionLocalProvider(LocalInspectionMode provides shown.hasMap()) {
                CatsRadarTheme {
                    EncounterDetailScreen(
                        state = shown,
                        contentPadding = PaddingValues(top = STATUS_BAR, bottom = BOTTOM_BAR),
                        onBackClick = onBackClick,
                        onCoordinatesClick = { onCoordinatesClick() },
                    )
                }
            }
        }
    }

    private fun EncounterDetailState.hasMap() =
        (this as? EncounterDetailState.Loaded)?.pages?.any { it.mapPosition != null } == true

    private fun map() = compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true)

    private val scrollsVertically = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun back() = compose.onNodeWithContentDescription(context.getString(R.string.detail_back))

    private fun setOnMap() = compose.onNodeWithText(context.getString(R.string.detail_set_location))

    private fun Dp.px(): Float = with(compose.density) { toPx() }

    private companion object {
        const val DAY = "Today"
        const val FLAG = "🇪🇸"
        val STATUS_BAR = 24.dp
        val BOTTOM_BAR = 80.dp

        // The photo makes the list taller than the screen, so it has somewhere to scroll.
        val cat = CatPage(
            id = "cat-1",
            dayLabel = DAY,
            timeLabel = "14:32",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            photos = persistentListOf(DetailPhoto(id = "cat-1", path = "/data/photos/cat-1.jpg")),
        )

        // No photo, so the map sits on screen as it opens.
        val onTheMap = cat.copy(
            location = LocationLabel.CURRENT,
            coordinatesLabel = "41.39864, 2.17842",
            photos = persistentListOf(),
            mapPosition = MapPosition(latitude = 41.39864, longitude = 2.17842),
        )
    }
}
