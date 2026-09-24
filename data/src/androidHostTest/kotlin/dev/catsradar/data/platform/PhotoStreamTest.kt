package dev.catsradar.data.platform

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PhotoStreamTest {

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
        val capture = Uri.parse("content://dev.catsradar.fileprovider/captures/cat.jpg")
        serve(capture, redacted = "as handed over", original = "original")

        assertEquals("as handed over", read(capture))
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
}
