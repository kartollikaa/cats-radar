package dev.catsradar.data.platform

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SampleSizeTest {

    @Test
    fun theSampleSizeIsTheLargestPowerOfTwoThatKeepsTheLongestSideAtOrAboveTheMinimum() {
        val longestSides = listOf(800, 2048, 4095, 4096, 8160, 8191, 8192, 16320)

        val sampleSizes = longestSides.associateWith { sampleSizeFor(longestSide = it, minLongestSide = 2048) }

        val expected = mapOf(800 to 1, 2048 to 1, 4095 to 1, 4096 to 2, 8160 to 2, 8191 to 2, 8192 to 4, 16320 to 4)
        assertEquals(expected, sampleSizes)
    }

    @Test
    fun aMinimumThatIsNotPositiveIsRejected() {
        assertFailsWith<IllegalArgumentException> { sampleSizeFor(longestSide = 4000, minLongestSide = 0) }
    }
}
