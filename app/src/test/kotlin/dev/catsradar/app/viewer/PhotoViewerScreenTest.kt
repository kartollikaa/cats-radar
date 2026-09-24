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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

        tapThePhoto()
        back().assertDoesNotExist()

        tapThePhoto()
        back().assertIsDisplayed()
    }

    @Test
    fun `the back arrow reports the tap`() {
        var backs = 0
        show(onBackClick = { backs++ })

        back().performClick()

        assertEquals(1, backs)
    }

    private fun show(onBackClick: () -> Unit = {}) {
        val photo = File(context.cacheDir, "cat.png").apply {
            outputStream().use { Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.setContent {
            CatsRadarTheme {
                PhotoViewerScreen(state = PhotoViewerState.Showing(photoPath = photo.absolutePath), onBackClick = onBackClick)
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

    private val photoOnScreen: SemanticsMatcher
        get() = hasContentDescription(context.getString(R.string.detail_photo_description)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)
}
