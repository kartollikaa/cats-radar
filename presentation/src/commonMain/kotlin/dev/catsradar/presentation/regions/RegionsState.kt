package dev.catsradar.presentation.regions

import dev.catsradar.presentation.encounters.EncounterListItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class RegionsState(
    val rows: ImmutableList<RegionRowState> = persistentListOf(),
    val encounters: ImmutableList<EncounterListItem> = persistentListOf(),
) {
    val isEmpty: Boolean get() = rows.isEmpty() && encounters.isEmpty()
}

/**
 * [key] is the identity the screen hands back when the row is tapped; the callback stays a
 * composable parameter, so State carries no lambdas.
 */
data class RegionRowState(
    val key: RegionRowKey,
    val label: RegionRowLabel,
    val countLabel: String,
    val drillable: Boolean,
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
