package dev.catsradar.data.repository

import dev.catsradar.data.db.PlaceCellEntity
import dev.catsradar.domain.model.PlaceCell

internal fun PlaceCellEntity.toDomain(): PlaceCell =
    PlaceCell(
        cellId = cellId,
        centerLat = centerLat,
        centerLon = centerLon,
        countryCode = countryCode,
        countryName = countryName,
        adminArea = adminArea,
        locality = locality,
        subLocality = subLocality,
        status = status,
        attempts = attempts,
        lastAttemptAt = lastAttemptAt,
        resolvedAt = resolvedAt,
    )

internal fun PlaceCell.toEntity(): PlaceCellEntity =
    PlaceCellEntity(
        cellId = cellId,
        centerLat = centerLat,
        centerLon = centerLon,
        countryCode = countryCode,
        countryName = countryName,
        adminArea = adminArea,
        locality = locality,
        subLocality = subLocality,
        status = status,
        attempts = attempts,
        lastAttemptAt = lastAttemptAt,
        resolvedAt = resolvedAt,
    )
