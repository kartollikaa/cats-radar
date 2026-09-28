package dev.catsradar.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisLabelStartsTest {

    @Test
    fun labelsWithRoomAreCentredUnderTheirBars() {
        val starts = axisLabelStarts(
            centres = listOf(50f, 150f, 250f),
            widths = listOf(40, 40, 40),
            width = 300,
            minGap = 4,
        )

        assertEquals(listOf(30, 130, 230), starts)
    }

    @Test
    fun labelsAtTheEndsStayInsideTheChart() {
        val starts = axisLabelStarts(centres = listOf(5f, 295f), widths = listOf(40, 40), width = 300, minGap = 4)

        assertEquals(listOf(0, 260), starts)
    }

    @Test
    fun aLabelThatWouldCrowdTheOneAfterItIsLeftOutAndTodaysAlwaysShows() {
        val starts = axisLabelStarts(
            centres = listOf(170f, 230f, 280f),
            widths = listOf(50, 50, 50),
            width = 300,
            minGap = 4,
        )

        assertEquals(listOf(145, null, 250), starts)
    }

    @Test
    fun aBarWithoutALabelTakesNoRoom() {
        val starts = axisLabelStarts(
            centres = listOf(50f, 60f, 150f),
            widths = listOf(40, 0, 40),
            width = 300,
            minGap = 4,
        )

        assertEquals(listOf(30, null, 130), starts)
    }
}
