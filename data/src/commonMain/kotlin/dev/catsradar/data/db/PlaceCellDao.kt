package dev.catsradar.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.catsradar.domain.model.PlaceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceCellDao {
    @Upsert
    suspend fun upsert(cell: PlaceCellEntity)

    @Query("SELECT * FROM place_cells")
    fun observeAll(): Flow<List<PlaceCellEntity>>

    @Query("SELECT * FROM place_cells WHERE cellId = :cellId")
    suspend fun loadById(cellId: String): PlaceCellEntity?

    @Query("SELECT * FROM place_cells WHERE status = :status LIMIT :limit OFFSET :offset")
    suspend fun loadPage(status: PlaceStatus, limit: Int, offset: Int): List<PlaceCellEntity>
}
