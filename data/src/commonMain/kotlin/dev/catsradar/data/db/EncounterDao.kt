package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface EncounterDao {
    @Query("SELECT * FROM encounters WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<EncounterEntity>>

    @Query("SELECT * FROM encounters WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<EncounterEntity?>

    @Insert
    suspend fun insert(encounter: EncounterEntity)

    @Update
    suspend fun update(encounter: EncounterEntity)

    @Query("UPDATE encounters SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Instant)

    @Query("UPDATE encounters SET deletedAt = NULL WHERE id = :id")
    suspend fun clearDeletedAt(id: String)

    @Query("SELECT * FROM encounters WHERE sourceDigest = :sourceDigest AND deletedAt IS NULL LIMIT 1")
    suspend fun findBySourceDigest(sourceDigest: String): EncounterEntity?

    @Query("DELETE FROM encounters WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Instant): Int
}
