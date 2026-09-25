package dev.catsradar.domain.region

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell

/** [city] is null for a cell that names its country but no locality or admin area. */
data class EncounterPlace(val countryCode: String, val country: String, val city: String?)

/** The country and city [RegionTree] files this encounter under in [cell]; null under Unresolved or NoLocation. */
fun Encounter.placeIn(cell: PlaceCell?): EncounterPlace? =
    cell?.takeIf { isNamedBy(it) }?.let { named ->
        named.countryCode?.let { code ->
            EncounterPlace(countryCode = code, country = named.countryName ?: code, city = named.cityName())
        }
    }
