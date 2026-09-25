package dev.catsradar.presentation

import dev.catsradar.domain.model.TrackPoint
import dev.catsradar.domain.model.Walk
import dev.catsradar.domain.repository.WalkRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** [StoredWalkRepository] whose walk and track streams answer only after [answerAfter] of virtual time, like Room. */
internal class DelayedWalkRepository(
    walks: List<Walk> = emptyList(),
    points: List<TrackPoint> = emptyList(),
    private val answerAfter: Duration = 1.seconds,
    private val delegate: WalkRepository = StoredWalkRepository(walks, points),
) : WalkRepository by delegate {

    override fun observeAll(): Flow<List<Walk>> = delegate.observeAll().onStart { delay(answerAfter) }

    override fun observeTrack(walkId: String): Flow<List<TrackPoint>> =
        delegate.observeTrack(walkId).onStart { delay(answerAfter) }

    override fun observeEveryPoint(): Flow<List<TrackPoint>> =
        delegate.observeEveryPoint().onStart { delay(answerAfter) }
}
