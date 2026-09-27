package dev.catsradar.app.detail

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.detail.AddPhoto
import dev.catsradar.presentation.detail.AttachProgress
import dev.catsradar.presentation.detail.CatPage
import dev.catsradar.presentation.detail.DetailPhoto
import dev.catsradar.presentation.detail.EncounterDetailState
import dev.catsradar.presentation.encounters.LocationLabel
import dev.catsradar.ui.R
import dev.catsradar.ui.detail.DetailCarouselTestTag
import dev.catsradar.ui.detail.EncounterDetailScreen
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

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1000dp-xxhdpi")
@RunWith(AndroidJUnit4::class)
class DetailPhotoCarouselTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private lateinit var scheme: ColorScheme
    private var state by mutableStateOf<EncounterDetailState>(EncounterDetailState.Loading)
    private val taps = mutableListOf<String>()

    @Test
    fun `a cat's photos run oldest first, 300 dp wide at 4 to 5, 8 dp apart, the first in line with the back arrow`() {
        show(catWith("first", "second", "third"))

        val (first, second) = photos().take(2)
        val back = compose.onNodeWithContentDescription(context.getString(R.string.detail_back)).bounds()
        assertEquals(300.dp.px(), first.width, 1f)
        assertEquals(375.dp.px(), first.height, 1f)
        assertEquals(8.dp.px(), second.left - first.right, 1f)
        assertEquals(back.left, first.left, 1f)
        onScreenPhoto().performClick()
        assertEquals(listOf("photo first"), taps)
    }

    @Test
    fun `the row starts under the bar however short the page is`() {
        show(catWith("cover"))
        val alone = compose.onNodeWithTag(DetailCarouselTestTag).bounds().top

        state = catWith("cover", "second", "third")
        compose.waitForIdle()

        assertEquals(alone, compose.onNodeWithTag(DetailCarouselTestTag).bounds().top, 1f)
    }

    @Test
    fun `a photo has large corners`() {
        show(catWith("first", "second"))

        val first = photos().first()
        val pixels = screen()
        // Only a large corner splits these two: a medium one covers the first, an extra large one misses the second.
        assertEquals(scheme.surface, pixels.at(first.left + 7.dp.px(), first.top + 7.dp.px()), "outside the corner")
        assertEquals(
            scheme.surfaceContainerHighest,
            pixels.at(first.left + 9.5.dp.px(), first.top + 9.5.dp.px()),
            "inside the corner",
        )
    }

    @Test
    fun `after the photos come take a photo and from gallery, narrower and as tall, on the low container`() {
        show(catWith("first", "second"))
        val photo = photos().first()

        compose.scrollCarouselToEnd(photos = 2)

        val take = compose.onNodeWithText(context.getString(R.string.detail_take_photo)).bounds()
        val pick = compose.onNodeWithText(context.getString(R.string.detail_from_gallery)).bounds()
        assertTrue(take.right < pick.left, "take a photo comes first: $take, $pick")
        val pixels = screen()
        listOf(take, pick).forEach { item ->
            assertTrue(item.width < photo.width, "narrower than a photo: $item")
            assertEquals(photo.height, item.height, 1f)
            assertEquals(scheme.surfaceContainerLow, pixels.at(item.center.x, item.top + 12.dp.px()))
        }
    }

    @Test
    fun `the add items report take and pick for the cat`() {
        show(catWith("first"))

        compose.scrollCarouselToEnd(photos = 1)
        compose.onNodeWithText(context.getString(R.string.detail_take_photo)).performClick()
        compose.onNodeWithText(context.getString(R.string.detail_from_gallery)).performClick()

        assertEquals(listOf("take cat-1", "pick cat-1"), taps)
    }

    @Test
    fun `while a photo is being attached, both add items are disabled`() {
        show(catWith("first").withAttempt())

        compose.scrollCarouselToEnd(photos = 1)

        compose.onNodeWithText(context.getString(R.string.detail_take_photo)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.detail_from_gallery)).assertIsNotEnabled()
    }

    @Test
    fun `a cat with several photos shows under the row which photo is in front`() {
        show(catWith("cover", "second"))

        val label = position(1, of = 2)
        label.assertIsDisplayed()
        label.assert(hasContentDescription(context.getString(R.string.viewer_position_description, 1, 2)))
        assertTrue(label.bounds().top >= compose.onNodeWithTag(DetailCarouselTestTag).bounds().bottom)
    }

    @Test
    fun `a cat with one photo shows no position`() {
        show(catWith("cover"))

        position(1, of = 1).assertDoesNotExist()
    }

    @Test
    fun `scrolling the row to the next photo names it`() {
        show(catWith("cover", "second"))

        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        position(2, of = 2).assertIsDisplayed()
    }

    @Test
    fun `a flick moves the row one photo on`() {
        show(catWith("cover", "second", "third"))

        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput {
            swipe(center, center - Offset(FLICK.px(), 0f), durationMillis = 30)
        }
        compose.waitForIdle()

        position(2, of = 3).assertIsDisplayed()
    }

    @Test
    fun `a tap on the second photo opens the viewer on it`() {
        show(catWith("cover", "second"))
        compose.onNodeWithTag(DetailCarouselTestTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        onScreenPhoto().performClick()

        assertEquals(listOf("photo second"), taps)
    }

    @Test
    fun `a photo that arrives brings the row to it`() {
        show(catWith("cover", "second"))
        position(1, of = 2).assertIsDisplayed()

        state = catWith("cover", "second", "third")
        compose.waitForIdle()

        position(3, of = 3).assertIsDisplayed()
        onScreenPhoto().performClick()
        assertEquals(listOf("photo third"), taps)
    }

    @Test
    fun `while photos attach, the progress runs under the row`() {
        show(catWith("cover").withAttempt(AttachProgress(done = 2, total = 5)))

        val bar = compose.onNodeWithContentDescription("Attached 2 of 5 photos").bounds()

        // The indicator's semantics reach past its line on both sides, so its centre is what sits under the row.
        assertTrue(bar.center.y >= compose.onNodeWithTag(DetailCarouselTestTag).bounds().bottom, "$bar")
    }

    @Test
    fun `the add a photo card is gone`() {
        show(catWith("cover"))
        compose.scrollCarouselToEnd(photos = 1)

        compose.onAllNodesWithText("Add a photo").assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.detail_take_photo)).assertIsEnabled()
    }

    private fun show(cat: EncounterDetailState) {
        state = cat
        compose.setContent {
            CatsRadarTheme {
                scheme = MaterialTheme.colorScheme
                Surface {
                    EncounterDetailScreen(
                        state = state,
                        contentPadding = PaddingValues(),
                        onPhotoClick = { taps += "photo ${it.photoId}" },
                        onTakePhotoClick = { taps += "take $it" },
                        onPickPhotoClick = { taps += "pick $it" },
                    )
                }
            }
        }
    }

    private fun photoNodes() =
        compose.onAllNodesWithContentDescription(context.getString(R.string.detail_photo_description))

    private fun photos(): List<Rect> = photoNodes().fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }

    // The row clips its neighbours to slivers at its edges; the photo in front shows whole.
    private fun onScreenPhoto() = photoNodes()[
        photoNodes().fetchSemanticsNodes().withIndex().maxBy { it.value.boundsInRoot.width }.index,
    ]

    private fun position(page: Int, of: Int) =
        compose.onNodeWithText(context.getString(R.string.viewer_position, page, of))

    private fun screen(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private fun PixelMap.at(x: Float, y: Float): Color = this[x.toInt(), y.toInt()]

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun Dp.px(): Float = with(compose.density) { toPx() }

    private fun catWith(vararg photoIds: String) = loadedWith(
        CatPage(
            id = "cat-1",
            dayLabel = "Today",
            timeLabel = "14:32",
            location = LocationLabel.NONE,
            coordinatesLabel = null,
            accuracyMeters = null,
            photos = photoIds.map { DetailPhoto(id = it, path = "/data/photos/$it.jpg") }.toImmutableList(),
        ),
    )

    private fun EncounterDetailState.Loaded.withAttempt(progress: AttachProgress? = null) =
        copy(pages = pages.map { it.copy(addPhoto = AddPhoto.ATTACHING, attachProgress = progress) }.toImmutableList())

    private companion object {
        // Short and fast: a free fling would carry it past the next photo.
        val FLICK = 150.dp
    }
}
