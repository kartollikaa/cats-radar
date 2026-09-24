package dev.catsradar.ui.counter

import org.junit.Assert.assertEquals
import org.junit.Test

class WalkRowTest {

    @Test
    fun theButtonIsCentredWhileUndoIsHidden() {
        assertEquals(350, walkButtonStart(rowWidth = 1000, buttonWidth = 300, undoWidth = 0, gap = 20))
    }

    @Test
    fun theButtonStaysWhereItWasWhenUndoAppearsWithRoomToSpare() {
        val hidden = walkButtonStart(rowWidth = 1000, buttonWidth = 300, undoWidth = 0, gap = 20)
        val shown = walkButtonStart(rowWidth = 1000, buttonWidth = 300, undoWidth = 200, gap = 20)

        assertEquals(hidden, shown)
    }

    @Test
    fun theButtonStepsTowardTheStartWhereCentredItWouldMeetUndo() {
        val start = walkButtonStart(rowWidth = 800, buttonWidth = 400, undoWidth = 250, gap = 20)

        assertEquals(130, start)
        assertEquals(800 - 250, start + 400 + 20)
    }

    @Test
    fun theButtonIsCappedSoUndoKeepsItsWholeWidth() {
        val cap = walkButtonMaxWidth(rowWidth = 600, undoWidth = 250, gap = 20)

        assertEquals(330, cap)
        assertEquals(0, walkButtonStart(rowWidth = 600, buttonWidth = cap, undoWidth = 250, gap = 20))
    }

    @Test
    fun theButtonIsAtLeastHalfTheRow() {
        assertEquals(500, walkButtonMinWidth(rowWidth = 1000, maxWidth = 1000))
    }

    @Test
    fun theButtonsFloorGivesWayToUndoAsWell() {
        assertEquals(330, walkButtonMinWidth(rowWidth = 1000, maxWidth = 330))
    }

    @Test
    fun theButtonMayTakeTheWholeRowWhileUndoIsHidden() {
        assertEquals(600, walkButtonMaxWidth(rowWidth = 600, undoWidth = 0, gap = 20))
    }
}
