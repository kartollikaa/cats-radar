package dev.catsradar.domain.testing

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.model.PlaceCellAssignment
import dev.catsradar.domain.model.oldestFirst
import dev.catsradar.domain.repository.EncounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeEncounterRepository :
    EncounterRepository,
    RollsBack {
    private val encounters = MutableStateFlow<List<Encounter>>(emptyList())
    val inserted = mutableListOf<Encounter>()
    val softDeleteCalls = mutableListOf<Pair<String, Instant>>()
    val softDeleteAllCalls = mutableListOf<Pair<List<String>, Instant>>()
    val undoDeleteAllCalls = mutableListOf<Pair<List<String>, Instant>>()
    val attachLocationCalls = mutableListOf<String>()
    val setPlaceCellsCalls = mutableListOf<List<PlaceCellAssignment>>()
    val purgeCalls = mutableListOf<Instant>()
    var addPhotoShouldThrow: Throwable? = null

    /** Runs after a successful write, before the result returns. */
    var afterAddPhoto: suspend () -> Unit = {}

    /** Runs before setCoat writes, for what else happens to the cat in the meantime. */
    var beforeSetCoat: suspend () -> Unit = {}

    override fun checkpoint(): () -> Unit {
        val saved = encounters.value
        return { encounters.value = saved }
    }

    // Mirrors the DAO's deletedAt IS NULL filter; a fake that returned deleted rows here would
    // hide every bug about what a read is allowed to see.
    override fun observeAll(): Flow<List<Encounter>> =
        encounters.map { list -> list.filter { it.deletedAt == null } }
    override fun observeById(id: String): Flow<Encounter?> =
        encounters.map { list -> list.firstOrNull { it.id == id && it.deletedAt == null } }

    // Mirrors the DAO's plain @Insert, which aborts on an id that is already there.
    override suspend fun insert(encounter: Encounter) {
        check(encounters.value.none { it.id == encounter.id }) { "UNIQUE constraint failed: ${encounter.id}" }
        inserted += encounter
        encounters.update { it + encounter }
    }

    override suspend fun update(encounter: Encounter) {
        encounters.update { list -> list.map { if (it.id == encounter.id) encounter.copy(photos = it.photos) else it } }
    }

    override suspend fun addPhotos(photos: List<EncounterPhoto>) {
        photos.forEach { photo ->
            encounters.update { list ->
                val known = list.any { cat -> cat.photos.any { it.id == photo.id } }
                list.map { cat ->
                    if (cat.id == photo.encounterId && !known) {
                        cat.copy(
                            photos = (cat.photos + photo).oldestFirst()
                        )
                    } else {
                        cat
                    }
                }
            }
        }
    }

    // Re-checks deletedAt against the state at write time, not a caller's earlier snapshot -
    // mirrors the real DAO's WHERE id = :id AND deletedAt IS NULL guard.
    override suspend fun attachLocation(id: String, stamp: LocationStamp) {
        attachLocationCalls += id
        encounters.update { list ->
            list.map { encounter ->
                if (encounter.id == id && encounter.deletedAt == null) {
                    encounter.copy(
                        lat = stamp.lat,
                        lon = stamp.lon,
                        accuracyMeters = stamp.accuracyMeters,
                        locationSource = stamp.locationSource,
                        locationFixedAt = stamp.locationFixedAt,
                        geohash = stamp.geohash,
                        placeCellId = stamp.placeCellId,
                        updatedAt = stamp.updatedAt,
                    )
                } else {
                    encounter
                }
            }
        }
    }

    // Mirrors the DAO's live-cat guard, checked at write time.
    override suspend fun addPhoto(photo: EncounterPhoto): Boolean {
        addPhotoShouldThrow?.let { throw it }
        var added = false
        encounters.update { list ->
            added = false
            list.map { encounter ->
                if (encounter.id == photo.encounterId && encounter.deletedAt == null) {
                    added = true
                    encounter.copy(photos = (encounter.photos + photo).oldestFirst(), updatedAt = photo.addedAt)
                } else {
                    encounter
                }
            }
        }
        if (added) afterAddPhoto()
        return added
    }

    // Mirrors the DAO's WHERE deletedAt IS NULL guard, checked at write time.
    override suspend fun setCoat(id: String, coat: CatCoat?, updatedAt: Instant) {
        beforeSetCoat()
        encounters.update { list ->
            list.map { encounter ->
                if (encounter.id == id && encounter.deletedAt == null) {
                    encounter.copy(coat = coat, updatedAt = updatedAt)
                } else {
                    encounter
                }
            }
        }
    }

    // Mirrors the DAO's WHERE lat = :lat AND lon = :lon guard, which ignores deletedAt.
    override suspend fun setPlaceCells(assignments: List<PlaceCellAssignment>) {
        setPlaceCellsCalls += assignments
        encounters.update { list ->
            list.map { encounter ->
                val assignment = assignments.firstOrNull {
                    it.encounterId == encounter.id && it.lat == encounter.lat && it.lon == encounter.lon
                }
                assignment?.let { encounter.copy(geohash = it.geohash, placeCellId = it.placeCellId) } ?: encounter
            }
        }
    }

    override suspend fun softDelete(id: String, deletedAt: Instant) {
        softDeleteCalls += id to deletedAt
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = deletedAt) else it } }
    }

    override suspend fun undoDelete(id: String) {
        encounters.update { list -> list.map { if (it.id == id) it.copy(deletedAt = null) else it } }
    }

    override suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant) {
        softDeleteAllCalls += ids to deletedAt
        encounters.update { list ->
            list.map { if (it.id in ids && it.deletedAt == null) it.copy(deletedAt = deletedAt) else it }
        }
    }

    override suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant) {
        undoDeleteAllCalls += ids to deletedAt
        encounters.update { list ->
            list.map { if (it.id in ids && it.deletedAt == deletedAt) it.copy(deletedAt = null) else it }
        }
    }

    // Mirrors the DAO: a soft-deleted row does not block a re-import of the same bytes.
    override suspend fun findBySourceDigest(sourceDigest: String): Encounter? =
        encounters.value.firstOrNull { cat ->
            cat.deletedAt == null && cat.photos.any { it.sourceDigest == sourceDigest }
        }

    override suspend fun loadEvery(): List<Encounter> = encounters.value

    override suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter> =
        encounters.value.filter { it.deletedAt != null && it.deletedAt!! < cutoff }

    override suspend fun purgeDeletedBefore(cutoff: Instant): Int {
        purgeCalls += cutoff
        val doomed = encounters.value.filter { it.deletedAt != null && it.deletedAt!! < cutoff }
        encounters.update { list -> list - doomed.toSet() }
        return doomed.size
    }
}
