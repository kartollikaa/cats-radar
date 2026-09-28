package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.titleRes
import dev.catsradar.ui.components.FlagTestTag
import dev.catsradar.ui.components.OutlinedLabelTestTag
import dev.catsradar.ui.detail.DetailFactsTestTag
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class DetailNamesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private lateinit var scheme: ColorScheme
    private lateinit var typography: Typography
    private val taps = mutableListOf<String>()
    private var state by mutableStateOf<EncounterDetailState>(EncounterDetailState.Loading)

    @Test
    fun `a cat is titled by its coat in the emphasized headline`() {
        show(placed)

        val title = compose.onNodeWithText("Ginger & white cat")
        title.assertIsDisplayed().assert(isHeading())
        val style = title.textLayout().layoutInput.style
        assertEquals(typography.headlineMediumEmphasized.fontSize, style.fontSize)
        assertEquals(typography.headlineMediumEmphasized.fontWeight, style.fontWeight)
    }

    @Test
    fun `the title and the facts start in line with the back arrow`() {
        show(placed)

        val back = compose.onNodeWithContentDescription(context.getString(R.string.detail_back)).bounds()
        val title = compose.onNodeWithText("Ginger & white cat").bounds()
        val facts = compose.onNodeWithTag(DetailFactsTestTag).bounds()
        assertEquals(back.left, title.left, 1f)
        assertEquals(back.left, facts.left, 1f)
    }

    @Test
    fun `a cat with no coat noted is a cat`() {
        show(bare)

        compose.onNodeWithText("A cat").assertIsDisplayed()
    }

    @Test
    fun `every coat has a title of its own, each a cat`() {
        val titles = (CoatOption.entries + null).map { it.titleRes() }

        assertEquals(titles.size, titles.distinct().size)
        assertEquals("A cat", context.getString(null.titleRes()))
        titles.forEach { assertTrue(context.getString(it).endsWith("cat"), context.getString(it)) }
    }

    @Test
    fun `the facts row shows the day, the time and the place, each an outlined label at least 32 dp tall`() {
        show(placed)

        val day = compose.onNodeWithText("Yesterday", useUnmergedTree = true).bounds()
        val time = compose.onNodeWithText("4:12 PM", useUnmergedTree = true).bounds()
        assertTrue(day.right < time.left, "the day comes first: $day, $time")
        val labels = compose.onAllNodesWithTag(OutlinedLabelTestTag, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(3, labels.size)
        labels.forEach { assertTrue(it.boundsInRoot.height >= 32.dp.px() - 1f, "${it.boundsInRoot}") }
        val flagInFacts = hasTestTag(FlagTestTag) and hasAnyAncestor(hasTestTag(DetailFactsTestTag))
        compose.onNode(flagInFacts, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a cat with no named place has no place label`() {
        show(bare)

        compose.onAllNodesWithTag(OutlinedLabelTestTag, useUnmergedTree = true).assertCountEquals(2)
        assertEquals(listOf("Sep 24, 2026", "11:17 PM"), factsSpoken())
    }

    @Test
    fun `TalkBack reads the facts as one item, the flag left out`() {
        show(placed)

        assertEquals(listOf("Yesterday", "4:12 PM", "Barcelona"), factsSpoken())
    }

    @Test
    fun `the day over a large time is gone`() {
        show(placed)

        compose.onAllNodesWithText("4:12 PM", useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            assertTrue(layouts.single().layoutInput.style.fontSize < typography.displayMedium.fontSize)
        }
    }

    @Test
    fun `more sits at the bar's end in the back arrow's circle`() {
        show(placed)

        val more = more().assertIsDisplayed().bounds()
        val back = compose.onNodeWithContentDescription(context.getString(R.string.detail_back)).bounds()
        val screen = compose.onRoot().bounds()
        assertTrue(more.center.x > screen.center.x && more.right <= screen.right, "$more in $screen")
        assertEquals(back.center.y, more.center.y, 1f)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val tone = { box: Rect -> pixels[(box.left + 10.dp.px()).toInt(), box.center.y.toInt()] }
        assertEquals(tone(back), tone(more))
        assertEquals(scheme.secondaryContainer, tone(more))
    }

    @Test
    fun `a removed or missing cat has no more`() {
        show(EncounterDetailState.Deleted(undoVisible = true))
        more().assertDoesNotExist()

        state = EncounterDetailState.Missing
        compose.waitForIdle()
        more().assertDoesNotExist()
    }

    @Test
    fun `more opens show on the map and remove this cat`() {
        show(placed)

        more().performClick()

        menuItem(R.string.detail_show_on_map).assertIsDisplayed()
        menuItem(R.string.detail_remove).assertIsDisplayed()
    }

    @Test
    fun `show on the map opens the map on the cat on screen`() {
        show(placed)

        more().performClick()
        menuItem(R.string.detail_show_on_map).performClick()

        assertEquals(listOf("map cat-7"), taps)
    }

    @Test
    fun `among several cats, more acts on the cat on screen`() {
        show(loadedOn(placedPage, barePage, placedPage))
        more().performClick()
        menuItem(R.string.detail_show_on_map).performClick()

        state = loadedOn(barePage, placedPage, barePage)
        compose.waitForIdle()
        more().performClick()

        assertEquals(listOf("map cat-7"), taps)
        menuItem(R.string.detail_show_on_map).assertIsNotEnabled()
    }

    @Test
    fun `a cat the map does not draw cannot be shown on the map`() {
        show(bare)

        more().performClick()

        menuItem(R.string.detail_show_on_map).assertIsNotEnabled()
    }

    @Test
    fun `the menu's remove this cat removes it`() {
        show(placed)

        more().performClick()
        menuItem(R.string.detail_remove).performClick()

        assertEquals(listOf("delete"), taps)
    }

    @Test
    fun `the page ends with a tonal remove this cat, centred, on the error container`() {
        show(placed)

        val remove = compose.onNodeWithText(context.getString(R.string.detail_remove)).performScrollTo()
        val box = remove.bounds()
        val screen = compose.onRoot().bounds()
        assertEquals(screen.center.x, box.center.x, 1.dp.px())
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.errorContainer, pixels[(box.left + 8.dp.px()).toInt(), box.center.y.toInt()])
        compose.onAllNodesWithText("Delete").assertCountEquals(0)
        remove.performClick()
        assertEquals(listOf("delete"), taps)
    }

    private fun show(cat: EncounterDetailState) {
        state = cat
        compose.setContent {
            // The spot map cannot start on the JVM.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CatsRadarTheme {
                    scheme = MaterialTheme.colorScheme
                    typography = MaterialTheme.typography
                    Surface {
                        EncounterDetailScreen(
                            state = state,
                            contentPadding = PaddingValues(),
                            onCoordinatesClick = { taps += "map $it" },
                            onDeleteClick = { taps += "delete" },
                        )
                    }
                }
            }
        }
    }

    private fun more() = compose.onNodeWithContentDescription(context.getString(R.string.detail_more))

    private fun menuItem(label: Int) =
        compose.onNode(hasText(context.getString(label)) and hasAnyAncestor(isPopup()))

    private fun factsSpoken(): List<String> =
        compose.onNodeWithTag(DetailFactsTestTag).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun Dp.px(): Float = with(compose.density) { toPx() }

    private companion object {
        val placedPage = CatPage(
            id = "cat-7",
            dayLabel = "Yesterday",
            timeLabel = "4:12 PM",
            location = LocationLabel.CURRENT,
            coordinatesLabel = "41.40150, 2.16000",
            accuracyMeters = 10,
            coat = CoatOption.GINGER_WHITE,
            mapPosition = MapPosition(latitude = 41.4015, longitude = 2.16),
            place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
        )
        val barePage = CatPage(
            id = "cat-8",
            dayLabel = "Sep 24, 2026",
            timeLabel = "11:17 PM",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        )
        val placed = loadedWith(placedPage)
        val bare = loadedWith(barePage)
    }
}
