package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.presentation.regions.RegionRowKey
import kotlinx.serialization.Serializable

/**
 * One level of the place drill-down. The parent is carried as flat, serializable fields rather than
 * a nested key, because a NavKey has to survive saved-state restoration.
 */
@Serializable
data class Regions(
    val kind: RegionKind = RegionKind.ROOT,
    val countryCode: String? = null,
    val city: String? = null,
    val areaHash: String? = null,
) : NavKey

@Serializable
enum class RegionKind { ROOT, COUNTRY, CITY, AREA, UNRESOLVED, NO_LOCATION }

fun Regions.toRegionKey(): RegionKey? = when (kind) {
    RegionKind.ROOT -> null
    RegionKind.COUNTRY -> RegionKey.Country(countryCode.orEmpty())
    RegionKind.CITY -> RegionKey.City(countryCode.orEmpty(), city.orEmpty())
    RegionKind.AREA -> RegionKey.Area(areaHash.orEmpty())
    RegionKind.UNRESOLVED -> RegionKey.Unresolved
    RegionKind.NO_LOCATION -> RegionKey.NoLocation
}

fun RegionRowKey.toNavKey(): Regions = when (this) {
    is RegionRowKey.Country -> Regions(RegionKind.COUNTRY, countryCode = countryCode)
    is RegionRowKey.City -> Regions(RegionKind.CITY, countryCode = countryCode, city = city)
    is RegionRowKey.Area -> Regions(RegionKind.AREA, areaHash = areaHash)
    RegionRowKey.Unresolved -> Regions(RegionKind.UNRESOLVED)
    RegionRowKey.NoLocation -> Regions(RegionKind.NO_LOCATION)
}
