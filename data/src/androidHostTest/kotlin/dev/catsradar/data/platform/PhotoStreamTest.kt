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
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class PhotoStreamTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val resolver = shadowOf(context.contentResolver)

    private val galleryPhoto =
        Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/20")

    private fun grantMediaLocation() =
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)

    private fun serve(uri: Uri, redacted: String, original: String) {
        resolver.registerInputStream(uri, redacted.byteInputStream())
        resolver.registerInputStream(MediaStore.setRequireOriginal(uri), original.byteInputStream())
    }

    private fun read(uri: Uri): String = context.openPhotoStream(uri.toString()).use { it.readBytes().decodeToString() }

    @Test
    fun withAccessToMediaLocationAMediaStorePhotoIsReadAsItsOriginal() {
        grantMediaLocation()
        serve(galleryPhoto, redacted = "redacted", original = "original")

        assertEquals("original", read(galleryPhoto))
    }

    @Test
    fun withoutAccessToMediaLocationAMediaStorePhotoIsReadAsHandedOver() {
        serve(galleryPhoto, redacted = "redacted", original = "original")

        assertEquals("redacted", read(galleryPhoto))
    }

    @Test
    fun aPhotoOutsideMediaStoreIsReadAsHandedOverEvenWithAccessToMediaLocation() {
        grantMediaLocation()
        val capture = Uri.parse("content://com.kartollika.catsradar.fileprovider/captures/cat.jpg")
        serve(capture, redacted = "as handed over", original = "original")

        assertEquals("as handed over", read(capture))
    }

    @Test
    fun aFileUriIsReadAsHandedOverEvenWithAccessToMediaLocation() {
        grantMediaLocation()
        val file = Uri.parse("file:///sdcard/Pictures/cat.jpg")
        serve(file, redacted = "as handed over", original = "original")

        assertEquals("as handed over", read(file))
    }

    @Test
    fun aPathIsReadFromItsFileEvenWithAccessToMediaLocation() {
        grantMediaLocation()
        val photo = temporaryFolder.newFile("cat.jpg").apply { writeText("from the file") }

        assertEquals("from the file", context.openPhotoStream(photo.path).use { it.readBytes().decodeToString() })
    }

    @Test
    fun anOriginalTheProviderRefusesFallsBackToThePhotoAsHandedOver() {
        grantMediaLocation()
        resolver.registerInputStream(galleryPhoto, "redacted".byteInputStream())
        resolver.registerInputStreamSupplier(MediaStore.setRequireOriginal(galleryPhoto)) {
            throw UnsupportedOperationException("Require Original is not supported for Picker URI")
        }

        assertEquals("redacted", read(galleryPhoto))
    }

    @Test
    fun aPhotoWhoseOriginalIsRefusedStillGivesItsExifDate() = runTest {
        grantMediaLocation()
        val handedOver = PhotoFixtures.copyTo(temporaryFolder.root, PhotoFixtures.LANDSCAPE_WITH_GPS)
        resolver.registerInputStreamSupplier(galleryPhoto) { handedOver.inputStream() }
        resolver.registerInputStreamSupplier(MediaStore.setRequireOriginal(galleryPhoto)) {
            throw UnsupportedOperationException("Caller must hold ACCESS_MEDIA_LOCATION")
        }

        val exif = AndroidExifReader(context, deviceZone = { TimeZone.UTC }).read(galleryPhoto.toString())

        assertEquals(Instant.parse("2026-07-14T07:31:12Z"), exif.takenAt)
    }
}
