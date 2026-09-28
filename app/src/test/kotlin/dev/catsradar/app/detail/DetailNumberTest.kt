package dev.catsradar.app.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPlace
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.components.OutlinedLabelTestTag
import dev.catsradar.ui.detail.DetailFactsTestTag
import dev.catsradar.ui.detail.DetailNumberTestTag
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
class DetailNumberTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private lateinit var scheme: ColorScheme
    private lateinit var typography: Typography

    @Test
    fun `the facts row opens with the number, before the day`() {
        show(numberedPage)

        val number = compose.onNodeWithText("#62", useUnmergedTree = true).bounds()
        val label = numberLabel().bounds()
        val day = compose.onNodeWithText("Yesterday", useUnmergedTree = true).bounds()
        val facts = compose.onNodeWithTag(DetailFactsTestTag).bounds()
        assertEquals(facts.left, label.left, 1f)
        assertTrue(label.contains(number.center), "$number in $label")
        assertTrue(label.right < day.left, "the number comes first: $label, $day")
    }

    @Test
    fun `the number sits in a filled primary container label as tall as the outlined ones, with no border`() {
        show(numberedPage)

        val label = numberLabel().bounds()
        val outlined = compose.onAllNodesWithTag(OutlinedLabelTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        assertTrue(label.height >= 32.dp.px() - 1f, "$label")
        assertEquals(outlined.height, label.height, 1f)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val y = label.center.y.toInt()
        assertEquals(scheme.primaryContainer, pixels[(label.left + 0.5.dp.px()).toInt(), y])
        assertEquals(scheme.primaryContainer, pixels[(label.left + 4.dp.px()).toInt(), y])
        val style = compose.onNodeWithText("#62", useUnmergedTree = true).textLayout().layoutInput.style
        assertEquals(scheme.onPrimaryContainer, style.color)
        assertEquals(typography.labelLarge.fontSize, style.fontSize)
    }

    @Test
    fun `the number's label has the outlined labels' small corners`() {
        show(numberedPage)

        val label = numberLabel().bounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val outsideTheCurve = 0.4.dp.px()
        val insideTheCurve = 3.2.dp.px()
        val corner = pixels[(label.left + outsideTheCurve).toInt(), (label.top + outsideTheCurve).toInt()]
        assertEquals(scheme.surface, corner)
        val inside = pixels[(label.left + insideTheCurve).toInt(), (label.top + insideTheCurve).toInt()]
        assertEquals(scheme.primaryContainer, inside)
    }

    @Test
    fun `TalkBack says cat number 62 for the number, and the facts stay one item`() {
        show(numberedPage)

        val number = compose.onNodeWithText("#62", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(listOf("Cat number 62"), number.config[SemanticsProperties.ContentDescription])
        compose.onAllNodes(hasText("#62")).assertCountEquals(1)
        val facts = compose.onNodeWithTag(DetailFactsTestTag).fetchSemanticsNode().config
        assertEquals(listOf("Cat number 62"), facts[SemanticsProperties.ContentDescription])
        val texts = facts[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("#62", "Yesterday", "4:12 PM", "Barcelona"), texts)
        compose.onNode(hasText("#62") and hasTestTag(DetailFactsTestTag)).assertExists()
    }

    @Test
    fun `a cat with no number has no number label, and the row starts with the day`() {
        show(numberedPage.copy(numberInLog = null))

        numberLabel().assertDoesNotExist()
        val day = compose.onNodeWithText("Yesterday", useUnmergedTree = true).bounds()
        val facts = compose.onNodeWithTag(DetailFactsTestTag).bounds()
        val first = compose.onAllNodesWithTag(OutlinedLabelTestTag, useUnmergedTree = true)
            .fetchSemanticsNodes().minBy { it.boundsInRoot.left }.boundsInRoot
        assertEquals(facts.left, first.left, 1f)
        assertTrue(first.contains(day.center), "the day opens the row: $day in $first")
        val numberInFacts = hasText("#", substring = true) and hasAnyAncestor(hasTestTag(DetailFactsTestTag))
        compose.onAllNodes(numberInFacts, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun show(page: CatPage) {
        compose.setContent {
            // The spot map cannot start on the JVM.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CatsRadarTheme {
                    scheme = MaterialTheme.colorScheme
                    typography = MaterialTheme.typography
                    Surface {
                        EncounterDetailScreen(state = loadedWith(page), contentPadding = PaddingValues())
                    }
                }
            }
        }
    }

    private fun numberLabel() = compose.onNodeWithTag(DetailNumberTestTag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun Dp.px(): Float = with(compose.density) { toPx() }

    private companion object {
        val numberedPage = CatPage(
            id = "cat-7",
            dayLabel = "Yesterday",
            timeLabel = "4:12 PM",
            location = LocationLabel.CURRENT,
            coordinatesLabel = "41.40150, 2.16000",
            accuracyMeters = 10,
            coat = CoatOption.GINGER_WHITE,
            place = DetailPlace(title = "Barcelona", country = "Spain", flag = "🇪🇸"),
            numberInLog = 62,
        )
    }
}
