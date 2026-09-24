package dev.catsradar.presentation.map

import dev.catsradar.domain.model.Encounter
import dev.catsradar.presentation.coat.CoatOption
import dev.catsradar.presentation.coat.toOption
import dev.catsradar.presentation.encounters.EncountersStateMapper
import kotlinx.datetime.LocalDate

class MapSpotStateMapper(private val encountersMapper: EncountersStateMapper) {

    /** The cats of [catIds] the map still shows under [coats], laid out as the Encounters list; null when none is. */
    fun map(
        encounters: List<Encounter>,
        catIds: Set<String>,
        coats: Set<CoatOption?>,
        today: LocalDate,
    ): MapSpotState.Listed? {
        val cats = encounters.filter { it.id in catIds && it.isOnTheMap() && coats.shows(it.coat?.toOption()) }
        if (cats.isEmpty()) return null
        return MapSpotState.Listed(catCount = cats.size, rows = encountersMapper.map(cats, today, grid = false).rows)
    }
}
