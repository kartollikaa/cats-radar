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

    // Keyed on cellId, not OFFSET: the geocode worker moves cells out of a status between pages, so
    // an offset would skip as many rows as the previous page resolved.
    @Query(
        "SELECT * FROM place_cells WHERE status = :status AND (:afterCellId IS NULL OR cellId > :afterCellId) " +
            "ORDER BY cellId LIMIT :limit",
    )
    suspend fun loadPage(status: PlaceStatus, afterCellId: String?, limit: Int): List<PlaceCellEntity>
}
