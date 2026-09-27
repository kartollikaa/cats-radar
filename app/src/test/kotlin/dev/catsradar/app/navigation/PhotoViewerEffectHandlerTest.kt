package dev.catsradar.app.navigation

import dev.catsradar.presentation.viewer.PhotoViewerEffect
import org.junit.Test
import kotlin.test.assertEquals

class PhotoViewerEffectHandlerTest {
    private val calls = mutableListOf<String>()

    private fun handle(effect: PhotoViewerEffect, galleryOpens: Boolean = true) = handlePhotoViewerEffect(
        effect,
        onClose = { calls += "close" },
        galleryOpener = { uri ->
            calls += "open $uri"
            galleryOpens
        },
        reporters = PhotoViewerReporters(
            galleryGone = { calls += "gone" },
            noGalleryApp = { calls += "no app" },
            removePhotoFailed = { calls += "remove failed" },
        ),
    )

    @Test
    fun `each effect reaches exactly its own collaborator`() {
        handle(PhotoViewerEffect.Close)
        handle(PhotoViewerEffect.OpenInGallery(SAVED))
        handle(PhotoViewerEffect.GalleryItemGone)
        handle(PhotoViewerEffect.RemovePhotoFailed)

        assertEquals(listOf("close", "open $SAVED", "gone", "remove failed"), calls)
    }

    @Test
    fun `a gallery that cannot be opened says no app can show the photo`() {
        handle(PhotoViewerEffect.OpenInGallery(SAVED), galleryOpens = false)

        assertEquals(listOf("open $SAVED", "no app"), calls)
    }

    private companion object {
        const val SAVED = "content://media/external/images/media/42"
    }
}
