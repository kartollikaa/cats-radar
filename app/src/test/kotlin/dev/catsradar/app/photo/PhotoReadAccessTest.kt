package dev.catsradar.app.photo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PhotoReadAccessTest {

    private val resolver: ContentResolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun photo(id: Int): Uri =
        Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/$id")

    private val photos = listOf(19, 20).map(::photo)

    @Test
    fun heldPhotosStayReadable() {
        resolver.holdReadAccess(photos)

        assertEquals(photos, resolver.persistedUriPermissions.map { it.uri })
        assertTrue(resolver.persistedUriPermissions.all { it.isReadPermission })
    }

    @Test
    fun aNewBatchLetsGoOfWhatAnEarlierOneStillHeld() {
        resolver.holdReadAccess(photos)
        val next = listOf(20, 21).map(::photo)

        resolver.holdReadAccess(next)

        assertEquals(next.toSet(), resolver.persistedUriPermissions.map { it.uri }.toSet())
    }

    @Test
    fun anEmptyPickLeavesTheEarlierBatchItsPhotos() {
        resolver.holdReadAccess(photos)

        resolver.holdReadAccess(emptyList())

        assertEquals(photos.toSet(), resolver.persistedUriPermissions.map { it.uri }.toSet())
    }

    @Test
    fun releasedPhotosAreNoLongerHeld() {
        resolver.holdReadAccess(photos)

        resolver.releaseReadAccess(photos)

        assertEquals(emptyList(), resolver.persistedUriPermissions)
    }
}
