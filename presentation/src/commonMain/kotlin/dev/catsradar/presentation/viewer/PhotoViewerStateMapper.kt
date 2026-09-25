package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.dayHeader
import dev.catsradar.presentation.time
import kotlinx.datetime.LocalDate

class PhotoViewerStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
    private val deviceIdProvider: DeviceIdProvider,
) {

    /** Null when the cat has no photo to show. */
    fun map(encounter: Encounter, today: LocalDate): PhotoViewerState.Showing? = encounter.cover?.let { cover ->
        PhotoViewerState.Showing(
            photoPath = photoStorage.resolve(cover.photoPath),
            timeLabel = dateTimeFormatter.time(encounter),
            dayLabel = dateTimeFormatter.dayHeader(encounter, today),
            opensInGallery = cover.galleryLink(deviceIdProvider.deviceId) != null,
        )
    }
}
