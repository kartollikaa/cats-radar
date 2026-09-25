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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.viewer.PhotoViewerState
import dev.catsradar.ui.R
import dev.catsradar.ui.theme.CatsRadarTheme
import dev.catsradar.ui.viewer.PhotoViewerScreen
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
    fun `a photo whose original is in the gallery offers it there, and the tap reports`() {
        var opens = 0
        show(opensInGallery = true, onOpenInGalleryClick = { opens++ })

        openInGallery().assertIsDisplayed().performClick()

        assertEquals(1, opens)
    }

    @Test
    fun `a photo with no original in the gallery offers nothing there`() {
        show(opensInGallery = false)

        back().assertIsDisplayed()
        openInGallery().assertDoesNotExist()
    }

    @Test
    fun `before the photo loads the bar offers back and names nothing`() {
        compose.setContent { CatsRadarTheme { PhotoViewerScreen(state = PhotoViewerState.Loading) } }

        back().assertIsDisplayed()
        assertTrue(compose.onAllNodes(isHeading(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private fun show(
        opensInGallery: Boolean = false,
        onBackClick: () -> Unit = {},
        onOpenInGalleryClick: () -> Unit = {},
    ) {
        val photo = File(context.cacheDir, "cat.png")
        photo.outputStream().use { out ->
            Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        compose.setContent {
            CatsRadarTheme {
                PhotoViewerScreen(
                    state = PhotoViewerState.Showing(
                        photoPath = photo.absolutePath,
                        timeLabel = TIME,
                        dayLabel = DAY,
                        opensInGallery = opensInGallery,
                    ),
                    onBackClick = onBackClick,
                    onOpenInGalleryClick = onOpenInGalleryClick,
                )
            }
        }
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
