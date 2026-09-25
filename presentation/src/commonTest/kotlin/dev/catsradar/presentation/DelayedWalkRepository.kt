package dev.catsradar.presentation

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** [StoredWalkRepository] whose [observeEveryPoint] answers only after [answerAfter] of virtual time, like Room. */
internal class DelayedWalkRepository(
    walks: List<Walk> = emptyList(),
    private val points: List<TrackPoint> = emptyList(),
    private val answerAfter: Duration = 1.seconds,
    private val delegate: WalkRepository = StoredWalkRepository(walks, points),
) : WalkRepository by delegate {

    override fun observeEveryPoint(): Flow<List<TrackPoint>> = flow {
        delay(answerAfter)
        emit(points.sortedWith(compareBy({ it.walkId }, { it.at })))
    }
}
