package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/** Whether this cat is drawn on the map: live, with coordinates that name a place on Earth. */
internal fun Encounter.isOnTheMap(): Boolean =
    deletedAt == null && lat.isWithin(MAX_LATITUDE) && lon.isWithin(MAX_LONGITUDE)

private fun Double?.isWithin(limit: Double): Boolean = this != null && this in -limit..limit
