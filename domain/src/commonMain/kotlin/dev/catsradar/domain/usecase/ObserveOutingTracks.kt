package dev.catsradar.domain.usecase

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.model.WalkTrack
import dev.catsradar.domain.repository.WalkRepository
import dev.catsradar.domain.session.SessionSplitter
import dev.catsradar.domain.walk.overlaps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The routes of the walks that overlap the outing holding cat `encounterId` among `encounters`, again
 * whenever the walks overlapping it change or one of their routes does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOutingTracks(private val walkRepository: WalkRepository) {

    operator fun invoke(encounters: Flow<List<Encounter>>, encounterId: String): Flow<List<WalkTrack>> {
        val outing = encounters.map { SessionSplitter.outingOf(it, encounterId) }
        return combine(outing, walkRepository.observeAll()) { cats, walks -> walks.overlapping(cats) }
            .distinctUntilChanged()
            .flatMapLatest(::tracksOf)
    }

    private fun List<Walk>.overlapping(outing: List<Encounter>?): List<Walk> =
        if (outing == null) emptyList() else filter { it.overlaps(outing.first().occurredAt, outing.last().occurredAt) }

    private fun tracksOf(walks: List<Walk>): Flow<List<WalkTrack>> =
        if (walks.isEmpty()) {
            flowOf(emptyList())
        } else {
            val tracks = walks.map { walk -> walkRepository.observeTrack(walk.id).map { WalkTrack(walk, it) } }
            combine(tracks) { it.toList() }
        }
}
