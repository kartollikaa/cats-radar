package dev.catsradar.domain.photo

import dev.catsradar.domain.platform.ExifData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExifLocationTest {

    @Test
    fun `a point on the globe is a location, up to the poles and the antimeridian`() {
        assertTrue(ExifData(lat = 41.39864, lon = 2.17842).hasLocationOnGlobe)
        assertTrue(ExifData(lat = 90.0, lon = 180.0).hasLocationOnGlobe)
        assertTrue(ExifData(lat = -90.0, lon = -180.0).hasLocationOnGlobe)
    }

    @Test
    fun `a latitude past a pole is no location`() {
        assertFalse(ExifData(lat = 200.0, lon = 2.17842).hasLocationOnGlobe)
    }

    @Test
    fun `a longitude past the antimeridian is no location`() {
        assertFalse(ExifData(lat = 41.39864, lon = -180.5).hasLocationOnGlobe)
    }

    @Test
    fun `a coordinate that is not a number is no location`() {
        assertFalse(ExifData(lat = Double.NaN, lon = 2.17842).hasLocationOnGlobe)
        assertFalse(ExifData(lat = 41.39864, lon = Double.POSITIVE_INFINITY).hasLocationOnGlobe)
    }

    @Test
    fun `half a coordinate pair is no location`() {
        assertFalse(ExifData(lat = 41.39864).hasLocationOnGlobe)
        assertFalse(ExifData(lon = 2.17842).hasLocationOnGlobe)
    }

    @Test
    fun `no coordinates at all is no location`() {
        assertFalse(ExifData().hasLocationOnGlobe)
    }
}
