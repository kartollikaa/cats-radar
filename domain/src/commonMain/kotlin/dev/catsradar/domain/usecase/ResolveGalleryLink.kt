package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.GalleryItems
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.first

sealed interface GalleryTarget {
    /** [grantRead] when the opener may pass on its own read access to the item. */
    data class Open(val uri: String, val grantRead: Boolean) : GalleryTarget

    /** The cat had an original in the gallery, and it has been deleted there since. */
    data object Gone : GalleryTarget

    data object Unavailable : GalleryTarget
}

class ResolveGalleryLink(
    private val encounterRepository: EncounterRepository,
    private val galleryItems: GalleryItems,
    private val deviceIdProvider: DeviceIdProvider,
) {
    suspend operator fun invoke(encounterId: String): GalleryTarget {
        val link = encounterRepository.observeById(encounterId).first()?.galleryLink(deviceIdProvider.deviceId)
            ?: return GalleryTarget.Unavailable
        return when {
            galleryItems.exists(link.uri) -> GalleryTarget.Open(uri = link.uri, grantRead = true)
            else -> GalleryTarget.Gone
        }
    }
}
