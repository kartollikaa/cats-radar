package dev.catsradar.domain.repository

import dev.catsradar.domain.model.PlaceCell
import kotlinx.coroutines.flow.Flow

interface PlaceCellRepository {
    fun observeAll(): Flow<List<PlaceCell>>

    suspend fun upsert(cell: PlaceCell)

    suspend fun loadById(cellId: String): PlaceCell?

    suspend fun loadPendingPage(limit: Int, offset: Int): List<PlaceCell>
}
