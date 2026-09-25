package dev.catsradar.domain.testing

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.region.RegionKey
import kotlin.time.Instant

fun encounterAt(occurredAt: Instant, tzOffsetMinutes: Int = 0, deletedAt: Instant? = null): Encounter = Encounter(
    id = "id",
    occurredAt = occurredAt,
    tzOffsetMinutes = tzOffsetMinutes,
    kind = EncounterKind.TALLY,
    origin = EncounterOrigin.APP,
    coat = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "device",
    createdAt = occurredAt,
    updatedAt = occurredAt,
    deletedAt = deletedAt,
)

fun encounterFixture(
    id: String,
    occurredAt: Instant,
    locationSource: LocationSource = LocationSource.NONE,
    lat: Double? = null,
    lon: Double? = null,
): Encounter = encounterAt(occurredAt).copy(id = id, lat = lat, lon = lon, locationSource = locationSource)

fun locatedFixture(id: String, occurredAt: Instant, lat: Double, lon: Double): Encounter {
    val geohash = Geohash.encode(lat, lon, Tuning.GEOHASH_PRECISION)
    return encounterFixture(id, occurredAt, LocationSource.CURRENT_FIX, lat, lon)
        .copy(geohash = geohash, placeCellId = Geohash.prefix(geohash, Tuning.PLACE_CELL_PRECISION))
}

/** The cat with one photo, which takes the cat's id, install and creation time. */
fun Encounter.withPhoto(
    photoPath: String = "$id.jpg",
    thumbPath: String? = "${id}_thumb.jpg",
    galleryUri: String? = null,
    sourceMediaUri: String? = null,
    sourceDigest: String? = null,
    shotId: String? = null,
): Encounter = copy(
    photos = listOf(
        EncounterPhoto(
            id = id,
            encounterId = id,
            photoPath = photoPath,
            thumbPath = thumbPath,
            galleryUri = galleryUri,
            sourceMediaUri = sourceMediaUri,
            sourceDigest = sourceDigest,
            deviceId = deviceId,
            addedAt = createdAt,
            shotId = shotId,
        ),
    ),
)

fun areaOf(encounter: Encounter, parent: RegionKey.AreaParent): RegionKey.Area =
    RegionKey.Area(Geohash.prefix(encounter.geohash!!, Tuning.AREA_PRECISION), parent)

@Suppress("LongParameterList") // a fixture builder: every parameter is one field of the row
fun placeCellFixture(
    encounter: Encounter,
    countryCode: String? = "ES",
    countryName: String? = "Spain",
    locality: String? = "Barcelona",
    subLocality: String? = null,
    adminArea: String? = null,
    status: PlaceStatus = PlaceStatus.RESOLVED,
) = PlaceCell(
    cellId = encounter.placeCellId!!,
    centerLat = encounter.lat!!,
    centerLon = encounter.lon!!,
    countryCode = countryCode,
    countryName = countryName,
    adminArea = adminArea,
    locality = locality,
    subLocality = subLocality,
    status = status,
    attempts = 1,
    lastAttemptAt = encounter.occurredAt,
    resolvedAt = encounter.occurredAt.takeIf { status == PlaceStatus.RESOLVED },
)
