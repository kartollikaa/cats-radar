package dev.catsradar.data.platform

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class AndroidExifReaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun reader(zone: TimeZone = TimeZone.UTC) =
        AndroidExifReader(context, deviceZone = { zone })

    private fun fixture(name: String) = PhotoFixtures.copyTo(temporaryFolder.root, name).path

    @Test
    fun gpsCoordinatesAreReadAsLatitudeThenLongitude() = runTest {
        val exif = reader().read(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS))

        val lat = assertNotNull(exif.lat)
        val lon = assertNotNull(exif.lon)
        assertTrue(abs(lat - EXPECTED_LAT) < TOLERANCE, "latitude was $lat")
        assertTrue(abs(lon - EXPECTED_LON) < TOLERANCE, "longitude was $lon")
    }

    @Test
    fun aPhotoWithoutGpsReportsNoCoordinatesRatherThanZeroZero() = runTest {
        val exif = reader().read(fixture(PhotoFixtures.PORTRAIT_NO_GPS))

        // 0.0, 0.0 is a real place in the Gulf of Guinea; "no location" has to be distinguishable.
        assertNull(exif.lat)
        assertNull(exif.lon)
    }

    @Test
    fun theCapturedInstantComesFromTheOffsetInTheFileNotTheDeviceZone() = runTest {
        val readInTokyo = reader(TimeZone.of("Asia/Tokyo"))
            .read(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS))

        // 09:31:12 at +02:00
        assertEquals(Instant.parse("2026-07-14T07:31:12Z"), readInTokyo.takenAt)
        assertEquals(OFFSET_MINUTES_AT_PLUS_TWO, readInTokyo.tzOffsetMinutes)
    }

    @Test
    fun withoutAnOffsetInTheFileTheDeviceZoneIsUsedAndReported() = runTest {
        val exif = reader(TimeZone.of("America/New_York")).read(fixture(PhotoFixtures.PORTRAIT_NO_GPS))

        // 23:59:01 local on 2025-12-31, New York being UTC-5 that day.
        assertEquals(Instant.parse("2026-01-01T04:59:01Z"), exif.takenAt)
        assertNull(exif.tzOffsetMinutes)
    }

    @Test
    fun aPhotoWithNoExifAtAllReadsAsEmptyRatherThanFailing() = runTest {
        val exif = reader().read(fixture(PhotoFixtures.SMALL_NO_EXIF))

        assertNull(exif.lat)
        assertNull(exif.takenAt)
    }

    @Test
    fun aTruncatedFileStillYieldsItsMetadataBecauseExifSitsInTheHeader() = runTest {
        // Worth pinning: the pixels are gone but the metadata is not, so a photo that fails to
        // decode can still carry a usable location. Nothing here may throw on it.
        val exif = reader().read(fixture(PhotoFixtures.TRUNCATED))

        assertNotNull(exif.lat)
        assertNotNull(exif.takenAt)
    }

    @Test
    fun anEmptyFileReadsAsEmptyInsteadOfThrowing() = runTest {
        assertEquals(null, reader().read(fixture(PhotoFixtures.EMPTY)).takenAt)
    }

    @Test
    fun aPathThatDoesNotExistReadsAsEmptyInsteadOfThrowing() = runTest {
        assertEquals(null, reader().read("${temporaryFolder.root}/nothing-here.jpg").takenAt)
    }

    @Test
    fun aGalleryPhotoWhoseOriginalIsRefusedStillGivesItsDate() = runTest {
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
        val photo = Uri.parse("content://media/external/images/media/42")
        val handedOver = File(fixture(PhotoFixtures.LANDSCAPE_WITH_GPS))
        shadowOf(context.contentResolver).registerInputStreamSupplier(photo) { handedOver.inputStream() }
        shadowOf(context.contentResolver).registerInputStreamSupplier(MediaStore.setRequireOriginal(photo)) {
            throw UnsupportedOperationException("Caller must hold ACCESS_MEDIA_LOCATION")
        }

        assertEquals(Instant.parse("2026-07-14T07:31:12Z"), reader().read(photo.toString()).takenAt)
    }

    private companion object {
        const val EXPECTED_LAT = 41.39864 // written by tools/make-photo-fixtures.py
        const val EXPECTED_LON = 2.17842
        const val TOLERANCE = 1e-5
        const val OFFSET_MINUTES_AT_PLUS_TWO = 120
    }
}
