package dev.catsradar.app.counter

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.counter.rememberCookieBreath
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.After
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
    private var breathing by mutableStateOf(true)
    private lateinit var breath: State<Float>

    @After
    fun restoreAnimatorScale() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    // An infinite transition is cancelled while the test clock advances on its own.
    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CatsRadarTheme { breath = rememberCookieBreath(breathing) } }
    }

    private fun framesOf(count: Int): List<Float> = List(count) {
        compose.mainClock.advanceTimeByFrame()
        breath.value
    }

    @Test
    fun `while breathing the shape swells and settles between one and one and a fiftieth`() {
        show()
        val seen = framesOf(190)

        assertTrue(seen.distinct().size > 1, "the breath never moved: $seen")
        assertTrue(seen.all { it in 1f..1.02f }, "out of range: ${seen.filterNot { it in 1f..1.02f }}")
        assertTrue(seen.max() > 1.015f, "never swelled: ${seen.max()}")
    }

    @Test
    fun `without a walk the shape stands still`() {
        breathing = false
        show()

        assertEquals(setOf(1f), framesOf(190).toSet())
    }

    @Test
    fun `with the animator scale at zero the shape stands still during a walk`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()

        assertEquals(setOf(1f), framesOf(190).toSet())
    }

    @Test
    fun `when the walk ends the shape settles back rather than jumping`() {
        show()
        val swollen = framesOf(80).last()
        assertTrue(swollen > 1.01f, "not swollen yet: $swollen")

        breathing = false
        val settling = framesOf(120)

        assertTrue(settling.first() > 1.005f, "jumped to ${settling.first()}")
        assertEquals(1f, settling.last())
    }
}
