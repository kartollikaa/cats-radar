package dev.catsradar.app.widget

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterKind
import dev.catsradar.domain.model.EncounterOrigin
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationSource
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.model.PlaceCellAssignment
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

internal class FakeTodayRepository : EncounterRepository {
    private val rows = MutableStateFlow(emptyList<Encounter>())
    private val announced = MutableStateFlow(emptyList<Encounter>())
    private var announcing = true
    private var heldRead: CompletableDeferred<Unit>? = null

    /** A reader that starts while this is set fails; one already reading carries on. */
    var failNewReads = false

    var newReads = 0
        private set

    fun add(id: String, at: Instant) {
        rows.update { it + tally(id, at) }
        if (announcing) announced.value = rows.value
    }

    fun snapshot(): List<Encounter> = rows.value

    /** Readers already following the rows stop hearing of changes; a new reader still reads them all. */
    fun holdAnnouncements() {
        announcing = false
    }

    /** Tells readers already following the rows that they are [rows], as a query started earlier would. */
    fun announce(rows: List<Encounter>) {
        announced.value = rows
    }

    fun resumeAnnouncements() {
        announcing = true
        announced.value = rows.value
    }

    /** The next new reader sees the rows as they are when it starts, but only once the result completes. */
    fun holdNextRead(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { heldRead = it }

    override fun observeAll(): Flow<List<Encounter>> = flow {
        newReads++
        check(!failNewReads) { "storage unavailable" }
        heldRead?.let { release ->
            heldRead = null
            val snapshot = rows.value
            release.await()
            emit(snapshot.live())
        }
        emit(rows.value.live())
        emitAll(announced.map { it.live() })
    }

    override suspend fun softDelete(id: String, deletedAt: Instant) {
        rows.update { list -> list.map { if (it.id == id) it.copy(deletedAt = deletedAt) else it } }
        if (announcing) announced.value = rows.value
    }

    override suspend fun setPlaceCells(assignments: List<PlaceCellAssignment>): Unit =
        throw NotImplementedError("unused by this test")

    override fun observeById(id: String): Flow<Encounter?> = throw NotImplementedError("unused by this test")
    override suspend fun insert(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")
    override suspend fun update(encounter: Encounter): Unit = throw NotImplementedError("unused by this test")
    override suspend fun attachLocation(id: String, stamp: LocationStamp): Boolean =
        throw NotImplementedError("unused by this test")

    override suspend fun addPhoto(photo: EncounterPhoto): Boolean =
        throw NotImplementedError("unused by this test")

    override suspend fun addPhotos(photos: List<EncounterPhoto>): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun setCoat(id: String, coat: CatCoat?, updatedAt: Instant): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun undoDelete(id: String): Unit = throw NotImplementedError("unused by this test")
    override suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant): Unit =
        throw NotImplementedError("unused by this test")

    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? = null
    override suspend fun loadEvery(): List<Encounter> = rows.value
    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> = emptyList()
    override suspend fun purgeDeletedBefore(cutoff: Instant): Int = 0
}

private fun List<Encounter>.live() = filter { it.deletedAt == null }

private fun tally(id: String, at: Instant): Encounter = Encounter(
    id = id,
    occurredAt = at,
    tzOffsetMinutes = 0,
    kind = EncounterKind.TALLY,
    origin = EncounterOrigin.APP,
    coat = null,
    lat = null,
    lon = null,
    accuracyMeters = null,
    locationSource = LocationSource.NONE,
    locationFixedAt = null,
    geohash = null,
    placeCellId = null,
    deviceId = "device-1",
    createdAt = at,
    updatedAt = at,
    deletedAt = null,
)
