package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPlace
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.components.FlagTestTag
import dev.catsradar.ui.detail.DetailFactsTestTag
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.detail.NoLocationNoticeTestTag
import dev.catsradar.ui.detail.WhereCardTestTag
import dev.catsradar.ui.detail.WherePillTestTag
import dev.catsradar.ui.map.AccuracyCircleTestTag
import dev.catsradar.ui.map.SpotMapTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class DetailWhereTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private lateinit var scheme: ColorScheme
    private lateinit var typography: Typography
    private val taps = mutableListOf<String>()

    @Test
    fun `a located cat's where card is on the low container with large corners, headed where you met`() {
        show(located)

        val card = compose.onNodeWithTag(WhereCardTestTag).performScrollTo().bounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.surfaceContainerLow, pixels[(card.left + 6.dp.px()).toInt(), card.center.y.toInt()])
        compose.assertLargeCorner(card, outside = scheme.surface, inside = scheme.surfaceContainerLow)
        val header = compose.onNodeWithText(context.getString(R.string.detail_where_you_met), useUnmergedTree = true)
        header.assert(isHeading())
        assertTrue(card.contains(header.bounds().center), "the header sits inside the card")
    }

    @Test
    fun `the card's map is 16 to 10 with medium corners`() {
        show(located)

        compose.onNodeWithTag(WhereCardTestTag).performScrollTo()
        val map = compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true).bounds()
        assertEquals(1.6f, map.width / map.height, 0.02f)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val outside = pixels[(map.left + 4.5.dp.px()).toInt(), (map.top + 4.5.dp.px()).toInt()]
        val inside = pixels[(map.left + 7.dp.px()).toInt(), (map.top + 7.dp.px()).toInt()]
        assertEquals(scheme.surfaceContainerLow to scheme.surfaceContainerHighest, outside to inside)
    }

    @Test
    fun `the place reads city and country with its flag in the emphasized title`() {
        show(located)

        val place = compose.onNodeWithText("Barcelona, Spain", useUnmergedTree = true)
        val style = place.textLayout().layoutInput.style
        assertEquals(typography.titleMediumEmphasized.fontSize, style.fontSize)
        assertEquals(typography.titleMediumEmphasized.fontWeight, style.fontWeight)
        val flag = hasTestTag(FlagTestTag) and hasAnyAncestor(hasTestTag(WhereCardTestTag))
        compose.onNode(flag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a place with no city reads its title alone`() {
        show(located.copy(place = DetailPlace(title = "Spain", country = null, flag = "🇪🇸")))

        val inCard = hasAnyAncestor(hasTestTag(WhereCardTestTag))
        compose.onNode(hasText("Spain") and inCard, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Spain,", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `the source and the accuracy read on one quiet line`() {
        show(located)

        val line = compose.onNodeWithText("Current location · ±12 m", useUnmergedTree = true)
        val style = line.textLayout().layoutInput.style
        assertEquals(typography.bodyMedium.fontSize, style.fontSize)
        assertEquals(scheme.onSurfaceVariant, style.color)
    }

    @Test
    fun `with no accuracy the line reads the source alone`() {
        show(located.copy(accuracyMeters = null))

        compose.onNodeWithText("Current location", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the coordinates are small and quiet, with no map mark beside them`() {
        show(located)

        compose.onNodeWithTag(WhereCardTestTag).performScrollTo()
        val coordinates = compose.onNodeWithText("41.40150, 2.16000", useUnmergedTree = true)
        val style = coordinates.textLayout().layoutInput.style
        assertEquals(typography.bodySmall.fontSize, style.fontSize)
        assertEquals(scheme.onSurfaceVariant, style.color)
        val end = coordinates.bounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.surfaceContainerLow, pixels[(end.right + 14.dp.px()).toInt(), end.center.y.toInt()])
    }

    @Test
    fun `a cat on the map shows the filled pill, and the whole card is one button that opens the map`() {
        show(located)

        compose.onNodeWithTag(WhereCardTestTag).performScrollTo()
        val pill = compose.onNodeWithTag(WherePillTestTag, useUnmergedTree = true)
        compose.onNodeWithText(context.getString(R.string.detail_show_on_map), useUnmergedTree = true).assertExists()
        val box = pill.bounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.primary, pixels[(box.left + 6.dp.px()).toInt(), box.center.y.toInt()])
        val card = compose.onNodeWithTag(WhereCardTestTag).fetchSemanticsNode().config
        assertEquals(Role.Button, card[SemanticsProperties.Role])
        assertTrue(SemanticsActions.OnClick in card)
        val clickableInside = hasClickAction() and hasAnyAncestor(hasTestTag(WhereCardTestTag))
        compose.onAllNodes(clickableInside, useUnmergedTree = true).assertCountEquals(0)

        pill.performClick()
        compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true).performClick()

        assertEquals(listOf("map cat-7", "map cat-7"), taps)
    }

    @Test
    fun `a cat off the globe shows no map and no pill, and a tap on its card sends nothing`() {
        show(offTheGlobe)

        val card = compose.onNodeWithTag(WhereCardTestTag).performScrollTo()
        compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(WherePillTestTag, useUnmergedTree = true).assertDoesNotExist()
        card.performClick()

        assertEquals(emptyList(), taps)
    }

    @Test
    fun `a cat with no location has no where card, and a notice under its facts says so`() {
        show(unlocated)

        compose.onAllNodesWithText(context.getString(R.string.detail_where_you_met), useUnmergedTree = true)
            .assertCountEquals(0)
        val notice = compose.onNodeWithTag(NoLocationNoticeTestTag).performScrollTo().bounds()
        val facts = compose.onNodeWithTag(DetailFactsTestTag).bounds()
        assertTrue(notice.top >= facts.bottom, "the notice sits under the facts: $notice, $facts")
        compose.onNodeWithText(context.getString(R.string.detail_no_location_title), useUnmergedTree = true)
            .assertExists()
        val body = "It was logged without a fix, so it is not on the map or in Places."
        compose.onNodeWithText(body, useUnmergedTree = true).assertExists()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.tertiaryContainer, pixels[(notice.left + 19.dp.px()).toInt(), notice.center.y.toInt()])
    }

    @Test
    fun `TalkBack reads the notice's lines as one item, and set on map is a control of its own`() {
        show(unlocated)

        val title = context.getString(R.string.detail_no_location_title)
        val body = context.getString(R.string.detail_no_location_body)
        val lines = compose.onNodeWithText(title).performScrollTo().fetchSemanticsNode().config
        assertEquals(listOf(title, body), lines[SemanticsProperties.Text].map { it.text })
        compose.onNodeWithText(context.getString(R.string.detail_set_location)).performClick()

        assertEquals(listOf("set cat-8"), taps)
    }

    @Test
    fun `a cat with a location shows no notice`() {
        show(located)

        compose.onNodeWithTag(NoLocationNoticeTestTag).assertDoesNotExist()
    }

    @Test
    fun `the spot map draws the fix's accuracy to scale around the dot`() {
        show(located.copy(accuracyMeters = 100))

        compose.onNodeWithTag(WhereCardTestTag).performScrollTo()
        val map = compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true).bounds()
        val radius = (100 / metersPerDp(latitude = 41.4015)).dp.px()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val disc = scheme.primary.copy(alpha = 0.16f).compositeOver(scheme.surfaceContainerHighest)
        assertClose(disc, pixels[(map.center.x + radius * 0.7f).toInt(), map.center.y.toInt()])
        assertClose(scheme.primary, pixels[(map.center.x + radius - 0.75.dp.px()).toInt(), map.center.y.toInt()])
        val beyond = pixels[(map.center.x + radius + 3.dp.px()).toInt(), map.center.y.toInt()]
        assertEquals(scheme.surfaceContainerHighest, beyond)
    }

    @Test
    fun `an accuracy smaller than the dot, or none, draws no circle`() {
        show(located)

        compose.onNodeWithTag(WhereCardTestTag).performScrollTo()
        compose.onNodeWithTag(AccuracyCircleTestTag, useUnmergedTree = true).assertDoesNotExist()
        val map = compose.onNodeWithTag(SpotMapTestTag, useUnmergedTree = true).bounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.surfaceContainerHighest, pixels[(map.center.x + 12.dp.px()).toInt(), map.center.y.toInt()])
    }

    @Test
    fun `a cat with no accuracy draws no circle`() {
        show(located.copy(accuracyMeters = null))

        compose.onNodeWithTag(WhereCardTestTag).performScrollTo()
        compose.onNodeWithTag(AccuracyCircleTestTag, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertClose(expected: Color, actual: Color) {
        val channels = listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)
        assertTrue(channels.all { (e, a) -> abs(e - a) <= 2f / 255 }, "expected $expected, was $actual")
    }

    private fun metersPerDp(latitude: Double) = cos(Math.toRadians(latitude)) * 2 * PI * 6378137.0 / (32768 * 512)

    private fun show(page: CatPage) {
        compose.setContent {
            // The spot map cannot start on the JVM.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CatsRadarTheme {
                    scheme = MaterialTheme.colorScheme
                    typography = MaterialTheme.typography
                    Surface {
                        EncounterDetailScreen(
                            state = loadedWith(page),
                            contentPadding = PaddingValues(),
                            onCoordinatesClick = { taps += "map $it" },
                            onSetLocationClick = { taps += "set $it" },
                        )
                    }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun Dp.px(): Float = with(compose.density) { toPx() }

    private companion object {
        val located = CatPage(
            id = "cat-7",
            dayLabel = "Yesterday",
            timeLabel = "4:12 PM",
            location = LocationLabel.CURRENT,
            coordinatesLabel = "41.40150, 2.16000",
            accuracyMeters = 12,
            coat = CoatOption.GINGER_WHITE,
            mapPosition = MapPosition(latitude = 41.4015, longitude = 2.16),
            place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
        )
        val offTheGlobe = located.copy(coordinatesLabel = "95.00000, 2.16000", mapPosition = null, place = null)
        val unlocated = CatPage(
            id = "cat-8",
            dayLabel = "Sep 24, 2026",
            timeLabel = "11:17 PM",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        )
    }
}
