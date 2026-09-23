package dev.catsradar.data.repository

import dev.catsradar.data.db.PlaceCellDao
import dev.catsradar.data.db.PlaceCellEntity
import dev.catsradar.domain.model.PlaceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakePlaceCellDao : PlaceCellDao {
    var observeAllResult: List<PlaceCellEntity> = emptyList()
    var loadByIdResult: PlaceCellEntity? = null
    var loadPageResult: List<PlaceCellEntity> = emptyList()

    val upserted = mutableListOf<PlaceCellEntity>()
    var loadByIdCall: String? = null
    var loadPageCall: Triple<PlaceStatus, String?, Int>? = null

    override suspend fun upsert(cell: PlaceCellEntity) {
        upserted += cell
    }

    override fun observeAll(): Flow<List<PlaceCellEntity>> = flowOf(observeAllResult)

    override suspend fun loadById(cellId: String): PlaceCellEntity? {
        loadByIdCall = cellId
        return loadByIdResult
    }

    override suspend fun loadPage(status: PlaceStatus, afterCellId: String?, limit: Int): List<PlaceCellEntity> {
        loadPageCall = Triple(status, afterCellId, limit)
        return loadPageResult
    }
}
