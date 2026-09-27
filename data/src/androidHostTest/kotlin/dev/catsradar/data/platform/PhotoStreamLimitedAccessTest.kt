package dev.catsradar.data.platform

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PhotoStreamLimitedAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val galleryPhoto = Uri.parse("content://media/external/images/media/20")

    @Before
    fun shareThePhotoThroughLimitedAccessOnly() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val resolver = shadowOf(context.contentResolver)
        resolver.registerInputStream(galleryPhoto, "redacted".byteInputStream())
        resolver.registerInputStream(MediaStore.setRequireOriginal(galleryPhoto), "original".byteInputStream())
    }

    @Test
    fun fromAndroid14AMediaStorePhotoIsReadAsItsOriginal() {
        assertEquals("original", read(galleryPhoto))
    }

    @Test
    @Config(sdk = [33])
    fun beforeAndroid14AMediaStorePhotoIsReadAsHandedOver() {
        assertEquals("redacted", read(galleryPhoto))
    }

    private fun read(uri: Uri): String = context.openPhotoStream(uri.toString()).use { it.readBytes().decodeToString() }
}
