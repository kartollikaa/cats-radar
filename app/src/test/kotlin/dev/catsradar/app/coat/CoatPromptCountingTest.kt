package dev.catsradar.app.coat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CoatPrompt
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// A phone-sized screen, so the whole sheet, its last row of coats included, is on it and takes a tap.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CoatPromptCountingTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ginger get() = context.getString(R.string.coat_ginger)
    private val black get() = context.getString(R.string.coat_black)
    private val notSpecified get() = context.getString(R.string.coat_not_specified)
    private val remove get() = context.getString(R.string.counter_coat_count_remove)

    @Test
    fun `the sheet offers Several beside Not now`() {
        var severalTaps = 0
        compose.setContent {
            CatsRadarTheme {
                CoatPrompt(prompt = CoatPromptState(thumbPath = null), onSeveralClick = { severalTaps++ })
            }
        }

        compose.onNodeWithText(context.getString(R.string.counter_coat_prompt_skip)).assertExists()
        compose.onNodeWithText(context.getString(R.string.counter_coat_prompt_several)).performClick()

        assertEquals(1, severalTaps)
    }

    @Test
    fun `counting shows the number of cats, the tray and Save once a cat is counted`() {
        var prompt by mutableStateOf(CoatPromptState(thumbPath = null, counting = CoatCountState()))
        val removed = mutableListOf<Int>()
        var saves = 0
        compose.setContent {
            CatsRadarTheme {
                CoatPrompt(prompt = prompt, onTrayCatClick = { removed += it }, onSaveClick = { saves++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.counter_coat_count_title_empty)).assertExists()
        compose.onAllNodesWithText(saveLabel(1)).assertCountEquals(0)

        prompt = prompt.copy(counting = CoatCountState(persistentListOf(CoatOption.GINGER, null, CoatOption.BLACK)))
        compose.waitForIdle()

        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.counter_coat_count_title, 3, 3))
            .assertExists()
        compose.onAllNodes(SemanticsMatcher("removes a counted cat") { it.clickLabel() == remove })
            .assertCountEquals(3)
        compose.onNodeWithContentDescription(black).performClick()
        compose.onNodeWithText(saveLabel(3)).performClick()
        assertEquals(listOf(2) to 1, removed to saves)
    }

    @Test
    fun `a screen reader hears each counted cat and each face's count`() {
        compose.setContent {
            CatsRadarTheme {
                CoatPrompt(
                    prompt = CoatPromptState(
                        thumbPath = null,
                        counting = CoatCountState(persistentListOf(CoatOption.GINGER, CoatOption.GINGER, null)),
                    ),
                )
            }
        }
        val removesTheCat = SemanticsMatcher("offers Remove") { it.clickLabel() == remove }

        compose.onAllNodesWithContentDescription(ginger).assertCountEquals(2).assertAll(removesTheCat)
        compose.onAllNodesWithContentDescription(notSpecified).assertCountEquals(1).assertAll(removesTheCat)
        compose.onNodeWithText(ginger).assert(stateDescription(counted(2)))
        compose.onNodeWithText(notSpecified).assert(stateDescription(counted(1)))
        compose.onNodeWithText(black).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    private fun saveLabel(count: Int) =
        context.resources.getQuantityString(R.plurals.counter_coat_count_save, count, count)

    private fun counted(count: Int) = context.resources.getQuantityString(R.plurals.counter_coat_counted, count, count)

    private fun stateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun SemanticsNode.clickLabel(): String? =
        config.getOrNull(SemanticsActions.OnClick)?.label
}
