package dev.catsradar.app.encounters

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
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
import dev.catsradar.presentation.regions.RegionsState
import dev.catsradar.ui.R
import dev.catsradar.ui.encounters.CellLeadTestTag
import dev.catsradar.ui.encounters.EncountersScreen
import dev.catsradar.ui.encounters.OutingCardTestTag
import dev.catsradar.ui.encounters.OutingCatCardTestTag
import dev.catsradar.ui.encounters.SelectionBarTestTag
import dev.catsradar.ui.regions.RegionsScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1600dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class EncounterCellsLookTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var scheme: ColorScheme

    private fun show(state: EncountersState) {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                Surface { EncountersScreen(state = state) }
            }
        }
    }

    private fun leads() = compose.onAllNodesWithTag(CellLeadTestTag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.pixelAtFraction(x: Float, y: Float): Color {
        val pixels = captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
    }

    private fun pixelAt(x: Dp, y: Dp): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return with(compose.density) { pixels[x.roundToPx(), y.roundToPx()] }
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
    }

    @Test
    fun `a chosen tile's ring follows its shape`() {
        show(grid(gingerSelected = true))

        assertEquals(scheme.primary, leads()[0].pixelAtFraction(0.01f, 0.5f), "the ring on the circle's edge")
        assertEquals(scheme.surfaceContainerLow, leads()[0].pixelAtFraction(0.12f, 0.12f), "no ring off the circle")
        // At a quarter height the circle's edge is well in from the side, where a square's ring would run.
        assertEquals(scheme.surfaceContainerLow, leads()[0].pixelAtFraction(0.01f, 0.25f), "no square ring")
    }

    @Test
    fun `a pair's time sits on a dark chip`() {
        show(grid())

        val time = compose.onNodeWithText("4:58 PM", useUnmergedTree = true)
        assertEquals(scheme.inverseOnSurface, time.textColor())
        val bounds = time.getUnclippedBoundsInRoot()
        assertEquals(scheme.inverseSurface, pixelAt(bounds.left + 3.dp, bounds.top + (bounds.bottom - bounds.top) / 2))
    }

    @Test
    fun `a cat's card names its coat, or a cat, or the photo's cats, and its time and place`() {
        show(list)

        listOf("Ginger & white", "A cat", "Photo of 3 cats").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
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
    fun `the list's cats are separate cards with medium corners inside the outing's card`() {
        show(list)

        val pieces = compose.onAllNodesWithTag(OutingCardTestTag, useUnmergedTree = true)
        val first = pieces[1].getUnclippedBoundsInRoot()
        val centre = first.left + (first.right - first.left) / 2
        assertEquals(scheme.surfaceContainerLow, pixelAt(centre, first.bottom - 3.dp), "a gap between two cats")
        val card = compose.onAllNodesWithTag(OutingCatCardTestTag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertEquals(scheme.surfaceContainerLow, pixelAt(card.left + 2.dp, card.top + 2.dp), "the card's round corner")
        assertEquals(scheme.surface, pixelAt(centre, card.top + 2.dp), "the card itself")
    }

    @Test
    fun `a short run's cards name their coats too`() {
        show(grid())

        compose.onNodeWithText("Brown").assertIsDisplayed()
        compose.onNodeWithText("4:10 PM · Current location", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `while selecting, the bar is on the primary container`() {
        show(grid(gingerSelected = true).copy(selectedIds = persistentSetOf("c1")))

        val bar = compose.onAllNodesWithTag(SelectionBarTestTag, useUnmergedTree = true)[0]
        assertEquals(scheme.primaryContainer, bar.pixelAtFraction(0.5f, 0.05f))
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.encounters_selected, 1, 1))
            .assertIsDisplayed()
    }

    @Test
    fun `the Places list keeps its cards, the time for a title and no coat's name`() {
        compose.setContent {
            CatsRadarTheme { Surface { RegionsScreen(state = RegionsState.Cats(header = null, rows = list.rows)) } }
        }

        compose.onNodeWithText("4:58 PM").assertIsDisplayed()
        compose.onAllNodesWithText("Ginger & white").assertCountEquals(0)
        compose.onAllNodesWithTag(OutingCatCardTestTag, useUnmergedTree = true).assertCountEquals(0)
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
                    EncounterCell("c3", "4:21 PM", LocationLabel.NONE),
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
                EncounterCell("a1", "4:58 PM", LocationLabel.CURRENT, CellLead.Coat(CoatOption.GINGER_WHITE)),
                GroupPosition.FIRST,
            ),
            EncountersRow.Single(EncounterCell("a2", "4:51 PM", LocationLabel.NONE), GroupPosition.MIDDLE),
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
        totals = EncountersTotals(cats = 5, outings = 1),
    )
}
