package dev.catsradar.presentation.detail

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.region.EncounterPlace
import dev.catsradar.domain.session.OutingWindow
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.dayHeader
import dev.catsradar.presentation.encounters.toLocationLabel
import dev.catsradar.presentation.map.MapPosition
import dev.catsradar.presentation.map.isOnTheMap
import dev.catsradar.presentation.regions.countryFlag
import dev.catsradar.presentation.time
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class EncounterDetailStateMapper(
    private val dateTimeFormatter: DateTimeFormatter,
    private val photoStorage: PhotoStorage,
) {

    /** [currentId] is one of [window]'s cats; [attaching] and [places] are keyed by cat id. */
    fun map(
        window: OutingWindow,
        currentId: String,
        today: LocalDate,
        attaching: Map<String, AttachProgress> = emptyMap(),
        places: Map<String, EncounterPlace?> = emptyMap(),
    ): EncounterDetailState.Loaded {
        val pages = window.cats.map { cat -> page(cat, today, attaching[cat.id], places[cat.id]) }
        return EncounterDetailState.Loaded(
            pages = pages.toImmutableList(),
            currentId = currentId,
            currentNumber = pages.indexOfFirst { it.id == currentId } + 1,
        )
    }

    internal fun page(
        encounter: Encounter,
        today: LocalDate,
        attaching: AttachProgress? = null,
        place: EncounterPlace? = null,
    ): CatPage {
        val lat = encounter.lat
        val lon = encounter.lon
        return CatPage(
            id = encounter.id,
            dayLabel = dateTimeFormatter.dayHeader(encounter, today),
            timeLabel = dateTimeFormatter.time(encounter),
            location = encounter.locationSource.toLocationLabel(),
            coordinatesLabel = if (lat != null && lon != null) {
                "${formatCoordinate(lat)}, ${formatCoordinate(lon)}"
            } else {
                null
            },
            accuracyMeters = encounter.accuracyMeters?.takeIf { lat != null && lon != null }?.roundToInt(),
            photos = encounter.photos.map { DetailPhoto(it.id, photoStorage.resolve(it.photoPath)) }.toImmutableList(),
            coat = encounter.coat?.toOption(),
            addPhoto = if (attaching != null) AddPhoto.ATTACHING else AddPhoto.READY,
            attachProgress = attaching?.takeIf { it.total > 1 },
            mapPosition = if (lat != null && lon != null && encounter.isOnTheMap()) MapPosition(lat, lon) else null,
            setsLocation = encounter.locationSource == LocationSource.NONE,
            place = place?.let { found ->
                // A city-state's locality repeats its country's name.
                val city = found.city?.takeIf { !it.equals(found.country, ignoreCase = true) }
                DetailPlace(
                    title = city ?: found.country,
                    country = if (city != null) found.country else null,
                    flag = countryFlag(found.countryCode),
                )
            },
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
