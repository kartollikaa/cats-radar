package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.region.RegionTree
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One level of the region drill-down: its child regions, or its cats when it has no children. */
data class RegionView(val children: List<RegionNode>, val encounters: List<Encounter>)

class ObserveRegion(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
) {
    operator fun invoke(parent: RegionKey?): Flow<RegionView> =
        combine(encounterRepository.observeAll(), placeCellRepository.observeAll()) { encounters, cells ->
            when (parent) {
                null -> RegionView(RegionTree.countries(encounters, cells), emptyList())
                is RegionKey.Country -> RegionView(
                    RegionTree.cities(parent.countryCode, encounters, cells),
                    emptyList()
                )
                is RegionKey.City, RegionKey.Unresolved ->
                    RegionView(RegionTree.areas(parent, encounters, cells), emptyList())
                // An area and "no location" are the bottom: below them are the cats themselves.
                is RegionKey.Area, RegionKey.NoLocation ->
                    RegionView(emptyList(), RegionTree.encountersIn(parent, encounters, cells))
            }
        }
}
