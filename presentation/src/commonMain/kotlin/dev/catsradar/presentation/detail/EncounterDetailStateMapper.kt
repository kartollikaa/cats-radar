package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.time.localDate
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.encounters.toLocationLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class EncounterDetailStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    fun map(encounter: Encounter, today: LocalDate): EncounterDetailState.Loaded {
        val lat = encounter.lat
        val lon = encounter.lon
        return EncounterDetailState.Loaded(
            dayLabel = dateTimeFormatter.dayHeader(encounter.localDate(), today),
            timeLabel = dateTimeFormatter.time(encounter.occurredAt, UtcOffset(minutes = encounter.tzOffsetMinutes)),
            location = encounter.locationSource.toLocationLabel(),
            coordinatesLabel = if (lat != null && lon != null) {
                "${formatCoordinate(lat)}, ${formatCoordinate(lon)}"
            } else {
                null
            },
            accuracyMeters = encounter.accuracyMeters?.roundToInt(),
            photoPath = encounter.photoPath?.let(photoStorage::resolve),
        )
    }
}

private const val COORDINATE_DECIMALS = 5

// Coordinates are conventionally written with a decimal point whatever the locale, so this is a
// fixed pattern and not DateTimeFormatter's job.
internal fun formatCoordinate(value: Double): String {
    val factor = 10.0.pow(COORDINATE_DECIMALS).toLong()
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    val fraction = (magnitude % factor).toString().padStart(COORDINATE_DECIMALS, '0')
    return "$sign${magnitude / factor}.$fraction"
}
