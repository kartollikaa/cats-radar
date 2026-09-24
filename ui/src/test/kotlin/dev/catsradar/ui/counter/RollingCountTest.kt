package dev.catsradar.ui.counter

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingCountTest {

    @Test
    fun eachDigitSitsInTheCellOfItsPlaceWithTheUnitsLast() {
        assertEquals(
            listOf(DigitSlot(3, null), DigitSlot(2, '1'), DigitSlot(1, '0'), DigitSlot(0, '5')),
            digitSlots("105"),
        )
    }

    @Test
    fun aNumberGainingADigitFindsItsCellAlreadyThere() {
        val before = digitSlots("99").associate { it.place to it.digit }
        val after = digitSlots("100").associate { it.place to it.digit }

        assertEquals(mapOf(2 to null, 1 to '9', 0 to '9'), before)
        assertEquals('1', after[2])
    }

    @Test
    fun anUnreadTotalIsOneEmptyCell() {
        assertEquals(listOf(DigitSlot(0, null)), digitSlots(""))
    }

    @Test
    fun theUnitsRollAtOnceAndEachPlaceToTheLeftLater() {
        assertEquals(0, carryDelayMillis(0))
        assertTrue(carryDelayMillis(1) > carryDelayMillis(0))
        assertTrue(carryDelayMillis(2) > carryDelayMillis(1))
    }

    @Test
    fun aRiseRollsUpAndAFallRollsDown() {
        assertEquals(Roll.UP, rollBetween(previous = 49, current = 50))
        assertEquals(Roll.DOWN, rollBetween(previous = 50, current = 49))
    }

    @Test
    fun theFirstTotalDoesNotRoll() {
        assertEquals(Roll.NONE, rollBetween(previous = null, current = 42))
        assertEquals(Roll.NONE, rollBetween(previous = 42, current = null))
    }

    @Test
    fun aNumberThatFitsIsDrawnAtFullSize() {
        assertEquals(1f, fitScale(width = 300, height = 100, maxWidth = 600, maxHeight = 200, minScale = MIN), DELTA)
    }

    @Test
    fun aWideNumberShrinksToTheWidth() {
        assertEquals(0.5f, fitScale(width = 1200, height = 100, maxWidth = 600, maxHeight = 200, minScale = MIN), DELTA)
    }

    @Test
    fun aTallNumberShrinksToTheHeight() {
        assertEquals(0.4f, fitScale(width = 300, height = 500, maxWidth = 600, maxHeight = 200, minScale = MIN), DELTA)
    }

    @Test
    fun theNumberNeverShrinksPastTheFloor() {
        assertEquals(MIN, fitScale(width = 6000, height = 100, maxWidth = 600, maxHeight = 200, minScale = MIN), DELTA)
    }

    @Test
    fun anUnboundedSideDoesNotShrinkTheNumber() {
        val scale = fitScale(
            width = 1200,
            height = 100,
            maxWidth = Constraints.Infinity,
            maxHeight = Constraints.Infinity,
            minScale = MIN,
        )

        assertEquals(1f, scale, DELTA)
    }

    @Test
    fun anEmptyNumberInAnEmptyBoxIsLeftUnscaled() {
        assertEquals(1f, fitScale(width = 0, height = 0, maxWidth = 0, maxHeight = 0, minScale = MIN), DELTA)
    }

    private companion object {
        const val MIN = 32f / 112f
        const val DELTA = 0.0001f
    }
}
