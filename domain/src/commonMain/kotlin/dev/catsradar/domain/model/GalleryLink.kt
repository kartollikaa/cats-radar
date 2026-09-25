package dev.catsradar.domain.model

/** The gallery item holding a photo's original; [ownedByApp] when the app wrote it and can still see it. */
data class GalleryLink(val uri: String, val ownedByApp: Boolean)

// Gallery ids are per device, so only links this install recorded count.
fun EncounterPhoto.galleryLink(thisInstall: String): GalleryLink? {
    if (deviceId != thisInstall) return null
    return galleryUri?.let { GalleryLink(it, ownedByApp = true) }
        ?: sourceMediaUri?.let { GalleryLink(it, ownedByApp = false) }
}
