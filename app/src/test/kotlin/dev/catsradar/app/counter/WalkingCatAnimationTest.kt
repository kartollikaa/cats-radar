package dev.catsradar.app.counter

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.presentation.counter.CounterState
import dev.catsradar.ui.R
import dev.catsradar.ui.counter.CounterScreen
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Pixels, not semantics: the cat is decorative and has no node of its own to ask which frame it is on.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class WalkingCatAnimationTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // The test clock cancels infinite animations while it advances on its own, so it is driven by hand.
    private fun show(walking: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CatsRadarTheme {
                CounterScreen(
                    state = CounterState(totalLabel = "3", count = 3, undoVisible = false, walkingMode = walking),
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun button(walking: Boolean): Bitmap =
        compose.onNodeWithText(
            context.getString(if (walking) R.string.counter_walk_stop else R.string.counter_walk_start),
        ).captureToImage().asAndroidBitmap()

    @Test
    fun `during a walk the cat on the button moves`() {
        show(walking = true)
        val before = button(walking = true)

        compose.mainClock.advanceTimeBy(FRAME_MS * 2)

        assertFalse(before.sameAs(button(walking = true)))
    }

    @Test
    fun `with no walk on the cat stands still`() {
        show(walking = false)
        val before = button(walking = false)

        compose.mainClock.advanceTimeBy(FRAME_MS * 2)

        assertTrue(before.sameAs(button(walking = false)))
    }

    private companion object {
        const val FRAME_MS = 100L
    }
}
