package dev.catsradar.data.repository

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import kotlin.time.Instant

// Every field is distinct and non-null where the type allows it: centerLat/centerLon and the two
// Instants each differ, so a transposition between them cannot hide.
internal fun distinctPlaceCell(): PlaceCell = PlaceCell(
    cellId = "cell-1",
    centerLat = 11.11,
    centerLon = 22.22,
    countryCode = "RU",
    countryName = "Russia",
    adminArea = "Moscow Oblast",
    locality = "Moscow",
    subLocality = "Arbat",
    status = PlaceStatus.RESOLVED,
    attempts = 2,
    lastAttemptAt = Instant.parse("2026-01-01T01:00:00Z"),
    resolvedAt = Instant.parse("2026-01-01T02:00:00Z"),
)
