package dev.catsradar.presentation.viewer

import dev.catsradar.domain.platform.GalleryItems
import kotlinx.coroutines.CompletableDeferred

internal class FakeGalleryItems : GalleryItems {
    val present = mutableSetOf<String>()
    val asked = mutableListOf<String>()

    /** When set, every check waits for it, as a real query would wait on the disk. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun exists(uri: String): Boolean {
        asked += uri
        gate?.await()
        return uri in present
    }
}
