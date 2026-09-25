package dev.catsradar.domain.model

/** The gallery item holding a cat's original. */
data class GalleryLink(val uri: String)

// Gallery ids are per device and a row does not say which device wrote it, so only this install's links count.
fun Encounter.galleryLink(thisInstall: String): GalleryLink? =
    galleryUri?.takeIf { deviceId == thisInstall }?.let(::GalleryLink)
