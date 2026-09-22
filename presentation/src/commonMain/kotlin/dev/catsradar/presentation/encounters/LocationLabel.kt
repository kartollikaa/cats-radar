package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.LocationSource

/** Which words describe where an encounter's coordinates came from; the platform holds the text. */
enum class LocationLabel { FROM_PHOTO, CURRENT, LAST_KNOWN, FROM_OUTING, NONE }

fun LocationSource.toLocationLabel(): LocationLabel = when (this) {
    LocationSource.EXIF -> LocationLabel.FROM_PHOTO
    LocationSource.CURRENT_FIX -> LocationLabel.CURRENT
    LocationSource.LAST_KNOWN -> LocationLabel.LAST_KNOWN
    LocationSource.BACKFILLED -> LocationLabel.FROM_OUTING
    LocationSource.NONE -> LocationLabel.NONE
}
