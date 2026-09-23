package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import dev.catsradar.domain.model.LocationSource
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
@Suppress("TooManyFunctions") // one function per query; splitting a DAO by count would help nobody
interface EncounterDao {
    @Query("SELECT * FROM encounters WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<EncounterEntity>>

    @Query("SELECT * FROM encounters WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<EncounterEntity?>

    @Insert
    suspend fun insert(encounter: EncounterEntity)

    @Update
    suspend fun update(encounter: EncounterEntity)

    // The deletedAt IS NULL guard stops a repeat soft-delete (e.g. "Undo import") from restarting
    // an already-deleted row's purge clock.
    @Query("UPDATE encounters SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, deletedAt: Instant)

    @Query("UPDATE encounters SET deletedAt = NULL WHERE id = :id")
    suspend fun clearDeletedAt(id: String)

    @Query("UPDATE encounters SET deletedAt = NULL WHERE id = :id AND deletedAt = :deletedAt")
    suspend fun clearDeletedAtIfDeletedAt(id: String, deletedAt: Instant)

    // Row by row rather than one IN (:ids): SQLite before 3.32 (API < 31) caps a statement at 999
    // bound variables. The transaction keeps it all-or-nothing and invalidates observers once.
    @Transaction
    suspend fun softDeleteAll(ids: List<String>, deletedAt: Instant) {
        ids.forEach { softDelete(it, deletedAt) }
    }

    @Transaction
    suspend fun undoDeleteAll(ids: List<String>, deletedAt: Instant) {
        ids.forEach { clearDeletedAtIfDeletedAt(it, deletedAt) }
    }

    // The deletedAt IS NULL guard stops a fix that resolves after the row was undone (an
    // up-to-8-second wait) from writing deletedAt back to NULL and resurrecting it: this touches
    // only the location columns, never the row's other state.
    @Suppress("LongParameterList") // Room binds one :placeholder per parameter; no POJO destructuring in a raw @Query
    @Query(
        """
        UPDATE encounters SET
            lat = :lat, lon = :lon, accuracyMeters = :accuracyMeters,
            locationSource = :locationSource, locationFixedAt = :locationFixedAt,
            geohash = :geohash, placeCellId = :placeCellId, updatedAt = :updatedAt
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun attachLocation(
        id: String,
        lat: Double,
        lon: Double,
        accuracyMeters: Float,
        locationSource: LocationSource,
        locationFixedAt: Instant,
        geohash: String,
        placeCellId: String,
        updatedAt: Instant,
    )

    @Query("SELECT * FROM encounters WHERE sourceDigest = :sourceDigest AND deletedAt IS NULL LIMIT 1")
    suspend fun findBySourceDigest(sourceDigest: String): EncounterEntity?

    // Deleted rows included: a merge has to know that a row it is being offered was deleted here,
    // which observeAll() cannot tell it.
    @Query("SELECT * FROM encounters ORDER BY occurredAt DESC")
    suspend fun loadEvery(): List<EncounterEntity>

    // observeAll() hides soft-deleted rows, so the purge needs its own way to see them: without
    // this, their photo files would be orphaned and nothing would ever look for them again.
    @Query("SELECT * FROM encounters WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun loadDeletedBefore(cutoff: Instant): List<EncounterEntity>

    @Query("DELETE FROM encounters WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Instant): Int
}
