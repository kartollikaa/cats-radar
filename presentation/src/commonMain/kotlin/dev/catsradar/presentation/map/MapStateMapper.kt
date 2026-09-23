package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.encounters.EncountersStateMapper
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.datetime.LocalDate

// About a kilometre: one cat, or several on one street, should open on the street rather than a doorstep.
private const val MIN_AREA_DEGREES = 0.01

private const val MAX_LATITUDE = 90.0

class MapStateMapper(private val encountersMapper: EncountersStateMapper) {

    /** A spot or focus in [choices] that matches nothing is ignored. */
    fun map(encounters: List<Encounter>, today: LocalDate, choices: MapChoices = MapChoices()): MapState {
        val outing = choices.focus?.let { id -> focusedOuting(encounters, id) }
        val shown = outing ?: encounters
        val located = shown.mapNotNull { it.toPoint() }
        if (located.isEmpty()) return MapState.Empty
        val filtering = choices.coats.isNotEmpty()
        val points = if (filtering) located.filter { it.coat in choices.coats } else located
        val shownIds = points.mapTo(mutableSetOf()) { it.id }
        return MapState.Located(
            points = points.toImmutableList(),
            // Around every located cat, not only the shown ones: a coat filter does not change where the map opens.
            area = areaAround(located),
            spot = choices.spot?.let { ids -> spotOf(shown.filter { it.id in ids && it.id in shownIds }, today) },
            focus = outing?.let {
                MapFocus(
                    outingId = it.first().id,
                    label = encountersMapper.outingLabel(it, today),
                    route = located.toImmutableList(),
                )
            },
            heat = choices.heat,
            shownCoats = choices.coats.toImmutableSet(),
            coatFilterActive = filtering,
            filterMatchesNone = points.isEmpty(),
        )
    }

    private fun focusedOuting(encounters: List<Encounter>, id: String): List<Encounter>? =
        SessionSplitter.groupByOuting(encounters)
            .firstOrNull { outing -> outing.any { it.id == id } }
            ?.takeIf { outing -> outing.any { it.isOnTheMap() } }

    private fun spotOf(cats: List<Encounter>, today: LocalDate): MapSpot? {
        if (cats.isEmpty()) return null
        return MapSpot(catCount = cats.size, rows = encountersMapper.map(cats, today).rows)
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
