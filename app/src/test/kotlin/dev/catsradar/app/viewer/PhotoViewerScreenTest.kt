package dev.catsradar.app.viewer

import android.content.Context
import android.graphics.Bitmap
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
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
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.TextLayoutResult
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
import android.graphics.Color as AndroidColor

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
        show(callbacks = Callbacks(onBackClick = { backs++ }))

        back().performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `a photo whose original is in the gallery offers it there, and the tap reports which photo`() {
        val opened = mutableListOf<String>()
        show(
            photos = listOf(Photo("cover", opensInGallery = true)),
            callbacks = Callbacks(onOpenInGalleryClick = { opened += it }),
        )

        openInGallery().assertIsDisplayed().performClick()

        assertEquals(listOf("cover"), opened)
    }

    @Test
    fun `the bar's buttons keep the screens' content inset from the edges`() {
        show(photos = listOf(Photo("cover", opensInGallery = true)))
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val inset = with(compose.density) { 16.dp.toPx() }

        assertEquals(screen.left + inset, back().fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals(screen.right - inset, openInGallery().fetchSemanticsNode().boundsInRoot.right, 1f)
    }

    @Test
    fun `a day too long for the bar is given no more room than keeps it clear of the buttons`() {
        show(photos = listOf(Photo("cover", opensInGallery = true)), day = LONG_DAY)
        val clearance = with(compose.density) { 12.dp.toPx() }
        val between = openInGallery().fetchSemanticsNode().boundsInRoot.left -
            back().fetchSemanticsNode().boundsInRoot.right

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(LONG_DAY, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val room = layouts.single().layoutInput.constraints.maxWidth

        assertTrue(room <= between - 2 * clearance, "the day may take ${room}px of the ${between}px left to it")
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
            callbacks = Callbacks(onOpenInGalleryClick = { opened += it }),
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
    fun `a downward drag moves and shrinks the photo, fades the stage, and hides the chrome`() {
        showOver(underlay = Color.Red)
        val beforePhoto = photo().fetchSemanticsNode().boundsInRoot
        val beforeStage = stagePixel()

        startDownwardDrag(screenFraction = 0.2f)

        val duringPhoto = photo().fetchSemanticsNode().boundsInRoot
        val duringStage = stagePixel()
        assertTrue(duringPhoto.center.y > beforePhoto.center.y, "the photo follows the finger down")
        assertTrue(duringPhoto.width < beforePhoto.width, "the photo shrinks during dismissal")
        assertTrue(AndroidColor.red(duringStage) > AndroidColor.red(beforeStage), "the stage reveals the screen below")
        back().assertDoesNotExist()

        releaseDrag()
    }

    @Test
    fun `a short downward drag restores the viewer without closing it`() {
        var backs = 0
        show(callbacks = Callbacks(onBackClick = { backs++ }))
        val before = photo().fetchSemanticsNode().boundsInRoot

        startDownwardDrag(screenFraction = 0.1f)
        releaseDrag()

        assertEquals(0, backs)
        assertEquals(before, photo().fetchSemanticsNode().boundsInRoot)
        back().assertIsDisplayed()
    }

    @Test
    fun `a downward drag beyond one quarter of the screen closes the viewer once`() {
        var backs = 0
        show(callbacks = Callbacks(onBackClick = { backs++ }))

        startDownwardDrag(screenFraction = 0.3f)
        releaseDrag()

        assertEquals(1, backs)
    }

    @Test
    fun `a cancelled downward drag restores the viewer without closing it`() {
        var backs = 0
        show(callbacks = Callbacks(onBackClick = { backs++ }))
        val before = photo().fetchSemanticsNode().boundsInRoot

        startDownwardDrag(screenFraction = 0.3f)
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()

        assertEquals(0, backs)
        assertEquals(before, photo().fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `a zoomed photo keeps a downward drag for panning instead of closing`() {
        var backs = 0
        show(
            photos = listOf(Photo("zoomed-blue", width = 800, height = 600)),
            callbacks = Callbacks(onBackClick = { backs++ }),
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            val bitmap = photo().captureToImage().asAndroidBitmap()
            AndroidColor.blue(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 200
        }
        val beforeZoom = photo().captureToImage().asAndroidBitmap()
        photo().performTouchInput {
            pinch(
                start0 = center - Offset(24f, 0f),
                end0 = center - Offset(160f, 0f),
                start1 = center + Offset(24f, 0f),
                end1 = center + Offset(160f, 0f),
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            !beforeZoom.sameAs(photo().captureToImage().asAndroidBitmap())
        }

        startDownwardDrag(screenFraction = 0.3f)
        releaseDrag()

        assertEquals(0, backs)
    }

    @Test
    fun `before the photo loads the bar offers back and names nothing`() {
        compose.setContent { CatsRadarTheme { PhotoViewerScreen(state = PhotoViewerState.Loading) } }

        back().assertIsDisplayed()
        assertTrue(compose.onAllNodes(isHeading(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the visible chrome offers removal for the photo on screen`() {
        val requested = mutableListOf<String>()
        show(
            photos = listOf(Photo("cover"), Photo("second")),
            callbacks = Callbacks(onRemovePhotoClick = { requested += it }),
        )
        swipeToTheNextPhoto()

        removePhoto().assertIsDisplayed().performClick()

        assertEquals(listOf("second"), requested)
    }

    @Test
    fun `remove asks for confirmation and cancel reports no confirmation`() {
        var cancelled = 0
        var confirmed = 0
        show(
            removal = Removal("cover"),
            callbacks = Callbacks(
                onCancelPhotoRemoval = { cancelled++ },
                onConfirmPhotoRemoval = { confirmed++ },
            ),
        )

        compose.onNodeWithText(context.getString(R.string.viewer_remove_photo_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.viewer_remove_photo_message)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.viewer_remove_cancel)).performClick()

        assertEquals(1, cancelled)
        assertEquals(0, confirmed)
    }

    @Test
    fun `confirm reports once`() {
        var confirmed = 0
        show(
            removal = Removal("cover"),
            callbacks = Callbacks(onConfirmPhotoRemoval = { confirmed++ }),
        )

        compose.onNodeWithText(context.getString(R.string.viewer_remove_confirm)).performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun `confirm cannot be pressed while removal is running`() {
        show(removal = Removal("cover", inFlight = true))
        compose.onNodeWithText(context.getString(R.string.viewer_remove_confirm)).assertIsNotEnabled()
    }

    @Test
    fun `when the displayed photo disappears the nearest remaining page stays on screen`() {
        val cover = Photo("cover", opensInGallery = true)
        var state by mutableStateOf(showingOf(listOf(cover, Photo("second"))))
        compose.setContent {
            CatsRadarTheme {
                PhotoViewerScreen(
                    state = state,
                    onRemovePhotoClick = { photoId -> state = state.copy(removingPhotoId = photoId) },
                    onConfirmPhotoRemoval = { state = showingOf(listOf(cover)) },
                )
            }
        }
        awaitThePhoto()
        swipeToTheNextPhoto()
        removePhoto().performClick()
        compose.onNodeWithText(context.getString(R.string.viewer_remove_confirm)).performClick()

        openInGallery().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.viewer_position, 1, 2)).assertDoesNotExist()
    }

    private data class Photo(
        val id: String,
        val opensInGallery: Boolean = false,
        val width: Int = 40,
        val height: Int = 30,
    )

    private data class Callbacks(
        val onBackClick: () -> Unit = {},
        val onOpenInGalleryClick: (String) -> Unit = {},
        val onRemovePhotoClick: (String) -> Unit = {},
        val onCancelPhotoRemoval: () -> Unit = {},
        val onConfirmPhotoRemoval: () -> Unit = {},
    )

    private data class Removal(val photoId: String, val inFlight: Boolean = false)

    private fun show(
        photos: List<Photo> = listOf(Photo("cover")),
        firstPage: Int = 0,
        day: String = DAY,
        removal: Removal? = null,
        callbacks: Callbacks = Callbacks(),
    ) {
        compose.setContent {
            CatsRadarTheme {
                PhotoViewerScreen(
                    state = showingOf(photos, firstPage, day).copy(
                        removingPhotoId = removal?.photoId,
                        removalInFlight = removal?.inFlight == true,
                    ),
                    onBackClick = callbacks.onBackClick,
                    onOpenInGalleryClick = callbacks.onOpenInGalleryClick,
                    onRemovePhotoClick = callbacks.onRemovePhotoClick,
                    onCancelPhotoRemoval = callbacks.onCancelPhotoRemoval,
                    onConfirmPhotoRemoval = callbacks.onConfirmPhotoRemoval,
                )
            }
        }
        awaitThePhoto()
    }

    private fun showOver(underlay: Color) {
        compose.setContent {
            CatsRadarTheme {
                Box(modifier = Modifier.fillMaxSize().background(underlay)) {
                    PhotoViewerScreen(state = showingOf(listOf(Photo("cover"))))
                }
            }
        }
        awaitThePhoto()
    }

    private fun showingOf(photos: List<Photo>, firstPage: Int = 0, day: String = DAY): PhotoViewerState.Showing {
        val viewerPhotos = photos.map { photo ->
            val file = File(context.cacheDir, "${photo.id}.png")
            file.outputStream().use { out ->
                Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(AndroidColor.BLUE) }
                    .compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ViewerPhoto(id = photo.id, path = file.absolutePath, opensInGallery = photo.opensInGallery)
        }
        return PhotoViewerState.Showing(
            photos = viewerPhotos.toImmutableList(),
            firstPage = firstPage,
            timeLabel = TIME,
            dayLabel = day,
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

    private fun startDownwardDrag(screenFraction: Float) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(center)
            advanceEventTime(100)
            moveTo(Offset(centerX, centerY + root.height * screenFraction))
        }
        compose.waitForIdle()
    }

    private fun releaseDrag() {
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    private fun stagePixel(): Int {
        val stage = compose.onRoot().captureToImage().asAndroidBitmap()
        return stage.getPixel(1, 1)
    }

    private fun position(page: Int, of: Int) =
        compose.onNodeWithText(context.getString(R.string.viewer_position, page, of))

    private fun back() = compose.onNodeWithContentDescription(context.getString(R.string.viewer_back))

    private fun time() = compose.onNodeWithText(TIME, useUnmergedTree = true)

    private fun day() = compose.onNodeWithText(DAY, useUnmergedTree = true)

    private fun openInGallery() =
        compose.onNodeWithContentDescription(context.getString(R.string.viewer_open_in_gallery))

    private fun removePhoto() =
        compose.onNodeWithContentDescription(context.getString(R.string.viewer_remove_photo))

    private fun photo() = compose.onNode(photoOnScreen, useUnmergedTree = true)

    private val photoOnScreen: SemanticsMatcher
        get() = hasContentDescription(context.getString(R.string.detail_photo_description)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)

    private companion object {
        const val TIME = "14:32"
        const val DAY = "Yesterday"
        const val LONG_DAY = "Wednesday, 23 September 2026, late in the evening"
    }
}
