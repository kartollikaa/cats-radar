package dev.catsradar.domain.repository

import dev.catsradar.domain.model.PlaceCell
import kotlinx.coroutines.flow.Flow

interface PlaceCellRepository {
    fun observeAll(): Flow<List<PlaceCell>>

    /** Null while no cell has [cellId]. */
    fun observeById(cellId: String): Flow<PlaceCell?>

    suspend fun upsert(cell: PlaceCell)

    suspend fun loadById(cellId: String): PlaceCell?

    /** Pending cells ordered by id, starting after [afterCellId], or from the first when it is null. */
    suspend fun loadPendingPage(afterCellId: String?, limit: Int): List<PlaceCell>
}
