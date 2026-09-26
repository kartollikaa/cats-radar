package dev.catsradar.app.worker

import dev.catsradar.domain.model.PlaceCell
import dev.catsradar.domain.model.PlaceStatus
import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class InMemoryPlaceCells(vararg seed: PlaceCell) : PlaceCellRepository {
    private val rows = MutableStateFlow(seed.toList())

    fun put(cell: PlaceCell) {
        rows.update { list -> list.filterNot { it.cellId == cell.cellId } + cell }
    }

    fun attemptsOf(cellId: String): Int = rows.value.single { it.cellId == cellId }.attempts

    override fun observeAll(): Flow<List<PlaceCell>> = rows
    override fun observeById(cellId: String): Flow<PlaceCell?> =
        rows.map { list -> list.firstOrNull { it.cellId == cellId } }
    override suspend fun upsert(cell: PlaceCell) = put(cell)
    override suspend fun loadById(cellId: String): PlaceCell? = rows.value.firstOrNull { it.cellId == cellId }
    override suspend fun loadPendingPage(afterCellId: String?, limit: Int): List<PlaceCell> =
        rows.value.filter { it.status == PlaceStatus.PENDING && (afterCellId == null || it.cellId > afterCellId) }
            .sortedBy { it.cellId }
            .take(limit)
}

internal fun untriedCell(id: String) = PlaceCell(
    cellId = id,
    centerLat = 41.388,
    centerLon = 2.170,
    countryCode = null,
    countryName = null,
    adminArea = null,
    locality = null,
    subLocality = null,
    status = PlaceStatus.PENDING,
    attempts = 0,
    lastAttemptAt = null,
    resolvedAt = null,
)
