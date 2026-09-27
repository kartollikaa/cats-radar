package dev.catsradar.app.coat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import dev.catsradar.ui.counter.CoatPromptPawTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    private val noCoat get() = context.getString(R.string.coat_none)
    private val remove get() = context.getString(R.string.counter_coat_count_remove)
    private val oneCat get() = context.getString(R.string.counter_coat_prompt_one)
    private val several get() = context.getString(R.string.counter_coat_prompt_several)
    private val photo get() = context.getString(R.string.counter_coat_prompt_photo)
    private val asking = CoatPromptState("cat", "cat", thumbPath = null)
    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun show(prompt: () -> CoatPromptState, onOneCatClick: () -> Unit = {}, onSeveralClick: () -> Unit = {}) {
        compose.setContent {
            CatsRadarTheme {
                CoatPrompt(prompt = prompt(), onOneCatClick = onOneCatClick, onSeveralClick = onSeveralClick)
            }
        }
    }

    @Test
    fun `the sheet names its mode as One cat or Several, with One cat chosen while asking`() {
        show({ asking })

        compose.onNode(hasText(oneCat) and radio).assertIsOn()
        compose.onNode(hasText(several) and radio).assertIsOff()
    }

    @Test
    fun `while counting, Several is the one chosen`() {
        show({ asking.copy(counting = CoatCountState()) })

        compose.onNode(hasText(several) and radio).assertIsOn()
        compose.onNode(hasText(oneCat) and radio).assertIsOff()
    }

    @Test
    fun `the group reports a switch only from the mode not chosen`() {
        var prompt by mutableStateOf(asking)
        var ones = 0
        var severals = 0
        show({ prompt }, onOneCatClick = { ones++ }, onSeveralClick = { severals++ })

        compose.onNodeWithText(oneCat).performClick()
        compose.onNodeWithText(several).performClick()
        prompt = asking.copy(counting = CoatCountState(persistentListOf(CoatOption.GINGER)))
        compose.waitForIdle()
        compose.onNodeWithText(several).performClick()
        compose.onNodeWithText(oneCat).performClick()

        assertEquals(1 to 1, ones to severals)
    }

    @Test
    fun `the photo stays beside the question while counting, with no second copy in the tray`() {
        var prompt by mutableStateOf(asking.copy(thumbPath = "/photos/just-taken_thumb.jpg"))
        show({ prompt })
        assertBeside(context.getString(R.string.counter_coat_prompt_title))

        prompt = prompt.copy(counting = CoatCountState(persistentListOf(CoatOption.GINGER, CoatOption.BLACK)))
        compose.waitForIdle()

        assertBeside(context.resources.getQuantityString(R.plurals.counter_coat_count_title, 2, 2))
    }

    @Test
    fun `without a thumbnail the question sits beside the paw, asking and counting`() {
        var prompt by mutableStateOf(asking)
        show({ prompt })
        compose.onNodeWithTag(CoatPromptPawTestTag, useUnmergedTree = true).assertExists()

        prompt = asking.copy(counting = CoatCountState(persistentListOf(CoatOption.GINGER)))
        compose.waitForIdle()

        compose.onNodeWithTag(CoatPromptPawTestTag, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithContentDescription(photo).assertCountEquals(0)
    }

    @Test
    fun `an empty count says where counted cats gather, until the first one does`() {
        var prompt by mutableStateOf(asking.copy(counting = CoatCountState()))
        show({ prompt })
        val gather = context.getString(R.string.counter_coat_count_tray_empty)
        compose.onNodeWithText(gather).assertExists()

        prompt = asking.copy(counting = CoatCountState(persistentListOf(CoatOption.GINGER)))
        compose.waitForIdle()

        compose.onAllNodesWithText(gather).assertCountEquals(0)
    }

    @Test
    fun `Not now closes asking and counting alike, and Several is the group's alone`() {
        var prompt by mutableStateOf(asking)
        show({ prompt })
        val skip = context.getString(R.string.counter_coat_prompt_skip)
        compose.onNodeWithText(skip).assertExists()
        compose.onAllNodesWithText(several).assertCountEquals(1).assertAll(radio)

        prompt = asking.copy(counting = CoatCountState())
        compose.waitForIdle()

        compose.onNodeWithText(skip).assertExists()
        compose.onAllNodesWithText(several).assertCountEquals(1).assertAll(radio)
    }

    @Test
    fun `counting shows the number of cats, the tray and Save once a cat is counted`() {
        var prompt by mutableStateOf(CoatPromptState("cat", "cat", thumbPath = null, counting = CoatCountState()))
        val removed = mutableListOf<Int>()
        var saves = 0
        compose.setContent {
            CatsRadarTheme {
                CoatPrompt(prompt = prompt, onTrayCatClick = { removed += it.index }, onSaveClick = { saves++ })
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
                        catId = "cat",
                        photoId = "cat",
                        thumbPath = null,
                        counting = CoatCountState(persistentListOf(CoatOption.GINGER, CoatOption.GINGER, null)),
                    ),
                )
            }
        }
        val removesTheCat = SemanticsMatcher("offers Remove") { it.clickLabel() == remove }

        compose.onAllNodesWithContentDescription(ginger).assertCountEquals(2).assertAll(removesTheCat)
        compose.onAllNodesWithContentDescription(noCoat).assertCountEquals(1).assertAll(removesTheCat)
        compose.onNodeWithText(ginger).assert(stateDescription(counted(2)))
        compose.onNodeWithText(noCoat).assert(stateDescription(counted(1)))
        compose.onNodeWithText(black).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    private fun assertBeside(title: String) {
        val picture = compose.onNodeWithContentDescription(photo).getUnclippedBoundsInRoot()
        val words = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        compose.onAllNodesWithContentDescription(photo).assertCountEquals(1)
        assertTrue(picture.top < words.bottom && picture.bottom > words.top, "$picture beside $words")
        assertTrue(picture.right <= words.left, "$picture before $words")
    }

    private fun saveLabel(count: Int) =
        context.resources.getQuantityString(R.plurals.counter_coat_count_save, count, count)

    private fun counted(count: Int) = context.resources.getQuantityString(R.plurals.counter_coat_counted, count, count)

    private fun stateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun SemanticsNode.clickLabel(): String? =
        config.getOrNull(SemanticsActions.OnClick)?.label
}
