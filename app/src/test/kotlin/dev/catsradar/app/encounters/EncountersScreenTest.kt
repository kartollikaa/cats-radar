package dev.catsradar.app.encounters

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.encounters.CellLead
import dev.catsradar.presentation.encounters.EncounterCell
import dev.catsradar.presentation.encounters.EncountersLayout
import dev.catsradar.presentation.encounters.EncountersRow
import dev.catsradar.presentation.encounters.EncountersState
import dev.catsradar.presentation.encounters.EncountersTotals
import dev.catsradar.presentation.encounters.GroupPosition
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.presentation.encounters.OutingHeader
import dev.catsradar.presentation.map.MapSpotState
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.ui.R
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.encounters.MapPillIconTestTag
import dev.catsradar.ui.encounters.OutingCardTestTag
import dev.catsradar.ui.encounters.WalkChipTestTag
import dev.catsradar.ui.map.MapSpotScreen
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class EncountersScreenTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var scheme: ColorScheme
    private lateinit var typography: Typography

    private fun show(state: EncountersState, onMap: (String) -> Unit = {}) {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                typography = MaterialTheme.typography
                Surface { EncountersScreen(state = state, onOutingMapClick = onMap) }
            }
        }
    }

    private fun cards() = compose.onAllNodesWithTag(OutingCardTestTag, useUnmergedTree = true)

    private fun cardPieces(): List<DpRect> =
        List(cards().fetchSemanticsNodes().size) { cards()[it].getUnclippedBoundsInRoot() }

    private fun pixelAt(x: Dp, y: Dp): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return with(compose.density) { pixels[x.roundToPx(), y.roundToPx()] }
    }

    private fun SemanticsNodeInteraction.style(): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single().layoutInput.style
    }

    private fun SemanticsNodeInteraction.startPixel(): Color {
        val pixels = captureToImage().toPixelMap()
        return with(compose.density) { pixels[3.dp.roundToPx(), pixels.height / 2] }
    }

    @Test
    fun `the tab opens on its title and its totals`() {
        show(grid)

        val title = compose.onNodeWithText(context.getString(R.string.tab_encounters))
        title.assertIsDisplayed()
        compose.onNodeWithText("9 cats · 2 outings").assertIsDisplayed()
        assertEquals(
            typography.headlineMediumEmphasized.fontSize to typography.headlineMediumEmphasized.fontWeight,
            title.style().let { it.fontSize to it.fontWeight },
        )
        assertTrue(title.getUnclippedBoundsInRoot().bottom < cardPieces().first().top)
    }

    @Test
    fun `an outing's header names its day and its start, count and span`() {
        show(grid)

        val day = compose.onNodeWithText("Today")
        day.assertIsDisplayed()
        val summary = compose.onNodeWithText("4:12 PM · 6 cats · 48 min")
        summary.assertIsDisplayed()
        compose.onNodeWithText("2:32 PM · 3 cats").assertIsDisplayed()
        val underDay = summary.getUnclippedBoundsInRoot().top >= day.getUnclippedBoundsInRoot().bottom
        assertTrue(underDay, "summary under the day")
        assertEquals(
            typography.titleLargeEmphasized.fontSize to typography.titleLargeEmphasized.fontWeight,
            day.style().let { it.fontSize to it.fontWeight },
        )
    }

    @Test
    fun `an outing on a walk wears the walk chip and the other does not`() {
        show(grid)

        compose.onAllNodesWithText(context.getString(R.string.encounters_on_a_walk), useUnmergedTree = true)
            .assertCountEquals(1)
        val chip = compose.onAllNodesWithTag(WalkChipTestTag, useUnmergedTree = true)
        chip.assertCountEquals(1)
        assertEquals(scheme.tertiaryContainer, chip[0].startPixel())
        val walkedHeader = cardPieces().first()
        val chipBounds = chip[0].getUnclippedBoundsInRoot()
        val inWalkedHeader = chipBounds.top >= walkedHeader.top && chipBounds.bottom <= walkedHeader.bottom
        assertTrue(inWalkedHeader, "in the walked header")
    }

    @Test
    fun `On the map is a tonal pill that reports its outing, and an outing without a place has none`() {
        val reported = mutableListOf<String>()
        show(grid, onMap = { reported += it })

        val pills = compose.onAllNodesWithText(context.getString(R.string.encounters_outing_on_map))
        pills.assertCountEquals(1)
        assertEquals(scheme.secondaryContainer, pills[0].startPixel())
        val pill = pills[0].getUnclippedBoundsInRoot()
        val icon = compose.onAllNodesWithTag(MapPillIconTestTag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertEquals(18.dp, icon.right - icon.left, "the map icon")
        assertTrue(icon.left >= pill.left && icon.right <= pill.right, "the icon inside the pill")
        val day = compose.onNodeWithText("Today").getUnclippedBoundsInRoot()
        val header = cardPieces().first()
        assertTrue(pill.top < day.bottom && pill.bottom > day.top, "the pill on the day's line")
        assertTrue(header.right - pill.right <= 12.dp, "the pill at the header's end")
        pills[0].performClick()
        assertEquals(listOf("a6"), reported)
    }

    @Test
    fun `an outing's header and rows are one card, with the screen showing between two outings`() {
        var state by mutableStateOf(grid)
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                Surface { EncountersScreen(state = state) }
            }
        }

        listOf(grid, list).forEach { shown ->
            state = shown
            compose.waitForIdle()

            val pieces = cardPieces()
            val (first, second) = outingsOf(shown)
            (1 until first).forEach { assertEquals(pieces[it - 1].bottom, pieces[it].top, "${shown.layout} join $it") }
            val closing = pieces[first - 1]
            val centre = closing.left + (closing.right - closing.left) / 2
            assertEquals(scheme.surfaceContainerLow, pixelAt(centre, pieces[0].bottom - 2.dp), "${shown.layout} header")
            val rowGap = if (shown.layout == EncountersLayout.LIST) 2.dp else 8.dp
            assertEquals(
                scheme.surfaceContainerLow,
                pixelAt(centre, pieces[1].bottom - rowGap / 2),
                "${shown.layout} between two rows",
            )
            assertEquals(scheme.surfaceContainerLow, pixelAt(centre, closing.bottom - 2.dp), "${shown.layout} closing")
            val nextTop = pieces[first].top
            assertTrue(nextTop > closing.bottom, "${shown.layout} gap")
            assertEquals(scheme.surface, pixelAt(centre, closing.bottom + (nextTop - closing.bottom) / 2))
            assertEquals(first + second, pieces.size)
        }
    }

    @Test
    fun `the Places list draws its outings as before, without cards`() {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                Surface { RegionsScreen(state = RegionsState.Cats(header = null, rows = list.rows)) }
            }
        }

        assertDrawnWithoutCards()
    }

    @Test
    fun `the map's spot sheet draws its outings as before, without cards`() {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                Surface { MapSpotScreen(state = MapSpotState.Listed(catCount = 3, rows = list.rows)) }
            }
        }

        assertDrawnWithoutCards()
    }

    // The old header: its one-line label, and the screen's own background under it rather than a card's.
    private fun assertDrawnWithoutCards() {
        cards().assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.encounters_on_a_walk)).assertCountEquals(0)
        val label = compose.onNodeWithText("Today, 4:12 PM").getUnclippedBoundsInRoot()
        val underLabel = pixelAt(label.left + 2.dp, label.bottom + 2.dp)
        assertEquals(scheme.surface, underLabel)
    }

    // The number of card pieces each outing is drawn in: its header and each of its rows.
    private fun outingsOf(state: EncountersState): Pair<Int, Int> {
        val second = state.rows.indexOfLast { it is OutingHeader }
        return second to state.rows.size - second
    }

    private fun cell(id: String, time: String, coat: CoatOption? = null) =
        EncounterCell(id, time, LocationLabel.CURRENT, coat?.let { CellLead.Coat(it) } ?: CellLead.Paw)

    private val todayHeader = OutingHeader(
        key = "h1",
        label = "Today, 4:12 PM",
        mapOutingId = "a6",
        dayLabel = "Today",
        startLabel = "4:12 PM",
        count = 6,
        spanLabel = "48 min",
        onWalk = true,
    )

    private val yesterdayHeader = OutingHeader(
        key = "h2",
        label = "Yesterday, 2:32 PM",
        dayLabel = "Yesterday",
        startLabel = "2:32 PM",
        count = 3,
    )

    private val grid = EncountersState(
        rows = persistentListOf(
            todayHeader,
            EncountersRow.Tiles(
                persistentListOf(
                    cell("a1", "4:58 PM", CoatOption.GINGER_WHITE),
                    cell("a2", "4:51 PM", CoatOption.BLACK),
                    cell("a3", "4:40 PM"),
                ),
            ),
            EncountersRow.Tiles(
                persistentListOf(
                    cell("a4", "4:33 PM"),
                    cell("a5", "4:21 PM"),
                    cell("a6", "4:12 PM", CoatOption.GINGER)
                ),
                closesOuting = true,
            ),
            yesterdayHeader,
            EncountersRow.Cards(persistentListOf(cell("b1", "2:43 PM"), cell("b2", "2:32 PM")), closesOuting = true),
        ),
        totals = EncountersTotals(cats = 9, outings = 2),
    )

    private val list = EncountersState(
        rows = persistentListOf(
            todayHeader,
            EncountersRow.Single(cell("a1", "4:58 PM", CoatOption.GINGER_WHITE), GroupPosition.FIRST),
            EncountersRow.Single(cell("a2", "4:51 PM", CoatOption.BLACK), GroupPosition.LAST),
            yesterdayHeader,
            EncountersRow.Single(cell("b1", "2:32 PM"), GroupPosition.ONLY),
        ),
        layout = EncountersLayout.LIST,
        totals = EncountersTotals(cats = 3, outings = 2),
    )
}
