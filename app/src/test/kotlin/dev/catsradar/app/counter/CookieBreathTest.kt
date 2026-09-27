package dev.catsradar.app.counter

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.counter.rememberCookieBreath
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CookieBreathTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // An infinite transition is cancelled while the test clock advances on its own.
    private fun breathOver(breathing: Boolean, frames: Int = 190): List<Float> {
        lateinit var breath: State<Float>
        compose.mainClock.autoAdvance = false
        compose.setContent { CatsRadarTheme { breath = rememberCookieBreath(breathing) } }
        return List(frames) {
            compose.mainClock.advanceTimeByFrame()
            breath.value
        }
    }

    @Test
    fun `while breathing the shape swells and settles between one and one and a fiftieth`() {
        val seen = breathOver(breathing = true)

        assertTrue(seen.distinct().size > 1, "the breath never moved: $seen")
        assertTrue(seen.all { it in 1f..1.02f }, "out of range: ${seen.filterNot { it in 1f..1.02f }}")
        assertTrue(seen.max() > 1.015f, "never swelled: ${seen.max()}")
    }

    @Test
    fun `without a walk the shape stands still`() {
        assertEquals(setOf(1f), breathOver(breathing = false).toSet())
    }

    @Test
    fun `with the animator scale at zero the shape stands still during a walk`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        assertEquals(setOf(1f), breathOver(breathing = true).toSet())
    }
}
