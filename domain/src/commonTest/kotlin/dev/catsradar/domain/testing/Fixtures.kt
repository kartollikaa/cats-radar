package dev.catsradar.domain.testing

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.region.RegionKey
import kotlin.time.Instant

fun encounterAt(occurredAt: Instant, tzOffsetMinutes: Int = 0, deletedAt: Instant? = null): Encounter = Encounter(
    id = "id",
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

fun areaOf(encounter: Encounter): RegionKey.Area =
    RegionKey.Area(Geohash.prefix(encounter.geohash!!, Tuning.AREA_PRECISION))
