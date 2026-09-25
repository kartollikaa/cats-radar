package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.GalleryItems

sealed interface GalleryTarget {
    data class Open(val uri: String) : GalleryTarget

    /** The photo had an original in the gallery, and it has been deleted there since. */
    data object Gone : GalleryTarget

    data object Unavailable : GalleryTarget
}

class ResolveGalleryLink(
    private val galleryItems: GalleryItems,
    private val deviceIdProvider: DeviceIdProvider,
) {
    suspend operator fun invoke(photo: EncounterPhoto): GalleryTarget {
        val link = photo.galleryLink(deviceIdProvider.deviceId) ?: return GalleryTarget.Unavailable
        // Without access to the user's photos the app cannot see an item it only picked: a check would call it gone.
        val openable = !link.ownedByApp || galleryItems.exists(link.uri)
        return if (openable) GalleryTarget.Open(link.uri) else GalleryTarget.Gone
    }
}
