package dev.catsradar.app.counter

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.catsradar.app.testing.ComponentActivityRegistered
import dev.catsradar.ui.counter.cookieTurnFor
import dev.catsradar.ui.counter.rememberCookieTurn
import dev.catsradar.ui.theme.CatsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CookieTurnAnimationTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ComponentActivityRegistered()).around(compose)

    private var count by mutableStateOf<Int?>(null)
    private lateinit var turn: State<Float>

    private fun showTurn() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CatsRadarTheme { turn = rememberCookieTurn(count) } }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    // With the clock held, one frame recomposes and the next runs the effect; only the spring needs more time.
    private fun turnAfter(newCount: Int?, millis: Long): Float {
        count = newCount
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        if (millis > 0) {
            compose.mainClock.advanceTimeBy(millis)
            compose.waitForIdle()
        }
        return compose.runOnIdle { turn.value }
    }

    @Test
    fun `the first count sets the cookie in place without a spin`() {
        showTurn()
        assertEquals(0f, turn.value)

        assertEquals(cookieTurnFor(62), turnAfter(62, millis = 0))
    }

    @Test
    fun `a tally turns the cookie a step on the spring and an undo turns it back`() {
        showTurn()
        turnAfter(62, millis = 0)

        val midway = turnAfter(63, millis = 48)
        assertTrue(midway > cookieTurnFor(62) && midway < cookieTurnFor(63), "midway $midway")
        assertEquals(cookieTurnFor(63), turnAfter(63, millis = 3_000), 0.01f)

        val back = turnAfter(62, millis = 48)
        assertTrue(back < cookieTurnFor(63) && back > cookieTurnFor(62), "back $back")
        assertEquals(cookieTurnFor(62), turnAfter(62, millis = 3_000), 0.01f)
    }

    @Test
    fun `a jump of many cats at once sets the cookie in place without a spin`() {
        showTurn()
        turnAfter(62, millis = 0)

        assertEquals(cookieTurnFor(162), turnAfter(162, millis = 0))
    }
}
