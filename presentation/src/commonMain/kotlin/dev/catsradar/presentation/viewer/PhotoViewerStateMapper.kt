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

    /**
     * Null when the cat has no photo to show; opens on [openedOn], or on the cover when the cat has no such photo.
     * A photo is offered in the gallery only once [galleryTargets] says it opens there.
     */
    fun map(
        encounter: Encounter,
        today: LocalDate,
        openedOn: String?,
        galleryTargets: Map<String, GalleryTarget>,
    ): PhotoViewerState.Showing? {
        if (encounter.photos.isEmpty()) return null
        return PhotoViewerState.Showing(
            photos = encounter.photos.map { photo ->
                ViewerPhoto(
                    id = photo.id,
                    path = photoStorage.resolve(photo.photoPath),
                    opensInGallery = galleryTargets[photo.id] is GalleryTarget.Open,
                )
            }.toImmutableList(),
            firstPage = encounter.photos.indexOfFirst { it.id == openedOn }.coerceAtLeast(0),
            timeLabel = dateTimeFormatter.time(encounter),
            dayLabel = dateTimeFormatter.dayHeader(encounter, today),
        )
    }

    fun offerGallery(showing: PhotoViewerState.Showing, galleryTargets: Map<String, GalleryTarget>) = showing.copy(
        photos = showing.photos.map { it.copy(opensInGallery = galleryTargets[it.id] is GalleryTarget.Open) }
            .toImmutableList(),
    )
}
