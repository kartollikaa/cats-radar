package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.region.RegionTree
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One level of the region drill-down: its child regions, or at the bottom of the tree its cats. */
sealed interface RegionView {
    data class Places(val children: List<RegionNode>) : RegionView
    data class Cats(val encounters: List<Encounter>) : RegionView
}

class ObserveRegion(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
) {
    operator fun invoke(parent: RegionKey?): Flow<RegionView> =
        combine(encounterRepository.observeAll(), placeCellRepository.observeAll()) { encounters, cells ->
            when (parent) {
                null -> RegionView.Places(RegionTree.countries(encounters, cells))
                is RegionKey.Country -> RegionView.Places(RegionTree.cities(parent.countryCode, encounters, cells))
                is RegionKey.AreaParent -> RegionView.Places(RegionTree.areas(parent, encounters, cells))
                is RegionKey.Area, RegionKey.NoLocation ->
                    RegionView.Cats(RegionTree.encountersIn(parent, encounters, cells))
            }
        }
}
