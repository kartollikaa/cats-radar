package dev.catsradar.domain.repository

import dev.catsradar.domain.model.CatCoat
import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.model.EncounterPhoto
import dev.catsradar.domain.model.LocationStamp
import dev.catsradar.domain.model.PlaceCellAssignment
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Suppress("TooManyFunctions") // one function per operation on one aggregate
interface EncounterRepository {
    fun observeAll(): Flow<List<Encounter>>

    fun observeById(id: String): Flow<Encounter?>

    suspend fun insert(encounter: Encounter)

    suspend fun update(encounter: Encounter)

    /** No-ops if the row was soft-deleted in the meantime; never resurrects it. */
    suspend fun attachLocation(id: String, stamp: LocationStamp)

    /**
     * Gives [photo] to its cat and stamps the cat's `updatedAt` with [EncounterPhoto.addedAt]. True only when the
     * cat was live and had no photo; otherwise writes nothing.
     */
    suspend fun addPhoto(photo: EncounterPhoto): Boolean

    /** No-ops if the row was soft-deleted in the meantime; never resurrects it. */
    suspend fun setCoat(id: String, coat: CatCoat?, updatedAt: Instant)

    /**
     * One write for all of [assignments]; each sets only the geohash and place cell, and only while its
     * row, deleted or not, still sits at the coordinates it names.
     */
    suspend fun setPlaceCells(assignments: List<PlaceCellAssignment>)

    suspend fun softDelete(id: String, deletedAt: Instant)

    suspend fun undoDelete(id: String)

    /** All of [ids] or none; a row that is already deleted keeps its own `deletedAt`. */
    suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant)

    /** Restores those of [ids] deleted at exactly [deletedAt]; a row deleted at another time stays deleted. */
    suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant)

    /** A live cat with a photo whose digest is [sourceDigest]. */
    suspend fun findBySourceDigest(sourceDigest: String): Encounter?

    /** Every row, soft-deleted ones included — what a backup merge has to reconcile against. */
    suspend fun loadEvery(): List<Encounter>

    /** Soft-deleted rows older than [cutoff]. */
    suspend fun loadDeletedBefore(cutoff: Instant): List<Encounter>

    suspend fun purgeDeletedBefore(cutoff: Instant): Int
}
