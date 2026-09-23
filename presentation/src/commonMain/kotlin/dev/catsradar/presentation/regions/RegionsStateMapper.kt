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
                // An area's children are its cats, which this screen shows in place rather than
                // pushing another level; everything above it drills down.
                drillable = node.key !is RegionKey.Area,
            )
        }.toPersistentList(),
        encounters = encountersMapper.mapList(view.encounters, today),
    )

    private fun RegionKey.toRowKey(): RegionRowKey = when (this) {
        is RegionKey.Country -> RegionRowKey.Country(countryCode)
        is RegionKey.City -> RegionRowKey.City(countryCode, city)
        is RegionKey.Area -> RegionRowKey.Area(areaHash)
        RegionKey.Unresolved -> RegionRowKey.Unresolved
        RegionKey.NoLocation -> RegionRowKey.NoLocation
    }

    private fun RegionLabel.toRowLabel(): RegionRowLabel = when (this) {
        is RegionLabel.Named -> RegionRowLabel.Named(name)
        is RegionLabel.Coordinates ->
            RegionRowLabel.Coordinates("${formatCoordinate(lat)}, ${formatCoordinate(lon)}")
        RegionLabel.Unresolved -> RegionRowLabel.Unresolved
        RegionLabel.NoLocation -> RegionRowLabel.NoLocation
    }
}
