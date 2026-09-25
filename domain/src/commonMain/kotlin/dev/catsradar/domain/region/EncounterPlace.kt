package dev.catsradar.domain.region

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell

/** [city] is null for a cell that names its country but no locality or admin area. */
data class EncounterPlace(val countryCode: String, val country: String, val city: String?)

/** The country and city [RegionTree] files this encounter under; null under Unresolved or NoLocation. */
fun Encounter.placeIn(cells: List<PlaceCell>): EncounterPlace? =
    resolvedCell(cells.associateBy { it.cellId })?.let { cell ->
        cell.countryCode?.let { code ->
            EncounterPlace(countryCode = code, country = cell.countryName ?: code, city = cell.cityName())
        }
    }
