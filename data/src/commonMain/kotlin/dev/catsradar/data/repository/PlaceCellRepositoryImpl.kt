package dev.catsradar.data.repository

import dev.catsradar.data.db.PlaceCellDao
import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaceCellRepositoryImpl(private val dao: PlaceCellDao) : PlaceCellRepository {
    override fun observeAll(): Flow<List<PlaceCell>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsert(cell: PlaceCell) = dao.upsert(cell.toEntity())

    override suspend fun loadById(cellId: String): PlaceCell? = dao.loadById(cellId)?.toDomain()

    override suspend fun loadPendingPage(afterCellId: String?, limit: Int): List<PlaceCell> =
        dao.loadPage(PlaceStatus.PENDING, afterCellId, limit).map { it.toDomain() }
}
