package dev.catsradar.presentation.viewer

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.usecase.GalleryTarget
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.dayHeader
import dev.catsradar.presentation.time
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate

class PhotoViewerStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    /** Null when the cat has no photo to show; opens on [openedOn], or on the cover when the cat has no such photo. */
    fun map(
        encounter: Encounter,
        today: LocalDate,
        openedOn: String?,
        galleryTargets: Map<String, GalleryTarget>,
    ): PhotoViewerState.Showing? {
        if (encounter.photos.isEmpty()) return null
        val showing = PhotoViewerState.Showing(
            photos = encounter.photos.map { ViewerPhoto(id = it.id, path = photoStorage.resolve(it.photoPath)) }
                .toImmutableList(),
            firstPage = encounter.photos.indexOfFirst { it.id == openedOn }.coerceAtLeast(0),
            timeLabel = dateTimeFormatter.time(encounter),
            dayLabel = dateTimeFormatter.dayHeader(encounter, today),
        )
        return offerGallery(showing, galleryTargets)
    }

    fun offerGallery(showing: PhotoViewerState.Showing, galleryTargets: Map<String, GalleryTarget>) = showing.copy(
        photos = showing.photos.map { it.copy(opensInGallery = galleryTargets[it.id] is GalleryTarget.Open) }
            .toImmutableList(),
    )
}
