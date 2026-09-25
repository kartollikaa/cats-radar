package dev.catsradar.domain.model

/** The gallery item holding a cat's original; [ownedByApp] when the app wrote it and can still see it. */
data class GalleryLink(val uri: String, val ownedByApp: Boolean)

// Gallery ids are per device and a row does not say which device wrote it, so only this install's links count.
fun Encounter.galleryLink(thisInstall: String): GalleryLink? {
    if (deviceId != thisInstall) return null
    return galleryUri?.let { GalleryLink(it, ownedByApp = true) }
        ?: sourceMediaUri?.let { GalleryLink(it, ownedByApp = false) }
}
