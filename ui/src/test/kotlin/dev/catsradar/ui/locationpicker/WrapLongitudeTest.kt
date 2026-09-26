package dev.catsradar.ui.locationpicker

import org.junit.Assert.assertEquals
import org.junit.Test

private const val TOLERANCE = 1e-9

class WrapLongitudeTest {

    @Test
    fun aLongitudeOnTheGlobeStaysAsItIs() {
        assertEquals(2.17403, wrapLongitude(2.17403), TOLERANCE)
        assertEquals(-179.5, wrapLongitude(-179.5), TOLERANCE)
    }

    @Test
    fun aLongitudePastTheAntimeridianComesBackOnTheOtherSide() {
        assertEquals(-170.0, wrapLongitude(190.0), TOLERANCE)
        assertEquals(170.0, wrapLongitude(-190.0), TOLERANCE)
    }

    @Test
    fun wholeTurnsAroundTheGlobeChangeNothing() {
        assertEquals(2.17403, wrapLongitude(2.17403 + 720.0), TOLERANCE)
    }
}
