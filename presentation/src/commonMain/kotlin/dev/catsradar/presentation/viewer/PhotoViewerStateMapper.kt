package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.galleryLink
import dev.catsradar.domain.platform.DeviceIdProvider
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.time.localDate
import dev.catsradar.presentation.DateTimeFormatter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset

class PhotoViewerStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
    private val deviceIdProvider: DeviceIdProvider,
) {

    /** Null when the cat has no photo to show. */
    fun map(encounter: Encounter, today: LocalDate): PhotoViewerState.Showing? = encounter.photoPath?.let {
        PhotoViewerState.Showing(
            photoPath = photoStorage.resolve(it),
            timeLabel = dateTimeFormatter.time(encounter.occurredAt, UtcOffset(minutes = encounter.tzOffsetMinutes)),
            dayLabel = dateTimeFormatter.dayHeader(encounter.localDate(), today),
            opensInGallery = encounter.galleryLink(deviceIdProvider.deviceId) != null,
        )
    }
}
