package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.presentation.DateTimeFormatter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlin.time.Duration
import kotlin.time.Instant

internal fun encounterFixture(
    id: String,
    occurredAt: Instant,
    tzOffsetMinutes: Int = 0,
    locationSource: LocationSource = LocationSource.NONE,
    deletedAt: Instant? = null,
): Encounter = Encounter(
    id = id,
    occurredAt = occurredAt,
    tzOffsetMinutes = tzOffsetMinutes,
    kind = EncounterKind.TALLY,
    origin = EncounterOrigin.APP,
    coat = null,
    photoPath = null,
    thumbPath = null,
    galleryUri = null,
    sourceDigest = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = locationSource,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "device-1",
    createdAt = occurredAt,
    updatedAt = occurredAt,
    deletedAt = deletedAt,
)

// Returns the argument each call was given rather than a real translation, so a mapper test can
// assert on the LocalDate/Instant it was asked to format without depending on any locale.
internal class FakeDateTimeFormatter : DateTimeFormatter {
    val dayHeaderCalls = mutableListOf<LocalDate>()

    override fun dayHeader(date: LocalDate, today: LocalDate): String {
        dayHeaderCalls += date
        return date.toString()
    }

    override fun time(instant: Instant, offset: UtcOffset): String = instant.toString()

    override fun duration(duration: Duration): String = duration.toString()
}
