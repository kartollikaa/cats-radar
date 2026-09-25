package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.PhotoStorage

class PhotoViewerStateMapper(
    private val photoStorage: PhotoStorage,
    private val deviceIdProvider: DeviceIdProvider,
) {

    /** Null when the cat has no photo to show. */
    fun map(encounter: Encounter): PhotoViewerState.Showing? = encounter.cover?.let { cover ->
        PhotoViewerState.Showing(
            photoPath = photoStorage.resolve(cover.photoPath),
            opensInGallery = cover.galleryLink(deviceIdProvider.deviceId) != null,
        )
    }
}
