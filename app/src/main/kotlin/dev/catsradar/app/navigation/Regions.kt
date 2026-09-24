package dev.catsradar.app.navigation

import androidx.navigation3.runtime.NavKey
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.presentation.regions.RegionRowKey
import kotlinx.serialization.Serializable

/**
 * One level of the place drill-down. The level, and an area's parent, are carried as flat,
 * serializable fields rather than nested keys, because a NavKey has to survive saved-state restoration.
 */
@Serializable
data class Regions(
    val kind: RegionKind = RegionKind.ROOT,
    val countryCode: String? = null,
    val city: String? = null,
    val areaHash: String? = null,
) : NavKey

@Serializable
enum class RegionKind {
    ROOT,
    COUNTRY,
    CITY,
    CITY_AREA,
    UNRESOLVED,
    UNRESOLVED_AREA,
    NO_CITY,
    NO_CITY_AREA,
    NO_LOCATION,
}

fun Regions.toRegionKey(): RegionKey? = when (kind) {
    RegionKind.ROOT -> null
    RegionKind.COUNTRY -> RegionKey.Country(countryCode.orEmpty())
    RegionKind.CITY -> cityKey()
    RegionKind.CITY_AREA -> RegionKey.Area(areaHash.orEmpty(), cityKey())
    RegionKind.UNRESOLVED -> RegionKey.Unresolved
    RegionKind.UNRESOLVED_AREA -> RegionKey.Area(areaHash.orEmpty(), RegionKey.Unresolved)
    RegionKind.NO_CITY -> noCityKey()
    RegionKind.NO_CITY_AREA -> RegionKey.Area(areaHash.orEmpty(), noCityKey())
    RegionKind.NO_LOCATION -> RegionKey.NoLocation
}

private fun Regions.cityKey() = RegionKey.City(countryCode.orEmpty(), city.orEmpty())

private fun Regions.noCityKey() = RegionKey.NoCity(countryCode.orEmpty())

fun RegionRowKey.toNavKey(): Regions = when (this) {
    is RegionRowKey.Country -> Regions(RegionKind.COUNTRY, countryCode = countryCode)
    is RegionRowKey.City -> Regions(RegionKind.CITY, countryCode = countryCode, city = city)
    is RegionRowKey.Area -> parent.areaNavKey(areaHash)
    RegionRowKey.Unresolved -> Regions(RegionKind.UNRESOLVED)
    is RegionRowKey.NoCity -> Regions(RegionKind.NO_CITY, countryCode = countryCode)
    RegionRowKey.NoLocation -> Regions(RegionKind.NO_LOCATION)
}

private fun RegionRowKey.AreaParent.areaNavKey(areaHash: String): Regions = when (this) {
    is RegionRowKey.City -> Regions(RegionKind.CITY_AREA, countryCode = countryCode, city = city, areaHash = areaHash)
    RegionRowKey.Unresolved -> Regions(RegionKind.UNRESOLVED_AREA, areaHash = areaHash)
    is RegionRowKey.NoCity -> Regions(RegionKind.NO_CITY_AREA, countryCode = countryCode, areaHash = areaHash)
}
