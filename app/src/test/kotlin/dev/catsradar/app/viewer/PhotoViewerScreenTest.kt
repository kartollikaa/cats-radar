package dev.catsradar.app.viewer

import android.content.Context
import android.graphics.Bitmap
import android.view.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.presentation.viewer.ViewerPhoto
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.viewer.PhotoViewerScreen
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Telephoto takes no taps until its image is on screen, so the photo has to be a file that decodes.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoViewerScreenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    @Test
    fun `a tap on the photo hides the top bar and a second tap brings it back`() {
        show()
        back().assertIsDisplayed()
        time().assertIsDisplayed()
        day().assertIsDisplayed()

        tapThePhoto()
        back().assertDoesNotExist()
        time().assertDoesNotExist()
        day().assertDoesNotExist()

        tapThePhoto()
        back().assertIsDisplayed()
        time().assertIsDisplayed()
        day().assertIsDisplayed()
    }

    @Test
    fun `the bar names when the photo was taken, the time over the day, centred on the screen`() {
        show()
        val screenCentre = compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x

        val time = compose.onNodeWithText(TIME, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val day = compose.onNodeWithText(DAY, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertTrue(time.bottom <= day.top, "the time sits over the day")
        val tolerance = with(compose.density) { 1.dp.toPx() }
        assertEquals(screenCentre, time.center.x, tolerance)
        assertEquals(screenCentre, day.center.x, tolerance)
    }

    @Test
    fun `the back arrow reports the tap`() {
        var backs = 0
        show(onBackClick = { backs++ })

        back().performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `a photo whose original is in the gallery offers it there, and the tap reports which photo`() {
        val opened = mutableListOf<String>()
        show(photos = listOf(Photo("cover", opensInGallery = true)), onOpenInGalleryClick = { opened += it })

        openInGallery().assertIsDisplayed().performClick()

        assertEquals(listOf("cover"), opened)
    }

    @Test
    fun `a photo with no original in the gallery offers nothing there`() {
        show(photos = listOf(Photo("cover", opensInGallery = false)))

        back().assertIsDisplayed()
        openInGallery().assertDoesNotExist()
    }

    @Test
    fun `a swipe moves to the next photo, and the gallery button follows the photo on screen`() {
        val opened = mutableListOf<String>()
        show(
            photos = listOf(Photo("cover", opensInGallery = false), Photo("second", opensInGallery = true)),
            onOpenInGalleryClick = { opened += it },
        )
        position(1, of = 2).assertIsDisplayed()
        openInGallery().assertDoesNotExist()

        swipeToTheNextPhoto()

        position(2, of = 2).assertIsDisplayed()
        openInGallery().assertIsDisplayed().performClick()
        assertEquals(listOf("second"), opened)
    }

    @Test
    fun `the photo on screen survives the screen being recreated`() {
        val restoration = StateRestorationTester(compose)
        val state = showingOf(listOf(Photo("cover"), Photo("second")))
        restoration.setContent { CatsRadarTheme { PhotoViewerScreen(state = state) } }
        awaitThePhoto()
        swipeToTheNextPhoto()

        restoration.emulateSavedInstanceStateRestore()

        position(2, of = 2).assertIsDisplayed()
    }

    @Test
    fun `opened on a photo the viewer starts there`() {
        show(photos = listOf(Photo("cover"), Photo("second"), Photo("third")), firstPage = 2)

        position(3, of = 3).assertIsDisplayed()
    }

    @Test
    fun `a cat with one photo shows no position`() {
        show(photos = listOf(Photo("cover")))

        compose.onNodeWithText(context.getString(R.string.viewer_position, 1, 1)).assertDoesNotExist()
    }

    @Test
    fun `before the photo loads the bar offers back and names nothing`() {
        compose.setContent { CatsRadarTheme { PhotoViewerScreen(state = PhotoViewerState.Loading) } }

        back().assertIsDisplayed()
        assertTrue(compose.onAllNodes(isHeading(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private data class Photo(val id: String, val opensInGallery: Boolean = false)

    private fun show(
        photos: List<Photo> = listOf(Photo("cover")),
        firstPage: Int = 0,
        onBackClick: () -> Unit = {},
        onOpenInGalleryClick: (String) -> Unit = {},
    ) {
        compose.setContent {
            CatsRadarTheme {
                PhotoViewerScreen(
                    state = showingOf(photos, firstPage),
                    onBackClick = onBackClick,
                    onOpenInGalleryClick = onOpenInGalleryClick,
                )
            }
        }
        awaitThePhoto()
    }

    private fun showingOf(photos: List<Photo>, firstPage: Int = 0): PhotoViewerState.Showing {
        val viewerPhotos = photos.map { photo ->
            val file = File(context.cacheDir, "${photo.id}.png")
            file.outputStream().use { out ->
                Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ViewerPhoto(id = photo.id, path = file.absolutePath, opensInGallery = photo.opensInGallery)
        }
        return PhotoViewerState.Showing(
            photos = viewerPhotos.toImmutableList(),
            firstPage = firstPage,
            timeLabel = TIME,
            dayLabel = DAY,
        )
    }

    private fun awaitThePhoto() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(photoOnScreen, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // A single tap only counts once the double-tap window has passed without a second one.
    private fun tapThePhoto() {
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(ViewConfiguration.getDoubleTapTimeout() * 2L)
        compose.waitForIdle()
    }

    private fun swipeToTheNextPhoto() {
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    private fun position(page: Int, of: Int) =
        compose.onNodeWithText(context.getString(R.string.viewer_position, page, of))

    private fun back() = compose.onNodeWithContentDescription(context.getString(R.string.viewer_back))

    private fun time() = compose.onNodeWithText(TIME, useUnmergedTree = true)

    private fun day() = compose.onNodeWithText(DAY, useUnmergedTree = true)

    private fun openInGallery() =
        compose.onNodeWithContentDescription(context.getString(R.string.viewer_open_in_gallery))

    private val photoOnScreen: SemanticsMatcher
        get() = hasContentDescription(context.getString(R.string.detail_photo_description)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)

    private companion object {
        const val TIME = "14:32"
        const val DAY = "Yesterday"
    }
}
