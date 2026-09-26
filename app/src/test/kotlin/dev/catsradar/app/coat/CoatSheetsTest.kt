package dev.catsradar.app.coat

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.presentation.map.MapSpotState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.CoatGrid
import dev.catsradar.ui.counter.CoatPrompt
import dev.catsradar.ui.map.MapCoatFilter
import dev.catsradar.ui.map.MapSpotScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// A phone-sized screen, so the grid's last row is on it and takes a tap.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CoatSheetsTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notSpecified get() = context.getString(R.string.coat_not_specified)
    private val coatsTapped = mutableListOf<CoatOption?>()
    private var unspecifiedTaps = 0
    private var skips = 0
    private var clears = 0

    @Test
    fun `the coat grid offers Not specified only when asked`() {
        compose.setContent { CatsRadarTheme { CoatGrid(onCoatClick = { coatsTapped += it }) } }

        compose.onNodeWithText(notSpecified).assertDoesNotExist()
    }

    @Test
    fun `Not specified is marked like a coat and reports its own tap`() {
        compose.setContent {
            CatsRadarTheme {
                CoatGrid(
                    selected = persistentSetOf(null),
                    onCoatClick = { coatsTapped += it },
                    onUnspecifiedClick = { unspecifiedTaps++ },
                )
            }
        }

        compose.onNodeWithText(notSpecified).assertIsSelected().performClick()

        assertEquals(1 to emptyList<CoatOption?>(), unspecifiedTaps to coatsTapped)
    }

    @Test
    fun `the map filter explains itself, toggles no coat from its grid, and clears only a choice`() {
        showFilter(persistentSetOf())
        compose.onNodeWithText(context.getString(R.string.map_coats_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.map_coats_hint)).assertExists()
        compose.onNodeWithText(context.getString(R.string.map_coats_all)).assertIsNotEnabled()

        compose.onNodeWithText(notSpecified).performClick()

        assertEquals(listOf<CoatOption?>(null), coatsTapped)
    }

    @Test
    fun `the map filter's Every coat clears a choice`() {
        showFilter(persistentSetOf(CoatOption.GINGER))

        compose.onNodeWithText(context.getString(R.string.map_coats_all)).assertIsEnabled().performClick()

        assertEquals(1, clears)
    }

    @Test
    fun `the coat prompt asks, explains, and takes a coat or a skip`() {
        compose.setContent {
            CatsRadarTheme {
                CoatPrompt(
                    prompt = CoatPromptState(thumbPath = null),
                    onCoatClick = { coatsTapped += it },
                    onSkipClick = { skips++ },
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.counter_coat_prompt_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.counter_coat_prompt_hint)).assertExists()
        compose.onNodeWithText(notSpecified).assertDoesNotExist()

        compose.onNodeWithText(context.getString(R.string.coat_ginger)).performClick()
        compose.onNodeWithText(context.getString(R.string.counter_coat_prompt_skip)).performClick()

        assertEquals(listOf<CoatOption?>(CoatOption.GINGER) to 1, coatsTapped to skips)
    }

    @Test
    fun `the coat prompt shows the photo when it has one`() {
        compose.setContent {
            CatsRadarTheme { CoatPrompt(prompt = CoatPromptState(thumbPath = "/photos/just-taken_thumb.jpg")) }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.counter_coat_prompt_photo)).assertExists()
    }

    @Test
    fun `a spot's list opens on the same sheet heading`() {
        compose.setContent {
            CatsRadarTheme { MapSpotScreen(state = MapSpotState.Listed(catCount = 3, rows = persistentListOf())) }
        }

        val title = context.resources.getQuantityString(R.plurals.map_spot_title, 3, 3)
        compose.onNodeWithText(title).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    private fun showFilter(shown: ImmutableSet<CoatOption?>) {
        compose.setContent {
            CatsRadarTheme {
                MapCoatFilter(shown = shown, onCoatToggle = { coatsTapped += it }, onClear = { clears++ })
            }
        }
    }
}
