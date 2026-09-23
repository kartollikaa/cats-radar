package dev.catsradar.data.platform

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class LocationAccuracyTest {

    @Test
    fun aLocationThatSaysHowPreciseItIsKeepsItsAccuracy() {
        val location = Location("fused").apply { accuracy = 12f }

        assertEquals(12f, location.accuracyOrNull())
    }

    @Test
    fun aLocationThatDoesNotSayHowPreciseItIsHasNoAccuracyRatherThanAPerfectOne() {
        assertNull(Location("fused").accuracyOrNull())
    }
}
