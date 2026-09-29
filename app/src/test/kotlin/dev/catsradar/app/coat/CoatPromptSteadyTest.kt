package dev.catsradar.app.coat

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.counter.CoatCountState
import dev.catsradar.presentation.counter.CoatPromptState
import dev.catsradar.ui.R
import dev.catsradar.ui.coat.labelRes
import dev.catsradar.ui.counter.CoatPrompt
import dev.catsradar.ui.counter.CoatTrayFaceTestTag
import dev.catsradar.ui.counter.CoatTrayTestTag
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A phone-sized screen, so the whole sheet is on it; native graphics, so wordings wrap as they do on a phone.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
open class CoatPromptSteadyTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val asking = CoatPromptState("cat", "cat", thumbPath = null)
    private var prompt by mutableStateOf(asking)

    @Test
    fun `counting cats up to the most a photo holds moves nothing one taps, nor a counted cat`() {
        show(counting(0))
        val before = taps()
        var counted = emptyList<Rect>()

        (1..CoatCountState.MOST_CATS).forEach { cats ->
            showNow(counting(cats))

            assertStill(before, taps(), "after counting cat $cats")
            val now = countedCats(cats)
            assertEquals(counted, now.dropLast(1), "the cats counted before cat $cats")
            counted = now
        }
    }

    @Test
    fun `taking counted cats out one by one moves nothing one taps, nor the cats left`() {
        show(counting(CoatCountState.MOST_CATS))
        val before = taps()
        val counted = countedCats(CoatCountState.MOST_CATS)

        (CoatCountState.MOST_CATS - 1 downTo 0).forEach { cats ->
            showNow(counting(cats))

            assertStill(before, taps(), "with $cats cats left")
            assertEquals(counted.take(cats), countedCats(cats), "the cats left with $cats")
        }
    }

    @Test
    fun `switching One cat, Several and back moves neither the pair, a face nor Not now`() {
        show(asking)
        val asked = taps()

        showNow(counting(0))
        val counting = taps()
        showNow(asking)

        assertStill(asked, counting, "switched to Several")
        assertStill(asked, taps(), "switched back to One cat")
    }

    @Test
    fun `taking out a cat moves nothing one taps, and each cat counted after it takes the place before`() {
        val full = CoatOption.entries.take(CoatCountState.MOST_CATS)
        show(counting(CoatCountState.MOST_CATS))
        val before = taps()
        val placed = placesOf(full)
        val takenOut = CoatCountState.MOST_CATS / 2

        val left = full.filterIndexed { index, _ -> index != takenOut }
        showNow(asking.copy(counting = CoatCountState(left.toImmutableList())))

        assertStill(before, taps(), "after taking out cat $takenOut")
        assertEquals(placed.dropLast(1), placesOf(left), "the cats left, in the order they were counted")
    }

    @Test
    fun `the tray holds only the rows its cats fill, and a row opens above the others`() {
        show(counting(1))
        val oneRow = tray().height

        (1..CoatCountState.MOST_CATS).forEach { cats ->
            showNow(counting(cats))

            val placed = countedCats(cats)
            val top = placed.minOf { it.top }
            assertEquals(top, placed.last().top, "the cat counted last is in the top row, with $cats cats")
            assertTrue(placed.all { it.bottom <= placed.first().bottom }, "no row under the first cat's: $placed")
            assertEquals(top, tray().top, 1f, "the tray's top with $cats cats")
            assertEquals(placed.first().bottom, tray().bottom, 1f, "the tray's bottom with $cats cats")
            if (placed.all { it.top == top }) assertEquals(oneRow, tray().height, 1f)
        }
        assertTrue(countedCats(CoatCountState.MOST_CATS).map { it.top }.distinct().size > 1, "a full tray takes rows")
    }

    @Test
    fun `a full tray shows every counted cat whole and on the screen`() {
        show(counting(CoatCountState.MOST_CATS))

        val whole = with(compose.density) { 40.dp.toPx() }
        val faces = trayFaces()
        assertEquals(CoatCountState.MOST_CATS, faces.size)
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        faces.forEach { face ->
            assertTrue(face.width >= whole - 1f && face.height >= whole - 1f, "a whole counted cat: $face")
            val inside = screen.contains(face.topLeft) && screen.contains(face.bottomRight - Offset(1f, 1f))
            assertTrue(inside, "$face on $screen")
        }
    }

    @Test
    fun `while counting, the tray lies between the header and the pair`() {
        show(counting(3))

        val hint = bounds(context.getString(R.string.counter_coat_count_hint))
        val header = title().let { Rect(it.left, it.top, maxOf(it.right, hint.right), maxOf(it.bottom, hint.bottom)) }
        val tray = tray()
        val pair = bounds(context.getString(R.string.counter_coat_prompt_one))
        assertTrue(header.bottom <= tray.top, "the header $header above the tray $tray")
        assertTrue(tray.bottom <= pair.top, "the tray $tray above the pair $pair")
    }

    @Test
    fun `once the words settle, a screen reader hears only the current ones`() {
        show(counting(0))
        showNow(counting(3))

        compose.onAllNodesWithText(context.getString(R.string.counter_coat_count_title_empty)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.counter_coat_count_full)).assertCountEquals(0)
        compose.onAllNodesWithText(saveLabel(3)).assertCountEquals(1)

        showNow(asking)

        compose.onAllNodesWithText(context.getString(R.string.counter_coat_count_hint)).assertCountEquals(0)
        compose.onAllNodesWithText(saveLabel(1)).assertCountEquals(0)
    }

    private fun show(state: CoatPromptState) {
        prompt = state
        compose.setContent {
            CatsRadarTheme {
                // Anchored at the bottom as the sheet is, so a change in height moves what sits above it.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    CoatPrompt(prompt = prompt)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showNow(state: CoatPromptState) {
        prompt = state
        compose.waitForIdle()
    }

    private fun counting(cats: Int) = asking.copy(
        counting = CoatCountState(List(cats) { CoatOption.entries[it % CoatOption.entries.size] }.toImmutableList()),
    )

    // Everything a finger goes for next: the pair, each face, No coat's included, and Not now.
    private fun taps(): Map<String, Rect> {
        val labels = listOf(R.string.counter_coat_prompt_one, R.string.counter_coat_prompt_several) +
            CoatOption.entries.map { it.labelRes() } + R.string.coat_none + R.string.counter_coat_prompt_skip
        return labels.associate { res -> context.getString(res).let { it to bounds(it) } }
    }

    private fun assertStill(before: Map<String, Rect>, now: Map<String, Rect>, whenever: String) {
        val moved = before.filter { (name, place) -> now[name] != place }
            .mapValues { (name, place) -> place to now[name] }
        assertEquals(emptyMap(), moved, "moved $whenever")
    }

    private fun title(): Rect = compose.onNode(isHeading()).fetchSemanticsNode().boundsInRoot

    private fun tray(): Rect = compose.onNodeWithTag(CoatTrayTestTag).fetchSemanticsNode().boundsInRoot

    // In the order they were counted: counting() gives each cat its own coat.
    private fun countedCats(cats: Int): List<Rect> = placesOf(CoatOption.entries.take(cats))

    private fun placesOf(coats: List<CoatOption>): List<Rect> = coats.map { coat ->
        val label = context.getString(coat.labelRes())
        compose.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag(CoatTrayTestTag)))
            .fetchSemanticsNode().boundsInRoot
    }

    private fun trayFaces(): List<Rect> =
        compose.onAllNodesWithTag(CoatTrayFaceTestTag, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }

    private fun bounds(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun saveLabel(count: Int) =
        context.resources.getQuantityString(R.plurals.counter_coat_count_save, count, count)
}

// Tall enough that the sheet fits the screen, so a full tray is on it.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CoatPromptSteadyLargestFontTest : CoatPromptSteadyTest()

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h900dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CoatPromptSteadyRuLargestFontTest : CoatPromptSteadyTest()

// The narrowest phones, where a longer Save label is likelier to wrap than a shorter one.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w320dp-h900dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CoatPromptSteadyRuNarrowLargestFontTest : CoatPromptSteadyTest()
