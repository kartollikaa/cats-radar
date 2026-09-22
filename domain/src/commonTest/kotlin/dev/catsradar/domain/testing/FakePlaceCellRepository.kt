package dev.catsradar.domain.testing

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakePlaceCellRepository(initial: List<PlaceCell> = emptyList()) : PlaceCellRepository {
    private val cells = MutableStateFlow(initial)
    val upserted = mutableListOf<PlaceCell>()

    override fun observeAll(): Flow<List<PlaceCell>> = cells

    override suspend fun upsert(cell: PlaceCell) {
        upserted += cell
        cells.update { list -> list.filterNot { it.cellId == cell.cellId } + cell }
    }

    override suspend fun loadById(cellId: String): PlaceCell? = cells.value.firstOrNull { it.cellId == cellId }

    override suspend fun loadPendingPage(limit: Int, offset: Int): List<PlaceCell> =
        cells.value.filter { it.status == dev.catsradar.domain.model.PlaceStatus.PENDING }
            .drop(offset)
            .take(limit)
}
