package dev.catsradar.domain.platform

interface GalleryItemLocator {
    /** The gallery item on this device that a picked photo is, or null when it names none. */
    suspend fun locate(pickedUri: String): String?
}
