package dev.catsradar.ui.counter

import org.junit.Assert.assertEquals
import org.junit.Test

class OutingRowTest {

    @Test
    fun theLineIsCentredWhileUndoIsHidden() {
        assertEquals(350, lineStart(rowWidth = 1000, lineWidth = 300, undoWidth = 0, gap = 20))
    }

    @Test
    fun theLineStaysWhereItWasWhenUndoAppearsWithRoomToSpare() {
        val hidden = lineStart(rowWidth = 1000, lineWidth = 300, undoWidth = 0, gap = 20)
        val shown = lineStart(rowWidth = 1000, lineWidth = 300, undoWidth = 200, gap = 20)

        assertEquals(hidden, shown)
    }

    @Test
    fun theLineStepsTowardTheStartWhereCentredItWouldMeetUndo() {
        val start = lineStart(rowWidth = 800, lineWidth = 400, undoWidth = 250, gap = 20)

        assertEquals(130, start)
        assertEquals(800 - 250, start + 400 + 20)
    }

    @Test
    fun theLineIsCappedSoUndoKeepsItsWholeWidth() {
        val cap = lineMaxWidth(rowWidth = 600, undoWidth = 250, gap = 20)

        assertEquals(330, cap)
        assertEquals(0, lineStart(rowWidth = 600, lineWidth = cap, undoWidth = 250, gap = 20))
    }

    @Test
    fun theLineMayTakeTheWholeRowWhileUndoIsHidden() {
        assertEquals(600, lineMaxWidth(rowWidth = 600, undoWidth = 0, gap = 20))
    }
}
