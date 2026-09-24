package dev.catsradar.domain.model

/** The gallery item holding a cat's original. */
data class GalleryLink(val uri: String)

// Gallery ids are per device, so on any other install the same id may be a different picture.
fun Encounter.galleryLink(thisInstall: String): GalleryLink? =
    galleryUri?.takeIf { deviceId == thisInstall }?.let(::GalleryLink)
