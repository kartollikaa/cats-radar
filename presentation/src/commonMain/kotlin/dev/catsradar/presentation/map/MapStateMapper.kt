package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.encounters.EncountersStateMapper
import dev.catsradar.presentation.encounters.OutingHeader
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate

// About a kilometre: one cat, or several on one street, should open on the street rather than a doorstep.
private const val MIN_AREA_DEGREES = 0.01

private const val MAX_LATITUDE = 90.0

class MapStateMapper(private val encountersMapper: EncountersStateMapper) {

    /**
     * [spot] holds the ids of the cats the user opened together, and [focus] the id of a cat whose
     * outing the map shows alone; each is ignored when it matches nothing.
     */
    fun map(encounters: List<Encounter>, today: LocalDate, spot: Set<String>? = null, focus: String? = null): MapState {
        val outing = focus?.let { id -> focusedOuting(encounters, id) }
        val shown = outing ?: encounters
        val points = shown.mapNotNull { it.toPoint() }
        if (points.isEmpty()) return MapState.Empty
        return MapState.Located(
            points = points.toImmutableList(),
            area = areaAround(points),
            spot = spot?.let { ids -> spotOf(shown.filter { it.id in ids && it.deletedAt == null }, today) },
            focus = outing?.let { MapFocus(outingId = it.first().id, label = headerLabel(it, today)) },
        )
    }

    private fun focusedOuting(encounters: List<Encounter>, id: String): List<Encounter>? =
        SessionSplitter.groupByOuting(encounters)
            .firstOrNull { outing -> outing.any { it.id == id } }
            ?.takeIf { outing -> outing.any { it.isOnTheMap() } }

    // The same header the Encounters list gives the outing, so the chip and the list never disagree.
    private fun headerLabel(outing: List<Encounter>, today: LocalDate): String =
        encountersMapper.mapList(outing, today).filterIsInstance<OutingHeader>().first().label

    private fun spotOf(cats: List<Encounter>, today: LocalDate): MapSpot? {
        if (cats.isEmpty()) return null
        return MapSpot(catCount = cats.size, rows = encountersMapper.map(cats, today, grid = false).rows)
    }

    private fun Encounter.toPoint(): MapPoint? {
        val latitude = lat
        val longitude = lon
        if (!isOnTheMap() || latitude == null || longitude == null) return null
        return MapPoint(id = id, latitude = latitude, longitude = longitude, coat = coat?.toOption())
    }

    private fun areaAround(points: List<MapPoint>): MapArea {
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
