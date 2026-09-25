package dev.catsradar.presentation.regions

import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionLabel
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.usecase.RegionView
import dev.catsradar.presentation.detail.formatCoordinate
import dev.catsradar.presentation.encounters.EncountersStateMapper
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate

class RegionsStateMapper(private val encountersMapper: EncountersStateMapper) {

    fun map(view: RegionView, parent: RegionKey?, today: LocalDate): RegionsState = when (view) {
        is RegionView.Places -> when {
            view.children.isEmpty() && parent == null ->
                RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_YET, RegionsEmptyHint.HOW_PLACES_APPEAR)
            view.children.isEmpty() -> RegionsState.Empty(RegionsEmptyLabel.NO_PLACES_HERE)
            else -> {
                val total = view.children.sumOf { it.count }
                RegionsState.Places(
                    header = header(parent, view.self, total),
                    section = parent.section(),
                    rows = view.children.map { it.toRow(total) }.toPersistentList(),
                )
            }
        }
        is RegionView.Cats -> when {
            view.encounters.isEmpty() -> RegionsState.Empty(RegionsEmptyLabel.NO_CATS_HERE)
            else -> RegionsState.Cats(
                header = header(parent, view.self, view.encounters.size),
                rows = encountersMapper.map(view.encounters, today, grid = false).rows,
            )
        }
    }

    private fun header(parent: RegionKey?, self: RegionNode?, total: Int): RegionsHeader? = when {
        parent == null -> RegionsHeader(RegionsTitle.AllPlaces, total)
        self != null -> RegionsHeader(RegionsTitle.Of(self.label.toRowLabel()), self.count)
        else -> null
    }

    // An area and No location list cats, never places, so their answer is never shown.
    private fun RegionKey?.section(): RegionsSection = when (this) {
        null -> RegionsSection.COUNTRIES
        is RegionKey.Country -> RegionsSection.CITIES
        is RegionKey.AreaParent, is RegionKey.Area, RegionKey.NoLocation -> RegionsSection.AREAS
    }

    private fun RegionNode.toRow(total: Int) = RegionRowState(
        key = key.toRowKey(),
        label = label.toRowLabel(),
        countLabel = count.toString(),
        share = if (total > 0) count.toFloat() / total else 0f,
        pseudo = key == RegionKey.Unresolved || key is RegionKey.NoCity || key == RegionKey.NoLocation,
    )

    private fun RegionKey.toRowKey(): RegionRowKey = when (this) {
        is RegionKey.Country -> RegionRowKey.Country(countryCode)
        is RegionKey.AreaParent -> toRowParent()
        is RegionKey.Area -> RegionRowKey.Area(areaHash, parent.toRowParent())
        RegionKey.NoLocation -> RegionRowKey.NoLocation
    }

    private fun RegionKey.AreaParent.toRowParent(): RegionRowKey.AreaParent = when (this) {
        is RegionKey.City -> RegionRowKey.City(countryCode, city)
        RegionKey.Unresolved -> RegionRowKey.Unresolved
        is RegionKey.NoCity -> RegionRowKey.NoCity(countryCode)
    }

    private fun RegionLabel.toRowLabel(): RegionRowLabel = when (this) {
        is RegionLabel.Named -> RegionRowLabel.Named(name)
        is RegionLabel.Coordinates ->
            RegionRowLabel.Coordinates("${formatCoordinate(lat)}, ${formatCoordinate(lon)}")
        RegionLabel.Unresolved -> RegionRowLabel.Unresolved
        RegionLabel.NoCity -> RegionRowLabel.NoCity
        RegionLabel.NoLocation -> RegionRowLabel.NoLocation
    }
}
