package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.region.RegionTree
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One level of the region drill-down: its child regions, or at the bottom of the tree its cats. */
sealed interface RegionView {
    /** This level's node as the level above lists it; null at the top. */
    val self: RegionNode?

    data class Places(val children: List<RegionNode>, override val self: RegionNode? = null) : RegionView
    data class Cats(val encounters: List<Encounter>, override val self: RegionNode? = null) : RegionView
}

class ObserveRegion(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
) {
    operator fun invoke(parent: RegionKey?): Flow<RegionView> =
        combine(encounterRepository.observeAll(), placeCellRepository.observeAll()) { encounters, cells ->
            val self = parent?.let { levelNode(it, encounters, cells) }
            when (parent) {
                null -> RegionView.Places(RegionTree.countries(encounters, cells))
                is RegionKey.Country ->
                    RegionView.Places(RegionTree.cities(parent.countryCode, encounters, cells), self)
                is RegionKey.AreaParent -> RegionView.Places(RegionTree.areas(parent, encounters, cells), self)
                is RegionKey.Area, RegionKey.NoLocation ->
                    RegionView.Cats(RegionTree.encountersIn(parent, encounters, cells), self)
            }
        }

    private fun levelNode(key: RegionKey, encounters: List<Encounter>, cells: List<PlaceCell>): RegionNode? {
        val siblings = when (key) {
            is RegionKey.Country, RegionKey.Unresolved, RegionKey.NoLocation -> RegionTree.countries(encounters, cells)
            is RegionKey.City -> RegionTree.cities(key.countryCode, encounters, cells)
            is RegionKey.NoCity -> RegionTree.cities(key.countryCode, encounters, cells)
            is RegionKey.Area -> RegionTree.areas(key.parent, encounters, cells)
        }
        return siblings.firstOrNull { it.key == key }
    }
}
