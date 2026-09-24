package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter
import dev.catsradar.presentation.coat.CoatOption

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/** Whether this cat is drawn on the map: live, with coordinates that name a place on Earth. */
internal fun Encounter.isOnTheMap(): Boolean =
    deletedAt == null && lat.isWithin(MAX_LATITUDE) && lon.isWithin(MAX_LONGITUDE)

/** Whether a cat of [coat] is shown under this coat choice; an empty choice shows every coat. */
internal fun Set<CoatOption?>.shows(coat: CoatOption?): Boolean = isEmpty() || coat in this

private fun Double?.isWithin(limit: Double): Boolean = this != null && this in -limit..limit
