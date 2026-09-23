package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface WalkDao {
    @Query("SELECT * FROM walks ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WalkEntity>>

    @Query("SELECT * FROM walks WHERE endedAt IS NULL LIMIT 1")
    suspend fun loadOpen(): WalkEntity?

    @Insert
    suspend fun insert(walk: WalkEntity)

    // Returns the row read back rather than [walk]: the database keeps instants to the millisecond.
    @Transaction
    suspend fun startIfNoneOpen(walk: WalkEntity): WalkEntity {
        loadOpen()?.let { return it }
        insert(walk)
        return checkNotNull(loadOpen())
    }

    @Query("UPDATE walks SET endedAt = :endedAt, updatedAt = :endedAt WHERE id = :id AND endedAt IS NULL")
    suspend fun end(id: String, endedAt: Instant): Int

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Query("SELECT * FROM track_points WHERE walkId = :walkId ORDER BY at DESC, rowId DESC LIMIT 1")
    suspend fun loadLastPoint(walkId: String): TrackPointEntity?

    @Query("SELECT * FROM track_points WHERE walkId = :walkId ORDER BY at")
    fun observeTrack(walkId: String): Flow<List<TrackPointEntity>>
}
