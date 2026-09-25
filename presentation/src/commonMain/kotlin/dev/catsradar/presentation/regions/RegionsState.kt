package dev.catsradar.presentation.regions

import dev.catsradar.presentation.encounters.EncountersRow
import kotlinx.collections.immutable.ImmutableList

sealed interface RegionsState {
    data object Loading : RegionsState

    data class Empty(val label: RegionsEmptyLabel) : RegionsState

    /** [header] is null only when the level above no longer lists this level. */
    data class Places(
        val header: RegionsHeader?,
        val section: RegionsSection,
        val rows: ImmutableList<RegionRowState>,
    ) : RegionsState

    data class Cats(val header: RegionsHeader?, val rows: ImmutableList<EncountersRow>) : RegionsState
}

enum class RegionsEmptyLabel { NO_PLACES_YET, NO_PLACES_HERE, NO_CATS_HERE }

data class RegionsHeader(val title: RegionsTitle, val count: Int)

sealed interface RegionsTitle {
    data object AllPlaces : RegionsTitle
    data class Of(val label: RegionRowLabel) : RegionsTitle
}

enum class RegionsSection { COUNTRIES, CITIES, AREAS }

/**
 * [key] is the identity the screen hands back when the row is tapped; the callback stays a
 * composable parameter, so State carries no lambdas.
 */
data class RegionRowState(
    val key: RegionRowKey,
    val label: RegionRowLabel,
    val countLabel: String,
    /** This row's part of the level's cats, from 0 to 1. */
    val share: Float,
    /** Not named yet, No city and No location: rows that stand for the lack of a place. */
    val pseudo: Boolean,
)

/** A row's name, or the pieces the platform needs to build one. */
sealed interface RegionRowLabel {
    data class Named(val name: String) : RegionRowLabel
    data class Coordinates(val text: String) : RegionRowLabel
    data object Unresolved : RegionRowLabel
    data object NoCity : RegionRowLabel
    data object NoLocation : RegionRowLabel
}

/**
 * The domain's `RegionKey` in presentation terms. It exists because `:ui` does not depend on
 * `:domain` and must still be able to say which row was tapped.
 */
sealed interface RegionRowKey {
    sealed interface AreaParent : RegionRowKey

    data class Country(val countryCode: String) : RegionRowKey
    data class City(val countryCode: String, val city: String) : AreaParent
    data class Area(val areaHash: String, val parent: AreaParent) : RegionRowKey
    data object Unresolved : AreaParent
    data class NoCity(val countryCode: String) : AreaParent
    data object NoLocation : RegionRowKey
}
