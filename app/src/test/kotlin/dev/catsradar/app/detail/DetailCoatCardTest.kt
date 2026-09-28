package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.CoatCardFaceTestTag
import dev.catsradar.ui.detail.CoatCardLeadTestTag
import dev.catsradar.ui.detail.CoatCardPawTestTag
import dev.catsradar.ui.detail.CoatCardPillTestTag
import dev.catsradar.ui.detail.CoatCardTestTag
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
class DetailCoatCardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private lateinit var scheme: ColorScheme
    private lateinit var typography: Typography
    private val taps = mutableListOf<String>()

    @Test
    fun `the coat is a card on the low container with large corners, and the coat strip is gone`() {
        show(cat(CoatOption.GINGER))

        val card = compose.onNodeWithTag(CoatCardTestTag).performScrollTo().bounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.surfaceContainerLow, pixels[(card.left + 8.dp.px()).toInt(), card.center.y.toInt()])
        assertEquals(scheme.surface, pixels[(card.left + 1.dp.px()).toInt(), (card.top + 1.dp.px()).toInt()])
        val insideTheCurve = 9.5.dp.px()
        assertEquals(
            scheme.surfaceContainerLow,
            pixels[(card.left + insideTheCurve).toInt(), (card.top + insideTheCurve).toInt()],
        )
        compose.onAllNodesWithText(context.getString(R.string.coat_black)).assertCountEquals(0)
    }

    @Test
    fun `the card shows the face in its coat's 72 dp shape, its name over Coat, and Change at its end`() {
        show(cat(CoatOption.GINGER))

        val card = compose.onNodeWithTag(CoatCardTestTag).performScrollTo().bounds()
        assertEquals(72.dp.px(), compose.onNodeWithTag(CoatCardLeadTestTag, useUnmergedTree = true).bounds().width, 1f)
        assertEquals(54.dp.px(), compose.onNodeWithTag(CoatCardFaceTestTag, useUnmergedTree = true).bounds().width, 1f)
        val name = compose.onNodeWithText(context.getString(R.string.coat_ginger), useUnmergedTree = true)
        val style = name.textLayout().layoutInput.style
        assertEquals(typography.titleMediumEmphasized.fontSize, style.fontSize)
        assertEquals(typography.titleMediumEmphasized.fontWeight, style.fontWeight)
        val under = compose.onNodeWithText(context.getString(R.string.detail_coat), useUnmergedTree = true).bounds()
        assertTrue(under.top >= name.bounds().bottom, "Coat sits under the name: $under")
        val pill = compose.onNodeWithTag(CoatCardPillTestTag, useUnmergedTree = true).bounds()
        compose.onNodeWithText(context.getString(R.string.detail_coat_change), useUnmergedTree = true).assertExists()
        assertEquals(card.right - 16.dp.px(), pill.right, 1f)
        assertTrue(pill.height >= 40.dp.px() - 1f, "$pill")
    }

    @Test
    fun `a cat with no coat shows the paw, Coat not noted and Add`() {
        show(cat(coat = null))

        compose.onNodeWithTag(CoatCardTestTag).performScrollTo()
        compose.onNodeWithTag(CoatCardPawTestTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(CoatCardFaceTestTag, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.detail_coat_not_noted), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.detail_coat_add), useUnmergedTree = true).assertExists()
        val change = context.getString(R.string.detail_coat_change)
        compose.onNodeWithText(change, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the whole card is one button, and a tap on the face or the pill names the cat`() {
        show(cat(CoatOption.GINGER))

        val card = compose.onNodeWithTag(CoatCardTestTag).performScrollTo().fetchSemanticsNode().config
        assertEquals(listOf("Ginger", "Coat", "Change"), card[SemanticsProperties.Text].map { it.text })
        assertEquals(Role.Button, card[SemanticsProperties.Role])
        assertTrue(SemanticsActions.OnClick in card)
        compose.onNodeWithTag(CoatCardLeadTestTag, useUnmergedTree = true).performClick()
        compose.onNodeWithTag(CoatCardPillTestTag, useUnmergedTree = true).performClick()

        assertEquals(listOf("coat cat-7", "coat cat-7"), taps)
    }

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
                            onCoatCardClick = { taps += "coat $it" },
                        )
                    }
                }
            }
        }
    }

    private fun cat(coat: CoatOption?) = CatPage(
        id = "cat-7",
        dayLabel = "Yesterday",
        timeLabel = "4:12 PM",
        location = LocationLabel.NONE,
        coordinatesLabel = null,
        accuracyMeters = null,
        coat = coat,
    )

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun Dp.px(): Float = with(compose.density) { toPx() }
}
