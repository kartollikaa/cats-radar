package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(point: TrackPointEntity)

    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points ORDER BY walkId, at, rowId")
    suspend fun loadEvery(): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE walkId = :walkId ORDER BY at DESC, rowId DESC LIMIT 1")
    suspend fun loadLast(walkId: String): TrackPointEntity?

    @Query("SELECT * FROM track_points WHERE walkId = :walkId ORDER BY at")
    fun observeTrack(walkId: String): Flow<List<TrackPointEntity>>
}
