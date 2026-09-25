package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.dayHeader
import dev.catsradar.presentation.time
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate

class PhotoViewerStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
    private val deviceIdProvider: DeviceIdProvider,
) {

    /** Null when the cat has no photo to show; opens on [openedOn], or on the cover when the cat has no such photo. */
    fun map(encounter: Encounter, today: LocalDate, openedOn: String?): PhotoViewerState.Showing? {
        if (encounter.photos.isEmpty()) return null
        return PhotoViewerState.Showing(
            photos = encounter.photos.map { photo ->
                ViewerPhoto(
                    id = photo.id,
                    path = photoStorage.resolve(photo.photoPath),
                    opensInGallery = photo.galleryLink(deviceIdProvider.deviceId) != null,
                )
            }.toImmutableList(),
            firstPage = encounter.photos.indexOfFirst { it.id == openedOn }.coerceAtLeast(0),
            timeLabel = dateTimeFormatter.time(encounter),
            dayLabel = dateTimeFormatter.dayHeader(encounter, today),
        )
    }
}
