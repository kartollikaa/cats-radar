package dev.catsradar.app.navigation

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** The viewer's key as it was saved before cats had several photos. */
@Serializable
private data class ViewerKeyBeforePhotoIds(val encounterId: String)

@RunWith(AndroidJUnit4::class)
class PhotoViewerSavedStateTest {

    @Test
    fun `a viewer key saved before photo ids comes back opening on the cover`() {
        val saved = encodeToSavedState(ViewerKeyBeforePhotoIds.serializer(), ViewerKeyBeforePhotoIds("cat-1"))

        assertEquals(PhotoViewer("cat-1", photoId = null), decodeFromSavedState(PhotoViewer.serializer(), saved))
    }

    @Test
    fun `a viewer key comes back on the photo it was opened for`() {
        val key = PhotoViewer("cat-1", photoId = "second")

        assertEquals(
            key,
            decodeFromSavedState(PhotoViewer.serializer(), encodeToSavedState(PhotoViewer.serializer(), key))
        )
    }
}
