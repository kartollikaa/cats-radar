package dev.catsradar.data.db

import dev.catsradar.domain.model.PlaceStatus

fun pendingPlaceCellEntity(cellId: String): PlaceCellEntity = PlaceCellEntity(
    cellId = cellId,
    centerLat = 55.75,
    centerLon = 37.62,
    countryCode = null,
    countryName = null,
    adminArea = null,
    locality = null,
    subLocality = null,
    status = PlaceStatus.PENDING,
    attempts = 0,
    lastAttemptAt = null,
    resolvedAt = null,
)
