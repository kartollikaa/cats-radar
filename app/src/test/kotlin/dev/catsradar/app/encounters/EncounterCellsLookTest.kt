package dev.catsradar.app.encounters

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
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
import dev.catsradar.presentation.encounters.PhotoCell
import dev.catsradar.presentation.map.MapSpotState
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.ui.R
import dev.catsradar.ui.encounters.CellLeadTestTag
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.encounters.OutingCardTestTag
import dev.catsradar.ui.encounters.OutingCatCardTestTag
import dev.catsradar.ui.encounters.SelectionBadgeTestTag
import dev.catsradar.ui.encounters.SelectionBarTestTag
import dev.catsradar.ui.map.MapSpotScreen
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
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
class EncounterCellsLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var scheme: ColorScheme

    private fun show(
        state: EncountersState,
        onEncounterClick: (String) -> Unit = {},
        onSelectionDismiss: () -> Unit = {},
        onDeleteSelectedClick: () -> Unit = {},
    ) = showElsewhere {
        EncountersScreen(
            state = state,
            onEncounterClick = onEncounterClick,
            onSelectionDismiss = onSelectionDismiss,
            onDeleteSelectedClick = onDeleteSelectedClick,
        )
    }

    private fun showElsewhere(content: @Composable () -> Unit) {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                Surface { content() }
            }
        }
    }

    private fun leads() = compose.onAllNodesWithTag(CellLeadTestTag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.pixelAtFraction(x: Float, y: Float): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
    }

    private fun screen() = compose.onRoot().captureToImage().toPixelMap()

    private fun PixelMap.at(x: Dp, y: Dp): Color = with(compose.density) { this@at[x.roundToPx(), y.roundToPx()] }

    // Only a medium corner splits these two points: a small one covers the first, a large one misses the second.
    private fun PixelMap.assertMediumCorner(box: DpRect, outside: Color, inside: Color) {
        assertEquals(outside, at(box.left + 4.5.dp, box.top + 4.5.dp), "outside a medium corner")
        assertEquals(inside, at(box.left + 7.dp, box.top + 7.dp), "inside a medium corner")
    }

    private fun SemanticsNodeInteraction.textColor(): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single().layoutInput.style.color
    }

    @Test
    fun `a run's tiles take their coats' own shapes`() {
        show(grid())

        // Outside a circle yet inside a rounded square: the card shows around Ginger, the tile fills Ginger & white.
        assertEquals(scheme.surfaceContainerLow, leads()[0].pixelAtFraction(0.12f, 0.12f), "Ginger, a circle")
        assertEquals(scheme.surfaceContainerHighest, leads()[1].pixelAtFraction(0.12f, 0.12f), "Ginger & white")
        assertEquals(scheme.surfaceContainerHighest, leads()[1].pixelAtFraction(0.5f, 0.04f), "Ginger & white's top")
        assertEquals(scheme.surfaceContainerHighest, leads()[2].pixelAtFraction(0.12f, 0.12f), "Ginger & white's photo")
    }

    @Test
    fun `a tile with no coat, and a shot's tile, take the no-coat shape`() {
        show(grid())

        // The no-coat shape's hem: a bump near each bottom corner, where a circle is empty, and a notch between them.
        listOf(3 to "a cat with no coat", 4 to "a shot").forEach { (index, tile) ->
            assertEquals(scheme.surfaceContainerLow, leads()[index].pixelAtFraction(0.12f, 0.12f), "$tile: no square")
            assertEquals(scheme.surfaceContainerHighest, leads()[index].pixelAtFraction(0.8f, 0.92f), "$tile: the hem")
            assertEquals(scheme.surfaceContainerLow, leads()[index].pixelAtFraction(0.5f, 0.92f), "$tile: its notch")
        }
    }

    @Test
    fun `a chosen tile's ring follows its shape`() {
        show(grid(gingerSelected = true))

        assertEquals(scheme.primary, leads()[0].pixelAtFraction(0.01f, 0.5f), "the ring on the circle's edge")
        assertEquals(scheme.surfaceContainerLow, leads()[0].pixelAtFraction(0.12f, 0.12f), "no ring off the circle")
        // At a quarter height the circle's edge is well in from the side, where a square's ring would run.
        assertEquals(scheme.surfaceContainerLow, leads()[0].pixelAtFraction(0.01f, 0.25f), "no square ring")
        val checks = compose.onAllNodesWithTag(SelectionBadgeTestTag, useUnmergedTree = true)
        checks.assertCountEquals(1)
        val tile = leads()[0].getUnclippedBoundsInRoot()
        val check = checks[0].getUnclippedBoundsInRoot()
        val onTopEnd = check.left > tile.left + (tile.right - tile.left) / 2 && check.right <= tile.right &&
            check.top >= tile.top && check.bottom < tile.top + (tile.bottom - tile.top) / 2
        assertTrue(onTopEnd, "the check at the chosen tile's top end: $check in $tile")
    }

    @Test
    fun `a pair keeps medium corners, its time on a dark chip over its bottom-start corner`() {
        show(grid())

        val time = compose.onNodeWithText("4:58 PM", useUnmergedTree = true)
        assertEquals(scheme.inverseOnSurface, time.textColor())
        val bounds = time.getUnclippedBoundsInRoot()
        val middle = bounds.top + (bounds.bottom - bounds.top) / 2
        val tile = compose.onNodeWithContentDescription("4:58 PM", substring = true).getUnclippedBoundsInRoot()
        val pixels = screen()
        assertEquals(scheme.inverseSurface, pixels.at(bounds.left - 4.dp, middle))
        val overBottomStart = bounds.right < tile.left + (tile.right - tile.left) / 2 &&
            bounds.top > tile.top + (tile.bottom - tile.top) / 2
        assertTrue(overBottomStart, "the chip at the pair tile's bottom-start corner: $bounds in $tile")
        pixels.assertMediumCorner(tile, outside = scheme.surfaceContainerLow, inside = scheme.surfaceContainerHighest)
    }

    @Test
    fun `a cat's card names its coat, or a cat, or the photo's cats, and its time and place`() {
        show(list)

        listOf("Ginger", "A cat", "Black", "Photo of 3 cats").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("4:58 PM · Current location", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("4:51 PM · No location yet", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `no location yet takes the tertiary container's text colour, and a place does not`() {
        show(list)

        val none = compose.onNodeWithText("4:51 PM · No location yet", useUnmergedTree = true).textColor()
        val placed = compose.onNodeWithText("4:58 PM · Current location", useUnmergedTree = true).textColor()
        assertEquals(scheme.onTertiaryContainer to scheme.onSurfaceVariant, none to placed)
    }

    @Test
    fun `a screen reader hears each cat's card name it once`() {
        show(list)

        listOf("Ginger", "Black", "Photo of 3 cats").forEach { name ->
            val card = compose.onNodeWithText(name).fetchSemanticsNode().config
            val spoken = card.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } +
                card.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
            assertEquals(1, spoken.count { it == name }, "$name in $spoken")
        }
    }

    @Test
    fun `a chosen cat's card rings its lead`() {
        val rows = list.rows.map { it.chosen("a1") }.toPersistentList()
        show(list.copy(rows = rows, selectedIds = persistentSetOf("a1")))

        assertEquals(scheme.primary, leads()[0].pixelAtFraction(0.02f, 0.5f), "the ring on the chosen cat's lead")
        assertEquals(scheme.surfaceContainerHighest, leads()[1].pixelAtFraction(0.02f, 0.5f), "no ring on the next")
        assertEquals(scheme.secondaryContainer, leads()[0].pixelAtFraction(0.02f, 0.25f), "no ring off the circle")
    }

    @Test
    fun `a cat's card leads with its tile's shape`() {
        show(list)

        assertEquals(scheme.surface, leads()[0].pixelAtFraction(0.12f, 0.12f), "Ginger's circle, not a square")
        assertEquals(scheme.surfaceContainerHighest, leads()[0].pixelAtFraction(0.5f, 0.04f), "the circle's top")
        assertEquals(scheme.surface, leads()[1].pixelAtFraction(0.12f, 0.12f), "the no-coat shape, not a square")
    }

    @Test
    fun `a tap on a list card opens its cat`() {
        val opened = mutableListOf<String>()
        show(list, onEncounterClick = { opened += it })

        compose.onNodeWithText("Ginger").performClick()

        assertEquals(listOf("a1"), opened)
    }

    @Test
    fun `a tap on a short run's card opens its cat`() {
        val opened = mutableListOf<String>()
        show(grid(), onEncounterClick = { opened += it })

        compose.onNodeWithText("Brown").performClick()

        assertEquals(listOf("c4"), opened)
    }

    @Test
    fun `the list's cats are separate cards with medium corners inside the outing's card`() {
        show(list)

        val pieces = compose.onAllNodesWithTag(OutingCardTestTag, useUnmergedTree = true)
        val first = pieces[1].getUnclippedBoundsInRoot()
        val centre = first.left + (first.right - first.left) / 2
        val card = compose.onAllNodesWithTag(OutingCatCardTestTag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val pixels = screen()
        assertEquals(scheme.surfaceContainerLow, pixels.at(centre, first.bottom - 3.dp), "a gap between two cats")
        assertEquals(scheme.surfaceContainerLow, pixels.at(card.left + 2.dp, card.top + 2.dp), "the card's corner")
        pixels.assertMediumCorner(card, outside = scheme.surfaceContainerLow, inside = scheme.surface)
        assertEquals(scheme.surface, pixels.at(centre, card.top + 2.dp), "the card itself")
    }

    @Test
    fun `a short run's cards name their coats too`() {
        show(grid())

        compose.onNodeWithText("Brown").assertIsDisplayed()
        compose.onNodeWithText("4:10 PM · Current location", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `while selecting, the bar is on the primary container, and closes and deletes`() {
        var dismissed = 0
        var deleted = 0
        show(
            grid(gingerSelected = true).copy(selectedIds = persistentSetOf("c1")),
            onSelectionDismiss = { dismissed++ },
            onDeleteSelectedClick = { deleted++ },
        )

        val bar = compose.onAllNodesWithTag(SelectionBarTestTag, useUnmergedTree = true)[0]
        assertEquals(scheme.primaryContainer, bar.pixelAtFraction(0.5f, 0.05f))
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.encounters_selected, 1, 1))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.encounters_selection_close)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.encounters_delete_selected)).performClick()
        assertEquals(1 to 1, dismissed to deleted)
    }

    @Test
    fun `the Places list keeps its cards, the time for a title and no coat's name or shape`() {
        showElsewhere { RegionsScreen(state = RegionsState.Cats(header = null, rows = list.rows)) }

        assertKeepsItsCards()
    }

    @Test
    fun `the map's spot sheet keeps its cards, the time for a title and no coat's name or shape`() {
        showElsewhere { MapSpotScreen(state = MapSpotState.Listed(catCount = 6, rows = list.rows)) }

        assertKeepsItsCards()
    }

    private fun assertKeepsItsCards() {
        compose.onNodeWithText("4:58 PM").assertIsDisplayed()
        compose.onAllNodesWithText("Ginger").assertCountEquals(0)
        compose.onAllNodesWithTag(OutingCatCardTestTag, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(scheme.surfaceContainerHighest, leads()[0].pixelAtFraction(0.12f, 0.12f), "a square, not a circle")
    }

    private val header = OutingHeader(
        key = "h1",
        label = "Today, 4:10 PM",
        dayLabel = "Today",
        startLabel = "4:10 PM",
        count = 6,
        spanLabel = "48 min",
    )

    private fun grid(gingerSelected: Boolean = false) = EncountersState(
        rows = persistentListOf(
            header,
            EncountersRow.PhotoPair(
                first = PhotoCell("p1", "4:58 PM", LocationLabel.CURRENT, "/photos/p1.jpg", "/photos/p1_thumb.jpg"),
                second = PhotoCell("p2", "4:51 PM", LocationLabel.CURRENT, "/photos/p2.jpg", "/photos/p2_thumb.jpg"),
            ),
            EncountersRow.Tiles(
                persistentListOf(
                    EncounterCell("c1", "4:40 PM", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER))
                        .copy(selected = gingerSelected),
                    EncounterCell("c2", "4:33 PM", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER_WHITE)),
                    EncounterCell(
                        "c3",
                        "4:21 PM",
                        LocationLabel.NONE,
                        CellLead.Photo("/photos/c3_thumb.jpg", CoatOption.GINGER_WHITE),
                    ),
                ),
            ),
            EncountersRow.Tiles(
                persistentListOf(
                    EncounterCell("c5", "4:18 PM", LocationLabel.CURRENT),
                    EncounterCell(
                        "c6",
                        "4:15 PM",
                        LocationLabel.CURRENT,
                        CellLead.Photo("/photos/c6_thumb.jpg"),
                        catIds = persistentListOf("c6", "c7", "c8"),
                    ),
                    EncounterCell("c9", "4:12 PM", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GREY)),
                ),
            ),
            EncountersRow.Cards(
                persistentListOf(
                    EncounterCell("c4", "4:10 PM", LocationLabel.CURRENT, CellLead.Coat(CoatOption.BROWN))
                ),
                closesOuting = true,
            ),
        ),
        totals = EncountersTotals(cats = 6, outings = 1),
    )

    private val list = EncountersState(
        rows = persistentListOf(
            header,
            EncountersRow.Single(
                EncounterCell("a1", "4:58 PM", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER)),
                GroupPosition.FIRST,
            ),
            EncountersRow.Single(EncounterCell("a2", "4:51 PM", LocationLabel.NONE), GroupPosition.MIDDLE),
            EncountersRow.Single(
                EncounterCell(
                    "a6",
                    "4:45 PM",
                    LocationLabel.FROM_OUTING,
                    CellLead.Photo("/photos/a6_thumb.jpg", CoatOption.BLACK),
                ),
                GroupPosition.MIDDLE,
            ),
            EncountersRow.Single(
                EncounterCell(
                    "a3",
                    "4:40 PM",
                    LocationLabel.CURRENT,
                    CellLead.Photo("/photos/a3_thumb.jpg"),
                    catIds = persistentListOf("a3", "a4", "a5"),
                ),
                GroupPosition.LAST,
            ),
        ),
        layout = EncountersLayout.LIST,
        totals = EncountersTotals(cats = 6, outings = 1),
    )

    private fun EncountersRow.chosen(id: String) =
        if (this is EncountersRow.Single && cell.id == id) copy(cell = cell.copy(selected = true)) else this
}
