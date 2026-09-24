package dev.catsradar.presentation.regions

import dev.catsradar.domain.platform.PhotoStorage
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionLabel
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

    fun map(view: RegionView, today: LocalDate): RegionsState = RegionsState(
        rows = view.children.map { node ->
            RegionRowState(
                key = node.key.toRowKey(),
                label = node.label.toRowLabel(),
                countLabel = node.count.toString(),
            )
        }.toPersistentList(),
        encounters = encountersMapper.mapList(view.encounters, today),
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
