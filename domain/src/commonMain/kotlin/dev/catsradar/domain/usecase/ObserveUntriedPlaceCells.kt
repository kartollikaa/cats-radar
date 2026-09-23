package dev.catsradar.domain.usecase

import dev.catsradar.domain.repository.PlaceCellRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveUntriedPlaceCells(private val placeCellRepository: PlaceCellRepository) {
    operator fun invoke(): Flow<Set<String>> =
        placeCellRepository.observeAll().map { cells ->
            cells.filter { it.isUntried }.mapTo(mutableSetOf()) { it.cellId }
        }
}
