package dev.catsradar.domain.platform

interface GalleryItems {
    /** Whether the gallery still holds the item at [uri]; false whenever that cannot be told. */
    suspend fun exists(uri: String): Boolean
}
