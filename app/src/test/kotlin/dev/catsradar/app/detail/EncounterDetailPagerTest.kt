package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.DetailCarouselTestTag
import dev.catsradar.ui.detail.DetailPagesTestTag
import dev.catsradar.ui.detail.EncounterDetailScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
class EncounterDetailPagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `a swipe to the next page reports the older cat, once`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(state = loadedOn(newer, newer, older), onPageSettle = { settled += it })
            }
        }

        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(listOf(older.id), settled)
    }

    @Test
    fun `the pager follows the cat on screen when the state moves it`() {
        var state by mutableStateOf(loadedOn(newer, newer, older))
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = state, onPageSettle = { settled += it }) }
        }
        compose.onNodeWithText(NEWER_TIME).assertIsDisplayed()

        state = loadedOn(older, newer, older)
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        assertEquals(emptyList(), settled)
    }

    @Test
    fun `a cat logged while watching keeps the cat on screen and reports nothing`() {
        var state by mutableStateOf(loadedOn(older, newer, older))
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = state, onPageSettle = { settled += it }) }
        }
        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()

        state = loadedOn(older, logged, newer, older)
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(emptyList(), settled)
    }

    @Test
    fun `a restore after the pages shifted starts on the cat on screen and reports nothing`() {
        val restoration = StateRestorationTester(compose)
        val settled = mutableListOf<String>()
        var restoredState = loadedOn(older, newer, older)
        restoration.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = restoredState, onPageSettle = { settled += it }) }
        }
        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()

        restoredState = loadedOn(older, logged, newer, older)
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(emptyList(), settled)
    }

    @Test
    fun `several pages show the position of the cat on screen, read as Cat n of m`() {
        compose.setContent { CatsRadarTheme { EncounterDetailScreen(state = loadedOn(newer, logged, newer, older)) } }

        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.detail_position_description, 2, 3))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `a cat swiped away from and back to keeps its place in the row`() {
        compose.setContent {
            CatsRadarTheme { EncounterDetailScreen(state = loadedOn(photographed, photographed, older)) }
        }
        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.viewer_position, 2, 2)).assertExists()

        // Across the time, below the row, so the drag moves the pages rather than the row.
        val belowTheRow = compose.onNodeWithText(NEWER_TIME).fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithTag(DetailPagesTestTag)
            .performTouchInput { swipe(Offset(right - 1f, belowTheRow), Offset(left + 1f, belowTheRow)) }
        compose.waitForIdle()
        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.onNodeWithText(NEWER_TIME).assertExists()
        compose.onNodeWithText(context.getString(R.string.viewer_position, 2, 2)).assertExists()
    }

    @Test
    fun `a single cat shows no position`() {
        compose.setContent { CatsRadarTheme { EncounterDetailScreen(state = loadedOn(older, older)) } }

        compose.onNodeWithText("1 / 1").assertDoesNotExist()
        compose.onNode(hasContentDescription(context.getString(R.string.detail_position_description, 1, 1)))
            .assertDoesNotExist()
    }

    @Test
    fun `a drag past the row's end moves on to the next cat`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = loadedOn(photographed, photographed, older),
                    onPageSettle = { settled += it },
                )
            }
        }

        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(emptyList(), settled, "the first drag moves the row, not the pages")
        compose.onNodeWithText(context.getString(R.string.viewer_position, 2, 2)).assertExists()
        repeat(SWIPES_TO_PASS_THE_ROW) {
            if (settled.isEmpty()) {
                compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput { swipeLeft() }
                compose.waitForIdle()
            }
        }

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(listOf(older.id), settled)
    }

    @Test
    fun `the position names the page a swipe heads for before the pages come to rest`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(state = loadedOn(newer, logged, newer, older), onPageSettle = { settled += it })
            }
        }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()

        flickPagesAndLeaveThemSettling(frames = 1)

        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        assertEquals(emptyList(), settled, "the pages are still on their way")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        assertEquals(listOf(older.id), settled)
    }

    @Test
    fun `a drag on the row while the pages still settle moves the row, not the pages`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = loadedOn(newer, newer, photographedOlder, oldest),
                    onPageSettle = { settled += it },
                )
            }
        }
        flickPagesAndLeaveThemSettling(frames = 3)
        assertEquals(emptyList(), settled, "the pages are still on their way")

        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput {
            swipe(Offset(width * 0.6f, centerY), Offset(width * 0.1f, centerY), durationMillis = 150)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithText(context.getString(R.string.viewer_position, 2, 2)).assertIsDisplayed()
        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(listOf(photographedOlder.id), settled)
    }

    @Test
    fun `while the row is dragged during a settle the pages wait under it, then carry on`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = loadedOn(newer, newer, photographedOlder, oldest),
                    onPageSettle = { settled += it },
                )
            }
        }
        flickPagesAndLeaveThemSettling(frames = 3)
        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput {
            down(Offset(width * 0.6f, centerY))
            repeat(4) { moveBy(Offset(-12f, 0f)) }
        }
        compose.mainClock.advanceTimeByFrame()
        val held = timeLeft(OLDER_TIME)

        repeat(6) { compose.mainClock.advanceTimeByFrame() }

        assertEquals(held, timeLeft(OLDER_TIME), "the pages moved under the finger")
        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(listOf(photographedOlder.id), settled)
    }

    @Test
    fun `a second flick below the row while the pages still settle moves on to the cat after`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(
                    state = loadedOn(newer, newer, photographedOlder, oldest),
                    onPageSettle = { settled += it },
                )
            }
        }
        // Past halfway, so the second flick starts from the cat the first one heads for.
        flickPagesAndLeaveThemSettling(frames = 12)
        assertEquals(emptyList(), settled, "the pages are still on their way")

        val belowTheRow = compose.onNodeWithText(OLDER_TIME).fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput {
            swipe(Offset(width * 0.7f, belowTheRow), Offset(width * 0.5f, belowTheRow), durationMillis = 40)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithText(OLDEST_TIME).assertIsDisplayed()
        assertEquals(oldest.id, settled.last())
    }

    @Test
    fun `a tap while the pages still settle, however shaky, lets them carry on to the cat they head for`() {
        val settled = mutableListOf<String>()
        compose.setContent {
            CatsRadarTheme {
                EncounterDetailScreen(state = loadedOn(newer, newer, older, oldest), onPageSettle = { settled += it })
            }
        }
        flickPagesAndLeaveThemSettling(frames = 3)
        assertEquals(emptyList(), settled, "the pages are still on their way")

        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveBy(Offset(-3f, 0f))
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithText(OLDER_TIME).assertIsDisplayed()
        assertEquals(listOf(older.id), settled)
    }

    private fun timeLeft(time: String): Float = compose.onNodeWithText(time).fetchSemanticsNode().boundsInRoot.left

    // A short, quick drag: the pages still have most of the way to go when the finger lifts.
    private fun flickPagesAndLeaveThemSettling(frames: Int) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(DetailPagesTestTag).performTouchInput {
            swipe(Offset(width * 0.7f, centerY), Offset(width * 0.5f, centerY), durationMillis = 40)
        }
        repeat(frames) { compose.mainClock.advanceTimeByFrame() }
    }

    private companion object {
        // One advance per swipe: to the second photo, to the row's end, then over into the next cat.
        const val SWIPES_TO_PASS_THE_ROW = 3
        const val LOGGED_TIME = "14:40"
        const val NEWER_TIME = "14:32"
        const val OLDER_TIME = "13:58"
        const val OLDEST_TIME = "13:40"

        val logged = page("cat-0", LOGGED_TIME)
        val newer = page("cat-1", NEWER_TIME)
        val older = page("cat-2", OLDER_TIME)
        val oldest = page("cat-3", OLDEST_TIME)
        val twoPhotos = persistentListOf(
            DetailPhoto(id = "photo-1", path = "/data/photos/photo-1.jpg"),
            DetailPhoto(id = "photo-2", path = "/data/photos/photo-2.jpg"),
        )
        val photographed = newer.copy(photos = twoPhotos)
        val photographedOlder = older.copy(photos = twoPhotos)

        fun page(id: String, time: String) = CatPage(
            id = id,
            dayLabel = "Today",
            timeLabel = time,
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            setsLocation = true,
        )
    }
}
