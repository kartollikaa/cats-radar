package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface WalkDao {
    @Query("SELECT * FROM walks ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WalkEntity>>

    @Query("SELECT * FROM walks WHERE endedAt IS NULL LIMIT 1")
    suspend fun loadOpen(): WalkEntity?

    @Query("SELECT * FROM walks WHERE endedAt IS NULL LIMIT 1")
    fun observeOpen(): Flow<WalkEntity?>

    @Insert
    suspend fun insert(walk: WalkEntity)

    // Returns the row read back rather than [walk]: the database keeps instants to the millisecond.
    @Transaction
    suspend fun startIfNoneOpen(walk: WalkEntity): WalkEntity {
        loadOpen()?.let { return it }
        insert(walk)
        return checkNotNull(loadOpen())
    }

    @Query("UPDATE walks SET endedAt = :endedAt, updatedAt = :updatedAt WHERE id = :id AND endedAt IS NULL")
    suspend fun end(id: String, endedAt: Instant, updatedAt: Instant): Int

    @Upsert
    suspend fun upsert(walk: WalkEntity)
}
