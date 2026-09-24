package dev.catsradar.presentation.regions

import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionLabel
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.usecase.RegionView
import dev.catsradar.presentation.DateTimeFormatter
import dev.catsradar.presentation.detail.formatCoordinate
import dev.catsradar.presentation.encounters.EncountersStateMapper
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDate

class RegionsStateMapper(
    dateTimeFormatter: DateTimeFormatter,
    photoStorage: PhotoStorage,
) {
    private val encountersMapper = EncountersStateMapper(dateTimeFormatter, photoStorage)

    fun map(view: RegionView, today: LocalDate): RegionsState = when (view) {
        is RegionView.Places -> when {
            view.children.isEmpty() -> RegionsState.Empty(RegionsEmptyLabel.NO_PLACES)
            else -> RegionsState.Loaded(rows = view.children.map { it.toRow() }.toPersistentList())
        }
        is RegionView.Cats -> when {
            view.encounters.isEmpty() -> RegionsState.Empty(RegionsEmptyLabel.NO_CATS)
            else -> RegionsState.Loaded(encounters = encountersMapper.mapList(view.encounters, today))
        }
    }

    private fun RegionNode.toRow() = RegionRowState(
        key = key.toRowKey(),
        label = label.toRowLabel(),
        countLabel = count.toString(),
        // An area's children are its cats, which this screen shows in place rather than
        // pushing another level; everything above it drills down.
        drillable = key !is RegionKey.Area,
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
