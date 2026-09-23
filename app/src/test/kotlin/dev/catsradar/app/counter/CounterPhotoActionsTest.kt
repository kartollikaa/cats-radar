package dev.catsradar.app.counter

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class CounterPhotoActionsTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var cameraClicks = 0
    private var importClicks = 0

    @Before
    fun showTheCounter() {
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = false),
                    onCameraClick = { cameraClicks++ },
                    onImportClick = { importClicks++ },
                )
            }
        }
    }

    @Test
    fun `tapping Photo opens the camera and not an import`() {
        compose.onNodeWithText(context.getString(R.string.counter_camera)).performClick()

        assertEquals(Clicks(camera = 1, import = 0), clicks())
    }

    @Test
    fun `tapping the gallery half starts an import and not the camera`() {
        compose.onNodeWithContentDescription(context.getString(R.string.counter_import)).performClick()

        assertEquals(Clicks(camera = 0, import = 1), clicks())
    }

    @Test
    fun `holding Photo does not start an import`() {
        compose.onNodeWithText(context.getString(R.string.counter_camera)).performTouchInput { longClick() }

        assertEquals(0, importClicks)
    }

    private fun clicks() = Clicks(camera = cameraClicks, import = importClicks)

    private data class Clicks(val camera: Int, val import: Int)
}
