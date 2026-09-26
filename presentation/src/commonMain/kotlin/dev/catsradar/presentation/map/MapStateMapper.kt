package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.walk.overlaps
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.encounters.EncountersStateMapper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.datetime.LocalDate

// About a kilometre: one cat, or several on one street, should open on the street rather than a doorstep.
private const val MIN_AREA_DEGREES = 0.01

private const val MAX_LATITUDE = 90.0

class MapStateMapper(
    private val encountersMapper: EncountersStateMapper,
    private val photoStorage: PhotoStorage,
) {

    /** A focus in [choices] that matches nothing is ignored; no point carries a thumbnail in [unreadableThumbnails]. */
    fun map(
        encounters: List<Encounter>,
        today: LocalDate,
        choices: MapChoices = MapChoices(),
        walks: List<WalkTrack> = emptyList(),
        unreadableThumbnails: Set<String> = emptySet(),
    ): MapState {
        val outing = choices.focus?.let { id -> focusedOuting(encounters, id) }
        val shown = outing ?: encounters
        val seen = shown.mapNotNull { cat -> cat.toPoint(unreadableThumbnails)?.let { cat.occurredAt to it } }
        if (seen.isEmpty()) return MapState.Empty
        val located = seen.map { (_, point) -> point }
        val filtering = choices.coats.isNotEmpty()
        val points = seen.sortedByDescending { (occurredAt, _) -> occurredAt }
            .map { (_, point) -> point }
            .filter { choices.coats.shows(it.coat) }
        val locatedPositions = located.map { MapPosition(it.latitude, it.longitude) }
        val focus = outing?.let {
            MapFocus(outingId = it.first().id, label = headerLabel(it, today), lines = routeOf(it, located, walks))
        }
        val requested = choices.cat?.let { id -> points.firstOrNull { it.id == id } }
        return MapState.Located(
            points = points.toImmutableList(),
            // Around every located cat and its track, not only the shown ones: a coat filter does not move it.
            area = areaAround(locatedPositions + focus?.lines.orEmpty().flatMap { it.positions }),
            focus = focus,
            heat = choices.heat,
            shownCoats = choices.coats.toImmutableSet(),
            coatFilterActive = filtering,
            filterMatchesNone = points.isEmpty(),
            catArea = requested?.let { areaAround(listOf(MapPosition(it.latitude, it.longitude))) },
        )
    }

    private fun focusedOuting(encounters: List<Encounter>, id: String): List<Encounter>? =
        SessionSplitter.outingOf(encounters, id)?.takeIf { outing -> outing.any { it.isOnTheMap() } }

    // The same header the Encounters list gives the outing, so the chip and the list never disagree.
    private fun headerLabel(outing: List<Encounter>, today: LocalDate): String =
        encountersMapper.outingLabel(outing, today)

    private fun routeOf(
        outing: List<Encounter>,
        located: List<MapPoint>,
        walks: List<WalkTrack>,
    ): ImmutableList<MapLine> {
        val from = outing.minOf { it.occurredAt }
        val to = outing.maxOf { it.occurredAt }
        val tracks = walks
            .filter { it.walk.overlaps(from, to) && it.points.size >= 2 }
            .sortedBy { it.walk.startedAt }
            .map { track -> track.points.map { MapPosition(it.lat, it.lon) } }
        val lines = tracks.ifEmpty { listOf(located.map { MapPosition(it.latitude, it.longitude) }) }
        return lines.map { MapLine(it.toImmutableList()) }.toImmutableList()
    }

    private fun Encounter.toPoint(unreadableThumbnails: Set<String>): MapPoint? {
        val latitude = lat
        val longitude = lon
        if (!isOnTheMap() || latitude == null || longitude == null) return null
        return MapPoint(
            id = id,
            latitude = latitude,
            longitude = longitude,
            coat = coat?.toOption(),
            thumbnailPath = cover?.thumbPath?.let(photoStorage::resolve)?.takeUnless { it in unreadableThumbnails },
        )
    }

    private fun areaAround(points: List<MapPosition>): MapArea {
        val south = points.minOf { it.latitude }
        val north = points.maxOf { it.latitude }
        val west = points.minOf { it.longitude }
        val east = points.maxOf { it.longitude }
        val latitudePad = ((MIN_AREA_DEGREES - (north - south)) / 2).coerceAtLeast(0.0)
        val longitudePad = ((MIN_AREA_DEGREES - (east - west)) / 2).coerceAtLeast(0.0)
        return MapArea(
            south = (south - latitudePad).coerceAtLeast(-MAX_LATITUDE),
            west = west - longitudePad,
            north = (north + latitudePad).coerceAtMost(MAX_LATITUDE),
            east = east + longitudePad,
        )
    }
}
