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
import org.junit.Assume.assumeTrue
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
    fun `counting cats up to the most a photo holds moves nothing one taps, nor the title`() {
        show(counting(0))
        val before = taps() + ("title" to title())
        var firstCat: Rect? = null

        (1..CoatCountState.MOST_CATS).forEach { cats ->
            showNow(counting(cats))

            assertStill(before, taps() + ("title" to title()), "after counting cat $cats")
            val first = trayFaces().first()
            firstCat?.let { assertEquals(it, first, "the first counted cat after counting cat $cats") }
            firstCat = first
        }
    }

    @Test
    fun `taking counted cats out one by one moves nothing one taps, nor the first cat left`() {
        show(counting(CoatCountState.MOST_CATS))
        val before = taps() + ("title" to title())
        val firstCat = trayFaces().first()

        (CoatCountState.MOST_CATS - 1 downTo 0).forEach { cats ->
            showNow(counting(cats))

            assertStill(before, taps() + ("title" to title()), "with $cats cats left")
            if (cats > 0) assertEquals(firstCat, trayFaces().first(), "the first cat with $cats cats left")
        }
    }

    // False where Several is taller than the screen: the sheet then fills it and scrolls, so a switch moves the pair.
    protected open val bothModesFitTheScreen = true

    @Test
    fun `switching One cat, Several and back moves neither the pair, a face nor Not now`() {
        assumeTrue("Several fits the screen here", bothModesFitTheScreen)
        show(asking)
        val asked = taps()

        showNow(counting(0))
        val counting = taps()
        showNow(asking)

        assertStill(asked, counting, "switched to Several")
        assertStill(asked, taps(), "switched back to One cat")
    }

    @Test
    fun `a full tray keeps the height it had with one cat, and shows every counted cat whole`() {
        show(counting(1))
        val withOne = tray().height

        showNow(counting(CoatCountState.MOST_CATS))

        assertEquals(withOne, tray().height, 1f)
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

        val header = title()
        val tray = tray()
        val pair = bounds(context.getString(R.string.counter_coat_prompt_one))
        assertTrue(header.bottom <= tray.top, "the header $header above the tray $tray")
        assertTrue(tray.bottom <= pair.top, "the tray $tray above the pair $pair")
    }

    @Test
    fun `no wording kept only for its room reaches a screen reader`() {
        show(counting(3))

        compose.onAllNodesWithText(context.getString(R.string.counter_coat_count_title_empty)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.counter_coat_count_full)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.counter_coat_count_tray_empty)).assertCountEquals(0)
        compose.onAllNodesWithText(saveLabel(CoatCountState.MOST_CATS)).assertCountEquals(0)
        compose.onAllNodesWithText(saveLabel(3)).assertCountEquals(1)

        showNow(asking)

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

    // Everything a finger goes for next: the pair, each coat's face and Not now.
    private fun taps(): Map<String, Rect> {
        val labels = listOf(R.string.counter_coat_prompt_one, R.string.counter_coat_prompt_several) +
            CoatOption.entries.map { it.labelRes() } + R.string.counter_coat_prompt_skip
        return labels.associate { res -> context.getString(res).let { it to bounds(it) } }
    }

    private fun assertStill(before: Map<String, Rect>, now: Map<String, Rect>, whenever: String) {
        val moved = before.filter { (name, place) -> now[name] != place }
            .mapValues { (name, place) -> place to now[name] }
        assertEquals(emptyMap(), moved, "moved $whenever")
    }

    // Its top-left corner: the wording's width may change, where it starts may not.
    private fun title(): Rect = compose.onNode(isHeading()).fetchSemanticsNode().boundsInRoot
        .let { Rect(it.topLeft, it.topLeft) }

    private fun tray(): Rect = compose.onNodeWithTag(CoatTrayTestTag).fetchSemanticsNode().boundsInRoot

    // In reading order: rows top to bottom, each left to right.
    private fun trayFaces(): List<Rect> =
        compose.onAllNodesWithTag(CoatTrayFaceTestTag, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.sortedWith(compareBy({ it.top }, { it.left }))

    private fun bounds(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun saveLabel(count: Int) =
        context.resources.getQuantityString(R.plurals.counter_coat_count_save, count, count)
}

// Tall enough that the sheet still stands on the bottom rather than filling the screen, where it scrolls instead.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CoatPromptSteadyLargestFontTest : CoatPromptSteadyTest()

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h900dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CoatPromptSteadyRuLargestFontTest : CoatPromptSteadyTest() {
    override val bothModesFitTheScreen = false
}

// The narrowest phones, where a longer Save label is likelier to wrap than a shorter one.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w320dp-h900dp", fontScale = 1.5f)
@RunWith(AndroidJUnit4::class)
class CoatPromptSteadyRuNarrowLargestFontTest : CoatPromptSteadyTest() {
    override val bothModesFitTheScreen = false
}
