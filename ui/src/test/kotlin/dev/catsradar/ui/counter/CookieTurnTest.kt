package dev.catsradar.ui.counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieTurnTest {

    private val step = cookieTurnFor(1) - cookieTurnFor(0)

    @Test
    fun eachCatTurnsTheCookieAStepFurtherAndItStaysThere() {
        assertTrue(step > 0f)
        assertEquals(cookieTurnFor(62) + step, cookieTurnFor(63), 0f)
        assertEquals(62 * step, cookieTurnFor(62), 0f)
    }

    @Test
    fun anUndoTurnsItBackAStep() {
        assertEquals(cookieTurnFor(62) - step, cookieTurnFor(61), 0f)
    }

    @Test
    fun aTallyAndAnUndoTurnTheCookieWhileTheFirstCountAndAnImportSetItInPlace() {
        assertTrue(cookieTurnAnimates(shown = 62, next = 63))
        assertTrue(cookieTurnAnimates(shown = 62, next = 61))
        assertFalse(cookieTurnAnimates(shown = null, next = 62))
        assertFalse(cookieTurnAnimates(shown = 62, next = 162))
    }
}
