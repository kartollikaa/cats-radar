package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coatsheet.CoatSheetHint
import dev.catsradar.presentation.coatsheet.CoatSheetState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.detail.CoatSheetContent
import dev.catsradar.ui.detail.CoatSheetFaceTestTag
import dev.catsradar.ui.detail.CoatSheetLeadTestTag
import dev.catsradar.ui.detail.CoatSheetPawTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1000dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class DetailCoatSheetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private lateinit var scheme: ColorScheme
    private lateinit var typography: Typography
    private val taps = mutableListOf<String>()

    @Test
    fun `the head shows the cat's face in its coat's shape on the primary container, and the question`() {
        show(CoatSheetState.Open(CoatOption.GINGER, CoatSheetHint.PICK_ANOTHER))

        val lead = compose.onNodeWithTag(CoatSheetLeadTestTag).bounds()
        assertEquals(64.dp.px(), lead.width, 1f)
        val face = compose.onNodeWithTag(CoatSheetFaceTestTag, useUnmergedTree = true).bounds()
        assertEquals(48.dp.px(), face.width, 1f)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.primaryContainer, pixels[lead.center.x.toInt(), (lead.top + 4.dp.px()).toInt()])
        assertEquals(scheme.surface, pixels[(lead.left + 2.dp.px()).toInt(), (lead.top + 2.dp.px()).toInt()])
        val title = compose.onNodeWithText(context.getString(R.string.detail_coat_sheet_title))
        val style = title.textLayout().layoutInput.style
        assertEquals(typography.headlineSmallEmphasized.fontSize, style.fontSize)
        assertEquals(typography.headlineSmallEmphasized.fontWeight, style.fontWeight)
        compose.onNodeWithText("Pick another, or “No coat”").assertIsDisplayed()
    }

    @Test
    fun `with no coat noted the head shows the paw on the highest container and the hint to tap one`() {
        show(CoatSheetState.Open(coat = null, hint = CoatSheetHint.TAP_ONE))

        compose.onNodeWithTag(CoatSheetPawTestTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(CoatSheetFaceTestTag, useUnmergedTree = true).assertDoesNotExist()
        val lead = compose.onNodeWithTag(CoatSheetLeadTestTag).bounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.surfaceContainerHighest, pixels[lead.center.x.toInt(), (lead.top + 5.dp.px()).toInt()])
        compose.assertGhostHem(lead, outside = scheme.surface, inside = scheme.surfaceContainerHighest)
        compose.onNodeWithText("Tap the coat that fits").assertIsDisplayed()
    }

    @Test
    fun `the grid rings the cat's coat, and no coat is its own cell, unringed`() {
        show(CoatSheetState.Open(CoatOption.BLACK, CoatSheetHint.PICK_ANOTHER))

        compose.onNodeWithText(context.getString(R.string.coat_black)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.coat_ginger)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.coat_none)).assertIsNotSelected()
    }

    @Test
    fun `with no coat noted the no coat cell is the ringed one`() {
        show(CoatSheetState.Open(coat = null, hint = CoatSheetHint.TAP_ONE))

        compose.onNodeWithText(context.getString(R.string.coat_none)).assertIsSelected()
        CoatOption.entries.forEach { coat ->
            compose.onNodeWithText(context.getString(coat.labelRes())).assertIsNotSelected()
        }
    }

    @Test
    fun `a coat and no coat report their taps`() {
        show(CoatSheetState.Open(CoatOption.BLACK, CoatSheetHint.PICK_ANOTHER))

        compose.onNodeWithText(context.getString(R.string.coat_grey)).performClick()
        compose.onNodeWithText(context.getString(R.string.coat_none)).performClick()

        assertEquals(listOf("coat GREY", "no coat"), taps)
    }

    private fun show(state: CoatSheetState.Open) {
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                typography = MaterialTheme.typography
                Surface {
                    CoatSheetContent(
                        state = state,
                        onCoatClick = { taps += "coat $it" },
                        onNoCoatClick = { taps += "no coat" },
                    )
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
}
