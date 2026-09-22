package dev.catsradar.data.repository

import dev.catsradar.domain.model.PlaceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceCellRepositoryImplTest {
    private val dao = FakePlaceCellDao()
    private val repository = PlaceCellRepositoryImpl(dao)

    @Test
    fun observeAllMapsEntitiesToDomain() = runTest {
        dao.observeAllResult = listOf(distinctPlaceCell().toEntity())

        assertEquals(listOf(distinctPlaceCell()), repository.observeAll().first())
    }

    @Test
    fun upsertMapsDomainToEntity() = runTest {
        repository.upsert(distinctPlaceCell())

        assertEquals(listOf(distinctPlaceCell().toEntity()), dao.upserted)
    }

    @Test
    fun loadByIdDelegatesAndMaps() = runTest {
        dao.loadByIdResult = distinctPlaceCell().toEntity()

        assertEquals(distinctPlaceCell(), repository.loadById("cell-1"))
        assertEquals("cell-1", dao.loadByIdCall)
    }

    @Test
    fun loadPendingPageAsksTheDaoForPendingStatusSpecifically() = runTest {
        dao.loadPageResult = listOf(distinctPlaceCell().toEntity())

        val result = repository.loadPendingPage(limit = 5, offset = 1)

        assertEquals(Triple(PlaceStatus.PENDING, 5, 1), dao.loadPageCall)
        assertEquals(listOf(distinctPlaceCell()), result)
    }
}
