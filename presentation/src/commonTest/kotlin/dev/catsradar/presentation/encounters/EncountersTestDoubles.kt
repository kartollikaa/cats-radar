package dev.catsradar.presentation.encounters

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.platform.PhotoStorage
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

internal fun photoFixture(
    id: String,
    occurredAt: Instant,
    locationSource: LocationSource = LocationSource.NONE,
): Encounter = encounterFixture(id, occurredAt, locationSource = locationSource)
    .withPhoto(photoPath = "$id.jpg", thumbPath = "${id}_thumb.jpg")

/** The cat with one photo, which takes the cat's id, install and creation time. */
internal fun Encounter.withPhoto(
    photoPath: String,
    thumbPath: String? = null,
    galleryUri: String? = null,
    sourceMediaUri: String? = null,
): Encounter = copy(
    photos = listOf(
        EncounterPhoto(
            id = id,
            encounterId = id,
            photoPath = photoPath,
            thumbPath = thumbPath,
            galleryUri = galleryUri,
            sourceMediaUri = sourceMediaUri,
            sourceDigest = null,
            deviceId = deviceId,
            addedAt = createdAt,
            shotId = id,
        ),
    ),
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

// Mirrors AndroidPhotoStorage's contract: a stored path is relative, and resolving prefixes it with
// the app's own photo directory.
internal class FakePhotoStorage(private val root: String = "/data/photos") : PhotoStorage {
    override fun resolve(relativePath: String): String = "$root/$relativePath"

    override suspend fun delete(relativePath: String) = Unit
}
