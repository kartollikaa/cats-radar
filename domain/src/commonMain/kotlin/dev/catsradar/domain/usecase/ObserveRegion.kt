package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.region.RegionKey
import dev.catsradar.domain.region.RegionNode
import dev.catsradar.domain.region.RegionTree
import dev.catsradar.domain.repository.EncounterRepository
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** One level of the region drill-down: its child regions, or at the bottom of the tree its cats. */
sealed interface RegionView {
    /** This level's node as the level above lists it; null at the top. */
    val self: RegionNode?

    /** The nodes of the levels above this one, the outermost first; the top level is never among them. */
    val trail: List<RegionNode>

    data class Places(
        val children: List<RegionNode>,
        override val self: RegionNode? = null,
        override val trail: List<RegionNode> = emptyList(),
    ) : RegionView

    data class Cats(
        val encounters: List<Encounter>,
        override val self: RegionNode? = null,
        override val trail: List<RegionNode> = emptyList(),
    ) : RegionView
}

class ObserveRegion(
    private val encounterRepository: EncounterRepository,
    private val placeCellRepository: PlaceCellRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** The level under [parent], again whenever a cat or a place changes, worked out on [computeDispatcher]. */
    operator fun invoke(parent: RegionKey?): Flow<RegionView> =
        combine(encounterRepository.observeAll(), placeCellRepository.observeAll()) { encounters, cells ->
            val self = parent?.let { levelNode(it, encounters, cells) }
            val trail = parent?.let { trail(it, encounters, cells) }.orEmpty()
            when (parent) {
                null -> RegionView.Places(RegionTree.countries(encounters, cells))
                is RegionKey.Country ->
                    RegionView.Places(RegionTree.cities(parent.countryCode, encounters, cells), self, trail)
                is RegionKey.AreaParent -> RegionView.Places(RegionTree.areas(parent, encounters, cells), self, trail)
                is RegionKey.Area, RegionKey.NoLocation ->
                    RegionView.Cats(RegionTree.encountersIn(parent, encounters, cells), self, trail)
            }
        }.flowOn(computeDispatcher)

    private fun trail(key: RegionKey, encounters: List<Encounter>, cells: List<PlaceCell>): List<RegionNode> {
        val above = when (key) {
            is RegionKey.Country, RegionKey.Unresolved, RegionKey.NoLocation -> null
            is RegionKey.City -> RegionKey.Country(key.countryCode)
            is RegionKey.NoCity -> RegionKey.Country(key.countryCode)
            is RegionKey.Area -> key.parent
        } ?: return emptyList()
        return trail(above, encounters, cells) + listOfNotNull(levelNode(above, encounters, cells))
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
