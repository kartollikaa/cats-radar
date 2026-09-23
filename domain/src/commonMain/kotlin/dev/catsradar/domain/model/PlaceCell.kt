package dev.catsradar.domain.model

import kotlin.time.Instant

data class PlaceCell(
    val cellId: String,
    val centerLat: Double,
    val centerLon: Double,
    val countryCode: String?,
    val countryName: String?,
    val adminArea: String?,
    val locality: String?,
    val subLocality: String?,
    val status: PlaceStatus,
    val attempts: Int,
    val lastAttemptAt: Instant?,
    val resolvedAt: Instant?,
) {
    val isUntried: Boolean get() = status == PlaceStatus.PENDING && attempts == 0
}

enum class PlaceStatus { PENDING, RESOLVED, FAILED, UNAVAILABLE }
