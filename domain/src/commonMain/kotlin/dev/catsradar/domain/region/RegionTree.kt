package dev.catsradar.domain.region

import dev.catsradar.domain.Tuning
import dev.catsradar.domain.geo.Geohash
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.model.locatedPoint

/** One row of a region list: a real place, or one of the pseudo-nodes. */
data class RegionNode(val key: RegionKey, val label: RegionLabel, val count: Int)

sealed interface RegionKey {
    sealed interface AreaParent : RegionKey

    data class Country(val countryCode: String) : RegionKey
    data class City(val countryCode: String, val city: String) : AreaParent

    /** [parent]'s encounters inside [areaHash]; another parent's encounters in the same patch are not in it. */
    data class Area(val areaHash: String, val parent: AreaParent) : RegionKey

    /** Encounters with coordinates whose cell has no name — pending, failed, or unavailable. */
    data object Unresolved : AreaParent

    /** Encounters in [countryCode] whose cell names neither a locality nor an admin area. */
    data class NoCity(val countryCode: String) : AreaParent

    /** Encounters with no location: no point on the globe, or marked as having none. */
    data object NoLocation : RegionKey
}

/**
 * What to call a node. A named place carries its name; an area usually has none, so it carries the
 * pieces the platform needs to build one instead of a sentence this layer would have to write.
 */
sealed interface RegionLabel {
    data class Named(val name: String) : RegionLabel
    data class Coordinates(val lat: Double, val lon: Double) : RegionLabel
    data object Unresolved : RegionLabel
    data object NoCity : RegionLabel
    data object NoLocation : RegionLabel
}

object RegionTree {

    /** Countries, then the two pseudo-nodes. Busiest first; the pseudo-nodes always last. */
    fun countries(encounters: List<Encounter>, cells: List<PlaceCell>): List<RegionNode> {
        val byCell = cells.associateBy { it.cellId }
        val live = encounters.filter { it.deletedAt == null }

        val (located, unlocated) = live.partition { it.locatedPoint() != null }
        val (named, unnamed) = located.partition { it.resolvedCell(byCell) != null }

        val countries = named
            .groupBy { it.resolvedCell(byCell)!!.countryCode!! }
            .map { (code, group) ->
                RegionNode(
                    key = RegionKey.Country(code),
                    label = RegionLabel.Named(group.countryName(byCell) ?: code),
                    count = group.size,
                )
            }

        return countries.sortedByDescending { it.count } +
            pseudoNode(RegionKey.Unresolved, RegionLabel.Unresolved, unnamed.size) +
            pseudoNode(RegionKey.NoLocation, RegionLabel.NoLocation, unlocated.size)
    }

    /** Cities within one country, busiest first; then No city, for the cats whose cell names none. */
    fun cities(countryCode: String, encounters: List<Encounter>, cells: List<PlaceCell>): List<RegionNode> {
        val byCell = cells.associateBy { it.cellId }
        val cityNames = encounters
            .filter { it.deletedAt == null }
            .mapNotNull { it.resolvedCell(byCell) }
            .filter { it.countryCode == countryCode }
            .map { it.cityName() }
        val cities = cityNames
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .map { (city, count) -> RegionNode(RegionKey.City(countryCode, city), RegionLabel.Named(city), count) }
            .sortedByDescending { it.count }
        return cities + pseudoNode(RegionKey.NoCity(countryCode), RegionLabel.NoCity, cityNames.count { it == null })
    }

    /**
     * Areas under a node. Areas come from the coordinates, so they work with no network and even for
     * encounters whose cell was never named or never created.
     */
    fun areas(parent: RegionKey.AreaParent, encounters: List<Encounter>, cells: List<PlaceCell>): List<RegionNode> {
        val byCell = cells.associateBy { it.cellId }
        return encounters
            .filter { it.deletedAt == null }
            .filter { it.belongsTo(parent, byCell) }
            .mapNotNull { encounter -> encounter.areaHash()?.let { it to encounter } }
            .groupBy({ it.first }, { it.second })
            .map { (areaHash, group) ->
                RegionNode(
                    key = RegionKey.Area(areaHash, parent),
                    label = areaLabel(areaHash, group, byCell),
                    count = group.size,
                )
            }
            .sortedByDescending { it.count }
    }

    fun encountersIn(parent: RegionKey, encounters: List<Encounter>, cells: List<PlaceCell>): List<Encounter> {
        val byCell = cells.associateBy { it.cellId }
        return encounters.filter { it.deletedAt == null }.filter { it.belongsTo(parent, byCell) }
    }

    private fun areaLabel(
        areaHash: String,
        group: List<Encounter>,
        byCell: Map<String, PlaceCell>,
    ): RegionLabel {
        val subLocality = group
            .mapNotNull { it.resolvedCell(byCell)?.subLocality }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        if (subLocality != null) return RegionLabel.Named(subLocality)

        val bounds = Geohash.decode(areaHash)
        return RegionLabel.Coordinates(lat = bounds.centerLat, lon = bounds.centerLon)
    }

    private fun Encounter.belongsTo(parent: RegionKey, byCell: Map<String, PlaceCell>): Boolean {
        val cell = resolvedCell(byCell)
        return when (parent) {
            is RegionKey.Country -> cell?.countryCode == parent.countryCode
            is RegionKey.City -> cell?.countryCode == parent.countryCode && cell.cityName() == parent.city
            is RegionKey.NoCity -> cell?.countryCode == parent.countryCode && cell.cityName() == null
            is RegionKey.Area -> areaHash() == parent.areaHash && belongsTo(parent.parent, byCell)
            RegionKey.Unresolved -> locatedPoint() != null && cell == null
            RegionKey.NoLocation -> locatedPoint() == null
        }
    }

    private fun Encounter.areaHash(): String? =
        locatedPoint()?.let { Geohash.encode(it.lat, it.lon, Tuning.AREA_PRECISION) }

    private fun List<Encounter>.countryName(byCell: Map<String, PlaceCell>): String? =
        firstNotNullOfOrNull { it.resolvedCell(byCell)?.countryName }

    private fun pseudoNode(key: RegionKey, label: RegionLabel, count: Int): List<RegionNode> =
        if (count == 0) emptyList() else listOf(RegionNode(key, label, count))
}

/** A cell names a located encounter only once it is RESOLVED and has a country. */
internal fun Encounter.resolvedCell(byCell: Map<String, PlaceCell>): PlaceCell? =
    placeCellId
        ?.takeIf { locatedPoint() != null }
        ?.let(byCell::get)
        ?.takeIf { it.status == PlaceStatus.RESOLVED && it.countryCode != null }

// adminArea is the fallback because a rural point often has a region but no locality.
internal fun PlaceCell.cityName(): String? = locality ?: adminArea
