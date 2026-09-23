package dev.catsradar.ui.counter

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkRowTest {

    @Test
    fun theChipIsCentredWhileUndoIsHidden() {
        assertEquals(350, walkChipStart(rowWidth = 1000, chipWidth = 300, undoWidth = 0, gap = 20))
    }

    @Test
    fun theChipStaysWhereItWasWhenUndoAppearsWithRoomToSpare() {
        val hidden = walkChipStart(rowWidth = 1000, chipWidth = 300, undoWidth = 0, gap = 20)
        val shown = walkChipStart(rowWidth = 1000, chipWidth = 300, undoWidth = 200, gap = 20)

        assertEquals(hidden, shown)
    }

    @Test
    fun theChipStepsTowardTheStartWhereCentredItWouldMeetUndo() {
        val start = walkChipStart(rowWidth = 800, chipWidth = 400, undoWidth = 250, gap = 20)

        assertEquals(130, start)
        assertEquals(800 - 250, start + 400 + 20)
    }

    @Test
    fun theChipIsCappedSoUndoKeepsItsWholeWidth() {
        val cap = walkChipMaxWidth(rowWidth = 600, undoWidth = 250, gap = 20)

        assertEquals(330, cap)
        assertEquals(0, walkChipStart(rowWidth = 600, chipWidth = cap, undoWidth = 250, gap = 20))
    }

    @Test
    fun theChipMayTakeTheWholeRowWhileUndoIsHidden() {
        assertEquals(600, walkChipMaxWidth(rowWidth = 600, undoWidth = 0, gap = 20))
    }
}
