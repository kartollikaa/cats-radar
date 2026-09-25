package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.GalleryItems

sealed interface GalleryTarget {
    data class Open(val uri: String) : GalleryTarget

    /** The cat had an original in the gallery, and it has been deleted there since. */
    data object Gone : GalleryTarget

    data object Unavailable : GalleryTarget
}

class ResolveGalleryLink(
    private val galleryItems: GalleryItems,
    private val deviceIdProvider: DeviceIdProvider,
) {
    suspend operator fun invoke(encounter: Encounter): GalleryTarget {
        val link = encounter.galleryLink(deviceIdProvider.deviceId) ?: return GalleryTarget.Unavailable
        // The app cannot read an item it only picked, so it cannot tell whether that one is still there.
        val openable = !link.ownedByApp || galleryItems.exists(link.uri)
        return if (openable) GalleryTarget.Open(link.uri) else GalleryTarget.Gone
    }
}
